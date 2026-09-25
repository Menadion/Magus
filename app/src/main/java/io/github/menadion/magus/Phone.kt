package io.github.menadion.magus

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

// A member's optional phone number. Philippine mobiles only: "+63" is fixed, the person types the
// ten digits after it, and it is stored as +639XXXXXXXXX so the phone's dialer and messaging app
// take it as is. Nothing checks that the number is really theirs; it is a label for the family,
// not a login. Spec: M's calls 2026-09-26 (phone numbers only, the load request dropped).
object Phone {
    const val PREFIX = "+63"

    // What the person types, cleaned on every keystroke: digits only, a leading 0 dropped, ten at most.
    fun clean(typed: String): String = typed.filter { it.isDigit() }.trimStart('0').take(10)

    // The ten digits of a stored number, or "" for none.
    fun digits(stored: String?): String = stored?.removePrefix(PREFIX) ?: ""

    // What goes in the member record: null when the field was left empty.
    fun store(digits: String): String? = if (digits.isBlank()) null else PREFIX + digits

    // "+63 917 123 4567"
    fun display(stored: String): String {
        val d = digits(stored)
        return listOf(PREFIX, d.take(3), d.drop(3).take(3), d.drop(6)).filter { it.isNotEmpty() }.joinToString(" ")
    }

    fun call(context: Context, stored: String) = open(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$stored")))

    fun text(context: Context, stored: String) = open(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$stored")))

    fun copy(context: Context, stored: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Phone number", stored))
    }

    // A tablet or an emulator may have no dialer; then the tap does nothing rather than crash.
    private fun open(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
        }
    }
}
