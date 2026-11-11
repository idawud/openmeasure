package app.openmeasure

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** The accuracy disclaimer text, reachable from Settings > About. */
@Composable
fun AccuracyDisclaimerDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.disclaimer_title)) },
        text = { Text(stringResource(R.string.disclaimer_body)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.disclaimer_accept)) } },
    )
}
