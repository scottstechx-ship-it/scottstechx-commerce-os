package com.scottstechx.commerceos.ui.common

import android.content.Context
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Plain-English "what is this screen" dialog. We use the same component
 * across every screen's help action so the wording is consistent and
 * accessible (no jargon, no abbreviations).
 */
@Composable
fun HelpDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                body,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        }
    )
}

/**
 * Fires an ACTION_SEND chooser. We don't force a particular text-sharing
 * app — Android shows the user their installed options.
 */
fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(intent, "Share ScottsTechX")
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}
