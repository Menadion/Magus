package io.github.menadion.magus

import android.content.Context
import android.location.Location
import android.os.BatteryManager
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
)

// Everything the app knows about "my family": what's saved on this phone, and what's in Firebase.
//
// In Firebase:
//   families/{code}                   who made it, and when
//   families/{code}/members/{uid}     name, latest location, battery, last seen (overwritten, never a history)
object Family {
    // No I or O, so nobody reads a 1 or a 0 by mistake.
    private const val CODE_LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ"

    private fun prefs(context: Context) = context.getSharedPreferences("magus", Context.MODE_PRIVATE)
    private val db get() = FirebaseFirestore.getInstance()

    fun savedName(context: Context): String? = prefs(context).getString("name", null)
    fun savedCode(context: Context): String? = prefs(context).getString("familyCode", null)

    private fun save(context: Context, name: String, code: String) {
        prefs(context).edit().putString("name", name).putString("familyCode", code).apply()
    }

    // Signs in anonymously if this phone hasn't yet, and returns this phone's ID.
    suspend fun myId(): String {
        val auth = FirebaseAuth.getInstance()
        auth.currentUser?.let { return it.uid }
        return auth.signInAnonymously().await().user!!.uid
    }

    // Makes a new family with a fresh code, adds me to it, and returns the code.
    suspend fun create(context: Context, name: String): String {
        val uid = myId()
        repeat(5) {
            val code = (1..6).map { CODE_LETTERS.random() }.joinToString("")
            val family = db.collection("families").document(code)
            if (!family.get().await().exists()) {
                family.set(mapOf("createdBy" to uid, "createdAt" to FieldValue.serverTimestamp())).await()
                family.collection("members").document(uid).set(mapOf("name" to name)).await()
                save(context, name, code)
                return code
            }
        }
        error("Couldn't find a free family code. Try again.")
    }

    // Joins an existing family. Fails if nobody has made a family with that code.
    suspend fun join(context: Context, name: String, typedCode: String) {
        val code = typedCode.trim().uppercase()
        val uid = myId()
        val family = db.collection("families").document(code)
        if (!family.get().await().exists()) error("No family with the code $code.")
        family.collection("members").document(uid).set(mapOf("name" to name), SetOptions.merge()).await()
        save(context, name, code)
    }

    // Overwrites my latest location. Nothing older is kept.
    fun sendLocation(context: Context, location: Location) {
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
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        )
    }

    // Calls onChange with everyone in my family who has sent a location, every time anyone's changes.
    fun listen(context: Context, onChange: (List<Member>) -> Unit): ListenerRegistration? {
        val code = savedCode(context) ?: return null
        return db.collection("families").document(code).collection("members")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null) return@addSnapshotListener
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
