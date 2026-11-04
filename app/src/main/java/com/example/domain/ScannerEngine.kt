package com.example.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import com.example.data.model.ScanFilter
import com.example.data.model.ScanPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ScannerEngine {

    suspend fun processScanPage(
        context: Context,
        page: ScanPage
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val input = context.contentResolver.openInputStream(page.uri)
            val original = BitmapFactory.decodeStream(input)
            input?.close()
            if (original == null) return@withContext null

            // Apply rotation if needed
            val rotated = if (page.rotationDegrees % 360 != 0) {
                val matrix = Matrix().apply { postRotate(page.rotationDegrees.toFloat()) }
                val rot = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
                if (rot != original) original.recycle()
                rot
            } else {
                original
            }

            // Apply filter
            val filtered = when (page.filter) {
                ScanFilter.ORIGINAL -> rotated
                ScanFilter.GRAYSCALE -> applyGrayscale(rotated)
                ScanFilter.DOCUMENT_HIGH_CONTRAST -> applyHighContrast(rotated)
                ScanFilter.MAGIC_COLOR -> applyMagicColor(rotated)
            }

            if (filtered != rotated && rotated != original) {
                rotated.recycle()
            }
            return@withContext filtered
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    private fun applyGrayscale(src: Bitmap): Bitmap {
        val dest = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint()
        val cm = ColorMatrix().apply { setSaturation(0f) }
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return dest
    }

    private fun applyHighContrast(src: Bitmap): Bitmap {
        val dest = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint()

        // Convert to grayscale and apply contrast curve
        val cm = ColorMatrix().apply {
            setSaturation(0f)
            // Increase contrast: scale RGB values and adjust offset
            val contrast = 1.4f
            val translate = (-0.5f * contrast + 0.5f) * 255f + 15f
            val matrixArray = floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
            postConcat(ColorMatrix(matrixArray))
        }
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return dest
    }

    private fun applyMagicColor(src: Bitmap): Bitmap {
        val dest = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint()
        val cm = ColorMatrix().apply {
            setSaturation(1.3f)
            val contrast = 1.15f
            val translate = (-0.5f * contrast + 0.5f) * 255f + 5f
            val matrixArray = floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
            postConcat(ColorMatrix(matrixArray))
        }
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return dest
    }

    suspend fun saveScanSessionAsPdf(
        context: Context,
        pages: List<ScanPage>,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No scanned pages to save"))
        }

        // Render each page with its filters to a temporary JPG, then compile to PDF
        val tempUris = mutableListOf<Uri>()
        val tempFiles = mutableListOf<File>()

        try {
            for (page in pages) {
                val processedBitmap = processScanPage(context, page)
                if (processedBitmap != null) {
                    val temp = File(StorageManager.getCacheDir(context), "proc_${UUID.randomUUID()}.jpg")
                    FileOutputStream(temp).use { out ->
                        processedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }
                    processedBitmap.recycle()
                    tempFiles.add(temp)
                    tempUris.add(Uri.fromFile(temp))
                }
            }

            return@withContext PdfEngine.imagesToPdf(
                context = context,
                imageUris = tempUris,
                outputFile = outputFile,
                pageSize = "A4",
                marginDp = 10
            )
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        } finally {
            tempFiles.forEach { it.delete() }
        }
    }

    suspend fun saveScanSessionAsImages(
        context: Context,
        pages: List<ScanPage>,
        baseName: String
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        val savedFiles = mutableListOf<File>()
        try {
            val dir = StorageManager.getScansDir(context)
            pages.forEachIndexed { index, page ->
                val processedBitmap = processScanPage(context, page)
                if (processedBitmap != null) {
                    val file = File(dir, "${baseName}_page_${index + 1}.jpg")
                    FileOutputStream(file).use { out ->
                        processedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }
                    processedBitmap.recycle()
                    savedFiles.add(file)
                }
            }
            return@withContext Result.success(savedFiles)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }
}
