package com.scottstechx.commerceos.data.capture

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helpers for proof-of-delivery capture. The Driver screen asks the
 * caller to create a destination [Uri] via [newPhotoUri], then the
 * camera intent writes the JPEG to that Uri, and the Driver screen
 * calls [encodeJpeg] to base64-encode the bytes into the PodRequest.
 */
@Singleton
class PodCapture @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Create a private, file-provider-backed Uri in the app's
     * cache/pod/ directory. The CameraX / system camera intent writes
     * to this Uri, and the caller reads it back via [encodeJpeg].
     */
    fun newPhotoUri(): Uri {
        val dir = File(context.cacheDir, "pod").apply { mkdirs() }
        val file = File.createTempFile(
            "pod_${System.currentTimeMillis()}_",
            ".jpg",
            dir
        )
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    /**
     * Read the JPEG at the given Uri and return its base64 encoding
     * (no line wrapping). Returns null on any I/O failure.
     */
    fun encodeJpeg(uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            Base64.getEncoder().withoutPadding().encodeToString(input.readBytes())
        }
    }.getOrNull()
}

@Module
@InstallIn(SingletonComponent::class)
object PodCaptureModule {
    @Provides
    @Singleton
    fun providePodCapture(
        @ApplicationContext context: Context
    ): PodCapture = PodCapture(context)
}
