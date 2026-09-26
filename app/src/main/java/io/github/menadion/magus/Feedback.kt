package io.github.menadion.magus

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

// One optional note from the family, sent from Settings. Each lands in the top-level "feedback"
// collection, which phones can add to but never read; tools/read-feedback.mjs reads it on the laptop.
object Feedback {
    enum class Worked { YES, NO }

    // Longest answer kept, so one pasted essay can't fill the database.
    private const val LIMIT = 1000

    // Not waited on: offline, Firestore keeps the note on the phone and sends it once it's online.
    suspend fun send(context: Context, worked: Worked?, landedOn: String, suggestion: String) {
        val fields = mutableMapOf<String, Any?>(
            "uid" to Family.myId(),
            "name" to Family.savedName(context),
            "familyCode" to Family.savedCode(context),
            "settingsWorked" to worked?.name?.lowercase(),
            "suggestion" to suggestion.trim().take(LIMIT),
            "language" to LanguageSetting.mode,
            "diag" to Diagnostics.snapshot(context),
            "createdAt" to FieldValue.serverTimestamp(),
        )
        if (worked == Worked.NO) fields["landedOn"] = landedOn.trim().take(LIMIT)
        FirebaseFirestore.getInstance().collection("feedback").add(fields)
            .addOnSuccessListener { Log.d("Mogar", "feedback saved") }
            .addOnFailureListener { Log.w("Mogar", "feedback failed", it) }
    }
}

// The box behind Settings > Send feedback: one yes/no question about the Keep Mogar running buttons,
// a "where did you end up" box only after a No, and a box for anything else. All of it optional.
@Composable
fun FeedbackDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var worked by remember { mutableStateOf<Feedback.Worked?>(null) }
    var landedOn by remember { mutableStateOf("") }
    var suggestion by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val fieldShape = RoundedCornerShape(12.dp)

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(stringResource(R.string.feedback), style = MaterialTheme.typography.headlineMedium) },
        text = {
            if (sent) {
                Text(stringResource(R.string.feedback_thanks), style = MaterialTheme.typography.bodyLarge)
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.feedback_settings_q), style = MaterialTheme.typography.bodyLarge)
                    val choices = listOf(
                        Feedback.Worked.YES to stringResource(R.string.feedback_yes),
                        Feedback.Worked.NO to stringResource(R.string.feedback_no),
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        choices.forEachIndexed { index, (value, text) ->
                            SegmentedButton(
                                selected = worked == value,
                                // Tapping the picked one again clears it, since the question is optional.
                                onClick = { worked = if (worked == value) null else value },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = choices.size),
                            ) { Text(text) }
                        }
                    }
                    if (worked == Feedback.Worked.NO) {
                        OutlinedTextField(
                            value = landedOn,
                            onValueChange = { landedOn = it },
                            label = { Text(stringResource(R.string.feedback_landed)) },
                            shape = fieldShape,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    OutlinedTextField(
                        value = suggestion,
                        onValueChange = { suggestion = it },
                        label = { Text(stringResource(R.string.feedback_suggest)) },
                        minLines = 3,
                        shape = fieldShape,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.feedback_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            if (sent) {
                TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
            } else {
                // Nothing to send until the question is answered or something is typed.
                val empty = worked == null && suggestion.isBlank()
                TextButton(
                    enabled = !busy && !empty,
                    onClick = {
                        busy = true
                        problem = null
                        scope.launch {
                            try {
                                Feedback.send(context, worked, landedOn, suggestion)
                                sent = true
                            } catch (e: Exception) {
                                problem = context.getString(R.string.something_wrong_connection)
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) { Text(stringResource(if (busy) R.string.sending else R.string.send)) }
            }
        },
        dismissButton = if (sent) null else {
            { TextButton(enabled = !busy, onClick = onClose) { Text(stringResource(R.string.cancel)) } }
        },
    )
}
