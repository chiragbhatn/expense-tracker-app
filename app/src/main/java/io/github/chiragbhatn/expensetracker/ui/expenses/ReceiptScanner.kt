package io.github.chiragbhatn.expensetracker.ui.expenses

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device text recognition for receipt photos (ML Kit, bundled model, so
 * it works offline). It only reads text; [io.github.chiragbhatn.expensetracker.domain.ReceiptParser]
 * turns that into proposed values for the user to confirm.
 */
object ReceiptScanner {
    /** A new file for the camera to write a receipt photo to, and its content URI. */
    fun newPhoto(context: Context): Pair<File, Uri> {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() }
        val file = File(dir, "receipt-${System.currentTimeMillis()}.jpg")
        return file to FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /** Copies a picked image into the cache, so it can be attached to the expense later. */
    suspend fun copyToCache(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        runCatching {
            val (file, _) = newPhoto(context)
            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } } ?: return@runCatching null
            file
        }.getOrNull()
    }

    suspend fun readText(context: Context, uri: Uri): String {
        val image = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            suspendCancellableCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { result -> continuation.resume(result.text) }
                    .addOnFailureListener { error -> continuation.resumeWithException(error) }
            }
        } finally {
            recognizer.close()
        }
    }
}
