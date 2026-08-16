package com.scottstechx.commerceos.ui.common

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import java.util.Locale

/**
 * Voice-help helper. Launches the system speech-to-text intent and
 * returns the recognized text via [onResult]. If the device has no
 * STT engine installed, the launcher returns RESULT_CANCELED and we
 * silently no-op (no fake-success path).
 *
 * Uses ACTION_RECOGNIZE_SPEECH so we have NO dependency on Google
 * Play Services for this feature.
 */
@Composable
fun rememberVoiceInputLauncher(
    onResult: (String) -> Unit
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!text.isNullOrBlank()) onResult(text)
    }
    return {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now")
        }
        // Guard against no STT engine.
        runCatching { launcher.launch(intent) }
    }
}

@Composable
fun VoiceHelpButton(onResult: (String) -> Unit) {
    val launch = rememberVoiceInputLauncher(onResult)
    IconButton(onClick = launch) {
        Icon(
            Icons.Filled.Mic,
            contentDescription = "Voice input"
        )
    }
}
