package io.github.menadion.magus

import com.google.firebase.firestore.Blob
import android.util.Base64
import android.content.Context
import android.location.Location
import android.os.BatteryManager
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// One family member as the map sees them.
data class Member(
    val uid: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val battery: Int?,
    val updatedAtMillis: Long?,
    val sharing: Boolean,
    val diag: Map<String, Any?>? = null, // see Diagnostics
    val photo: ByteArray? = null, // small JPEG, see Photos
)

// Everything the app knows about "my family": what's saved on this phone, and what's in Firebase.
//
// In Firebase:
//   families/{code}                   family name, who made it, and when
//   families/{code}/members/{uid}     name, sharing on/off, latest location, battery, last seen
//                                     (overwritten, never a history)
object Family {
    // No I or O, so nobody reads a 1 or a 0 by mistake.
    private const val CODE_LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ"

    private fun prefs(context: Context) = context.getSharedPreferences("magus", Context.MODE_PRIVATE)
    private val db get() = FirebaseFirestore.getInstance()

    fun savedName(context: Context): String? = prefs(context).getString("name", null)
    fun savedCode(context: Context): String? = prefs(context).getString("familyCode", null)
    fun savedFamilyName(context: Context): String? = prefs(context).getString("familyName", null)
    fun isSharing(context: Context): Boolean = prefs(context).getBoolean("sharing", true)

    private fun save(context: Context, name: String, code: String, familyName: String?) {
        prefs(context).edit()
            .putString("name", name)
            .putString("familyCode", code)
            .putString("familyName", familyName)
            .putBoolean("sharing", true)
            .apply()
    }

    // "Santos Family", or "your Family" for a family made before names existed.
    fun familyLabel(context: Context, name: String? = savedFamilyName(context)): String =
        if (name.isNullOrBlank()) context.getString(R.string.family_label_yours)
        else context.getString(R.string.family_label_named, name)

    // Turns my sharing on or off, on this phone and for everyone else's map (green or grey dot).
    fun setSharing(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("sharing", on).apply()
        val code = savedCode(context) ?: return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("families").document(code).collection("members").document(uid)
            .set(mapOf("sharing" to on), SetOptions.merge())
            .addOnSuccessListener { Log.d("Mogar", "setSharing($on) saved") }
            .addOnFailureListener { Log.w("Mogar", "setSharing($on) failed", it) }
    }

    // Signs in anonymously if this phone hasn't yet, and returns this phone's ID.
    suspend fun myId(): String {
        val auth = FirebaseAuth.getInstance()
        auth.currentUser?.let { return it.uid }
        return auth.signInAnonymously().await().user!!.uid
    }

    // Makes a new family with a fresh code, adds me to it, and returns the code.
    suspend fun create(context: Context, name: String, familyName: String): String {
        val uid = myId()
        repeat(5) {
            val code = (1..6).map { CODE_LETTERS.random() }.joinToString("")
            val family = db.collection("families").document(code)
            if (!family.get().await().exists()) {
                family.set(
                    mapOf("name" to familyName, "createdBy" to uid, "createdAt" to FieldValue.serverTimestamp())
                ).await()
                family.collection("members").document(uid).set(mapOf("name" to name, "sharing" to true)).await()
                save(context, name, code, familyName)
                return code
            }
        }
        error(context.getString(R.string.no_free_code))
    }

    // Joins an existing family. Fails if nobody has made a family with that code.
    suspend fun join(context: Context, name: String, typedCode: String) {
        val code = typedCode.trim().uppercase()
        val uid = myId()
        val family = db.collection("families").document(code)
        val found = family.get().await()
        if (!found.exists()) error(context.getString(R.string.no_family_with_code, code))
        family.collection("members").document(uid)
            .set(mapOf("name" to name, "sharing" to true), SetOptions.merge()).await()
        save(context, name, code, found.getString("name"))
    }

    // Changes my name, on this phone and for everyone else's map.
    suspend fun rename(context: Context, name: String) {
        val code = savedCode(context) ?: return
        val uid = myId()
        db.collection("families").document(code).collection("members").document(uid)
            .set(mapOf("name" to name), SetOptions.merge()).await()
        prefs(context).edit().putString("name", name).apply()
    }

    // Sets or removes my profile picture, for everyone's map and this phone's own dot.
    suspend fun setPhoto(context: Context, bytes: ByteArray?) {
        val code = savedCode(context) ?: return
        val uid = myId()
        val value: Any = if (bytes == null) FieldValue.delete() else Blob.fromBytes(bytes)
        db.collection("families").document(code).collection("members").document(uid)
            .set(mapOf("photo" to value), SetOptions.merge()).await()
        cachePhoto(context, bytes)
    }

    // My own dot is drawn outside Compose, so it reads the picture from here.
    fun savedPhoto(context: Context): ByteArray? =
        prefs(context).getString("photo", null)?.let { Base64.decode(it, Base64.NO_WRAP) }

    fun cachePhoto(context: Context, bytes: ByteArray?) {
        val editor = prefs(context).edit()
        if (bytes == null) editor.remove("photo") else editor.putString("photo", Base64.encodeToString(bytes, Base64.NO_WRAP))
        editor.apply()
    }

    // Changes the family's name for everyone. Only its creator may; the rules check that too.
    suspend fun renameFamily(context: Context, name: String) {
        val code = savedCode(context) ?: return
        db.collection("families").document(code).update("name", name).await()
        prefs(context).edit().putString("familyName", name).apply()
    }

    // Follows the family itself: its name (a rename by the creator shows up here too) and who made it.
    fun listenFamily(context: Context, onChange: (name: String?, createdBy: String?) -> Unit): ListenerRegistration? {
        val code = savedCode(context) ?: return null
        return db.collection("families").document(code).addSnapshotListener { snapshot, _ ->
            if (snapshot == null) return@addSnapshotListener
            val name = snapshot.getString("name")
            if (name != null && name != savedFamilyName(context)) {
                prefs(context).edit().putString("familyName", name).apply()
            }
            onChange(name, snapshot.getString("createdBy"))
        }
    }

    // Leaves the family: my record goes from Firebase, this phone forgets the family. My name stays
    // for next time, and sharing is back on for whatever family comes next.
    suspend fun leave(context: Context) {
        val code = savedCode(context) ?: return
        val uid = myId()
        db.collection("families").document(code).collection("members").document(uid).delete().await()
        prefs(context).edit()
            .remove("familyCode")
            .remove("familyName")
            .putBoolean("sharing", true)
            .apply()
    }

    // Overwrites my latest location. Nothing older is kept.
    fun sendLocation(context: Context, location: Location) {
        if (!isSharing(context)) return
        val code = savedCode(context) ?: return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val battery = (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        db.collection("families").document(code).collection("members").document(uid).set(
            mapOf(
                "name" to savedName(context),
                "lat" to location.latitude,
                "lng" to location.longitude,
                "battery" to battery,
                "sharing" to true,
                "updatedAt" to FieldValue.serverTimestamp(),
                "diag" to Diagnostics.snapshot(context),
            ),
            SetOptions.merge(),
        )
    }

    // Calls onChange with everyone in my family who has sent a location, every time anyone's changes.
    fun listen(context: Context, onChange: (List<Member>) -> Unit): ListenerRegistration? {
        val code = savedCode(context) ?: return null
        return db.collection("families").document(code).collection("members")
            .addSnapshotListener { snapshot, error ->
                if (error != null) Log.w("Mogar", "family listener error", error)
                if (snapshot == null) return@addSnapshotListener
                Log.d("Mogar", "family update: " + snapshot.documents.joinToString {
                    "${it.getString("name")}=${it.getBoolean("sharing")}@${it.getTimestamp("updatedAt")?.toDate()}"
                })
                val members = snapshot.documents.mapNotNull { doc ->
                    val lat = doc.getDouble("lat") ?: return@mapNotNull null
                    val lng = doc.getDouble("lng") ?: return@mapNotNull null
                    Member(
                        uid = doc.id,
                        name = doc.getString("name") ?: "?",
                        lat = lat,
                        lng = lng,
                        battery = doc.getLong("battery")?.toInt(),
                        updatedAtMillis = doc.getTimestamp("updatedAt")?.toDate()?.time,
                        sharing = doc.getBoolean("sharing") ?: true,
                        diag = (doc.get("diag") as? Map<*, *>)?.mapKeys { it.key.toString() },
                        photo = doc.getBlob("photo")?.toBytes(),
                    )
                }
                onChange(members)
            }
    }
}

// Lets the code wait for a Firebase call to finish instead of nesting callbacks.
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}
