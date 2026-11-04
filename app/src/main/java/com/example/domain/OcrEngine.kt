package com.example.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object OcrEngine {

    suspend fun recognizeTextFromBitmap(bitmap: Bitmap): Result<String> = withContext(Dispatchers.Default) {
        try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = InputImage.fromBitmap(bitmap, 0)
            val task = recognizer.process(image)
            val visionText = Tasks.await(task)

            val text = visionText.text
            if (text.isBlank()) {
                return@withContext Result.failure(Exception("No readable text found in the image."))
            }
            return@withContext Result.success(text)
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("Text recognition failed: ${e.message}", e))
        }
    }

    suspend fun recognizeTextFromUri(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val input = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(input)
            input?.close()
            if (bitmap == null) {
                return@withContext Result.failure(Exception("Failed to decode image from file."))
            }
            val result = recognizeTextFromBitmap(bitmap)
            bitmap.recycle()
            return@withContext result
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("Could not load image: ${e.message}", e))
        }
    }
}
