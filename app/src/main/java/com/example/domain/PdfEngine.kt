package com.example.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument as AndroidPdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.data.model.PdfMetadata
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfEngine {

    private var isInitialized = false

    fun init(context: Context) {
        if (!isInitialized) {
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
                isInitialized = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun getPageCount(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                renderer = PdfRenderer(pfd)
                return@withContext renderer.pageCount
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
        return@withContext 1
    }

    suspend fun renderPageToBitmap(
        context: Context,
        uri: Uri,
        pageIndex: Int,
        targetWidth: Int = 1080
    ): Bitmap? = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext null
            renderer = PdfRenderer(pfd)
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null
            page = renderer.openPage(pageIndex)

            val width = targetWidth
            val height = (targetWidth.toFloat() * page.height / page.width).toInt().coerceAtLeast(100)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return@withContext bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        } finally {
            try { page?.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    suspend fun renderThumbnails(
        context: Context,
        uri: Uri,
        maxPages: Int = 20,
        thumbWidth: Int = 300
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val bitmaps = mutableListOf<Bitmap>()
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext emptyList()
            renderer = PdfRenderer(pfd)
            val count = minOf(renderer.pageCount, maxPages)
            for (i in 0 until count) {
                val page = renderer.openPage(i)
                val width = thumbWidth
                val height = (thumbWidth.toFloat() * page.height / page.width).toInt().coerceAtLeast(50)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bitmaps.add(bitmap)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
        return@withContext bitmaps
    }

    suspend fun readMetadata(context: Context, uri: Uri): Result<PdfMetadata> = withContext(Dispatchers.IO) {
        init(context)
        val tempFile = StorageManager.copyUriToTempFile(context, uri)
        try {
            PDDocument.load(tempFile).use { doc ->
                val info = doc.documentInformation
                val count = doc.numberOfPages
                var dimStr = "Unknown"
                if (count > 0) {
                    val firstPage = doc.getPage(0)
                    val box = firstPage.mediaBox
                    val wInches = box.width / 72f
                    val hInches = box.height / 72f
                    dimStr = String.format(Locale.US, "%.1f x %.1f in (%d x %d pt)", wInches, hInches, box.width.toInt(), box.height.toInt())
                }
                val sdf = SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault())
                val cDate = info?.creationDate?.time?.let { sdf.format(it) }
                val mDate = info?.modificationDate?.time?.let { sdf.format(it) }

                val meta = PdfMetadata(
                    fileName = StorageManager.getFileName(context, uri),
                    title = info?.title,
                    author = info?.author,
                    subject = info?.subject,
                    creator = info?.creator,
                    producer = info?.producer,
                    creationDate = cDate,
                    modDate = mDate,
                    pageCount = count,
                    fileSizeFormatted = StorageManager.formatFileSize(tempFile.length()),
                    dimensionsFormatted = dimStr,
                    isEncrypted = doc.isEncrypted,
                    version = doc.version
                )
                return@withContext Result.success(meta)
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        } finally {
            tempFile.delete()
        }
    }

    suspend fun imagesToPdf(
        context: Context,
        imageUris: List<Uri>,
        outputFile: File,
        pageSize: String = "A4", // A4 or FIT
        marginDp: Int = 16
    ): Result<File> = withContext(Dispatchers.IO) {
        if (imageUris.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No images selected"))
        }
        val document = AndroidPdfDocument()
        val density = context.resources.displayMetrics.density
        val marginPx = (marginDp * density).toInt()

        try {
            imageUris.forEachIndexed { index, uri ->
                val inputStream = context.contentResolver.openInputStream(uri)
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (originalBitmap != null) {
                    val pageWidth: Int
                    val pageHeight: Int

                    if (pageSize == "FIT") {
                        pageWidth = originalBitmap.width + (marginPx * 2)
                        pageHeight = originalBitmap.height + (marginPx * 2)
                    } else {
                        // Standard A4 at 72dpi: 595 x 842 points
                        pageWidth = 595
                        pageHeight = 842
                    }

                    val pageInfo = AndroidPdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                    val page = document.startPage(pageInfo)
                    val canvas = page.canvas

                    canvas.drawColor(Color.WHITE)

                    val availableWidth = (pageWidth - (marginPx * 2)).toFloat()
                    val availableHeight = (pageHeight - (marginPx * 2)).toFloat()

                    val scale = minOf(
                        availableWidth / originalBitmap.width.toFloat(),
                        availableHeight / originalBitmap.height.toFloat()
                    )

                    val scaledWidth = originalBitmap.width * scale
                    val scaledHeight = originalBitmap.height * scale

                    val left = marginPx + (availableWidth - scaledWidth) / 2f
                    val top = marginPx + (availableHeight - scaledHeight) / 2f

                    val destRect = RectF(left, top, left + scaledWidth, top + scaledHeight)
                    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                    canvas.drawBitmap(originalBitmap, null, destRect, paint)

                    document.finishPage(page)
                    originalBitmap.recycle()
                }
            }

            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
            return@withContext Result.success(outputFile)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        } finally {
            document.close()
        }
    }

    suspend fun textToPdf(
        context: Context,
        title: String,
        content: String,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        val document = AndroidPdfDocument()
        try {
            val pageWidth = 595
            val pageHeight = 842
            val margin = 50
            val contentWidth = pageWidth - (margin * 2)
            val contentHeight = pageHeight - (margin * 2)

            val titlePaint = TextPaint().apply {
                color = Color.parseColor("#1E1B4B")
                textSize = 22f
                isFakeBoldText = true
                isAntiAlias = true
            }

            val datePaint = TextPaint().apply {
                color = Color.parseColor("#64748B")
                textSize = 10f
                isAntiAlias = true
            }

            val bodyPaint = TextPaint().apply {
                color = Color.parseColor("#1E293B")
                textSize = 13f
                isAntiAlias = true
            }

            val pageNumPaint = TextPaint().apply {
                color = Color.parseColor("#94A3B8")
                textSize = 9f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }

            val linePaint = Paint().apply {
                color = Color.parseColor("#E2E8F0")
                strokeWidth = 1f
            }

            val dateStr = SimpleDateFormat("MMMM d, yyyy  h:mm a", Locale.getDefault()).format(Date())

            val staticLayout = StaticLayout.Builder.obtain(content, 0, content.length, bodyPaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(4f, 1.15f)
                .setIncludePad(true)
                .build()

            val totalLines = staticLayout.lineCount
            var currentLine = 0
            var pageIndex = 1

            while (currentLine < totalLines || pageIndex == 1) {
                val pageInfo = AndroidPdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas

                canvas.drawColor(Color.WHITE)

                var yOffset = margin.toFloat()

                if (pageIndex == 1) {
                    canvas.drawText(title, margin.toFloat(), yOffset + 20f, titlePaint)
                    yOffset += 30f
                    canvas.drawText("Created with SmartDocs • $dateStr", margin.toFloat(), yOffset + 10f, datePaint)
                    yOffset += 24f
                    canvas.drawLine(margin.toFloat(), yOffset, (pageWidth - margin).toFloat(), yOffset, linePaint)
                    yOffset += 16f
                }

                val usableHeight = (pageHeight - margin) - yOffset - 30f // reserve for page number

                var pageLinesCount = 0
                val lineStart = currentLine
                var currentBatchHeight = 0f

                while (currentLine < totalLines) {
                    val lh = staticLayout.getLineBottom(currentLine) - staticLayout.getLineTop(currentLine)
                    if (currentBatchHeight + lh > usableHeight) {
                        break
                    }
                    currentBatchHeight += lh
                    currentLine++
                    pageLinesCount++
                }

                if (pageLinesCount > 0) {
                    canvas.save()
                    canvas.translate(margin.toFloat(), yOffset)
                    val clipTop = staticLayout.getLineTop(lineStart)
                    val clipBottom = staticLayout.getLineBottom(currentLine - 1)
                    canvas.clipRect(0, 0, contentWidth, clipBottom - clipTop)
                    canvas.translate(0f, -clipTop.toFloat())
                    staticLayout.draw(canvas)
                    canvas.restore()
                }

                // Page Number
                canvas.drawText("Page $pageIndex", pageWidth / 2f, (pageHeight - 25).toFloat(), pageNumPaint)

                document.finishPage(page)
                pageIndex++

                if (currentLine >= totalLines) break
            }

            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
            return@withContext Result.success(outputFile)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        } finally {
            document.close()
        }
    }

    suspend fun mergePdfs(
        context: Context,
        inputUris: List<Uri>,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        init(context)
        if (inputUris.size < 2) {
            return@withContext Result.failure(IllegalArgumentException("Please select at least 2 PDF files to merge"))
        }

        val tempFiles = mutableListOf<File>()
        try {
            val merger = PDFMergerUtility()
            inputUris.forEach { uri ->
                val temp = StorageManager.copyUriToTempFile(context, uri)
                tempFiles.add(temp)
                merger.addSource(temp)
            }
            merger.destinationFileName = outputFile.absolutePath
            merger.mergeDocuments(null)
            return@withContext Result.success(outputFile)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        } finally {
            tempFiles.forEach { it.delete() }
        }
    }

    suspend fun splitPdf(
        context: Context,
        inputUri: Uri,
        pageRangeText: String, // e.g. "1-3, 5" or "all"
        outputDir: File
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        init(context)
        val tempFile = StorageManager.copyUriToTempFile(context, uri = inputUri)
        val createdFiles = mutableListOf<File>()

        try {
            PDDocument.load(tempFile).use { srcDoc ->
                val totalPages = srcDoc.numberOfPages
                val selectedIndices = parsePageRanges(pageRangeText, totalPages)

                if (selectedIndices.isEmpty()) {
                    return@withContext Result.failure(IllegalArgumentException("No valid pages specified in range: $pageRangeText"))
                }

                val outDoc = PDDocument()
                for (pageIdx in selectedIndices) {
                    outDoc.addPage(srcDoc.getPage(pageIdx))
                }
                val baseName = StorageManager.getFileName(context, inputUri).removeSuffix(".pdf")
                val outFile = File(outputDir, "${baseName}_split_${System.currentTimeMillis()}.pdf")
                outDoc.save(outFile)
                outDoc.close()
                createdFiles.add(outFile)

                return@withContext Result.success(createdFiles)
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        } finally {
            tempFile.delete()
        }
    }

    suspend fun rotatePdfPages(
        context: Context,
        inputUri: Uri,
        angleDegrees: Int, // 90, 180, 270
        selectedPageIndices: List<Int>?, // null means all pages
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        init(context)
        val tempFile = StorageManager.copyUriToTempFile(context, inputUri)
        try {
            PDDocument.load(tempFile).use { doc ->
                val total = doc.numberOfPages
                for (i in 0 until total) {
                    if (selectedPageIndices == null || selectedPageIndices.contains(i)) {
                        val page = doc.getPage(i)
                        val newRotation = (page.rotation + angleDegrees) % 360
                        page.rotation = newRotation
                    }
                }
                doc.save(outputFile)
                return@withContext Result.success(outputFile)
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        } finally {
            tempFile.delete()
        }
    }

    suspend fun reorderPdfPages(
        context: Context,
        inputUri: Uri,
        newOrderIndices: List<Int>,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        init(context)
        val tempFile = StorageManager.copyUriToTempFile(context, inputUri)
        try {
            PDDocument.load(tempFile).use { srcDoc ->
                val outDoc = PDDocument()
                for (index in newOrderIndices) {
                    if (index in 0 until srcDoc.numberOfPages) {
                        outDoc.addPage(srcDoc.getPage(index))
                    }
                }
                outDoc.save(outputFile)
                outDoc.close()
                return@withContext Result.success(outputFile)
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        } finally {
            tempFile.delete()
        }
    }

    suspend fun deletePdfPages(
        context: Context,
        inputUri: Uri,
        indicesToDelete: Set<Int>,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        init(context)
        val tempFile = StorageManager.copyUriToTempFile(context, inputUri)
        try {
            PDDocument.load(tempFile).use { srcDoc ->
                val total = srcDoc.numberOfPages
                val remainingIndices = (0 until total).filter { !indicesToDelete.contains(it) }
                if (remainingIndices.isEmpty()) {
                    return@withContext Result.failure(IllegalArgumentException("Cannot delete all pages of the document"))
                }

                val outDoc = PDDocument()
                for (idx in remainingIndices) {
                    outDoc.addPage(srcDoc.getPage(idx))
                }
                outDoc.save(outputFile)
                outDoc.close()
                return@withContext Result.success(outputFile)
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        } finally {
            tempFile.delete()
        }
    }

    suspend fun extractPdfPages(
        context: Context,
        inputUri: Uri,
        indicesToExtract: List<Int>,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        init(context)
        val tempFile = StorageManager.copyUriToTempFile(context, inputUri)
        try {
            PDDocument.load(tempFile).use { srcDoc ->
                if (indicesToExtract.isEmpty()) {
                    return@withContext Result.failure(IllegalArgumentException("No pages selected for extraction"))
                }
                val outDoc = PDDocument()
                for (idx in indicesToExtract) {
                    if (idx in 0 until srcDoc.numberOfPages) {
                        outDoc.addPage(srcDoc.getPage(idx))
                    }
                }
                outDoc.save(outputFile)
                outDoc.close()
                return@withContext Result.success(outputFile)
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        } finally {
            tempFile.delete()
        }
    }

    suspend fun protectPdf(
        context: Context,
        inputUri: Uri,
        userPassword: String,
        ownerPassword: String,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        init(context)
        if (userPassword.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Password cannot be empty"))
        }
        val tempFile = StorageManager.copyUriToTempFile(context, inputUri)
        try {
            PDDocument.load(tempFile).use { doc ->
                val permission = AccessPermission()
                val policy = StandardProtectionPolicy(ownerPassword.ifBlank { userPassword }, userPassword, permission)
                policy.encryptionKeyLength = 128
                doc.protect(policy)
                doc.save(outputFile)
                return@withContext Result.success(outputFile)
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        } finally {
            tempFile.delete()
        }
    }

    suspend fun unlockPdf(
        context: Context,
        inputUri: Uri,
        password: String,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        init(context)
        val tempFile = StorageManager.copyUriToTempFile(context, inputUri)
        try {
            PDDocument.load(tempFile, password).use { doc ->
                doc.isAllSecurityToBeRemoved = true
                doc.save(outputFile)
                return@withContext Result.success(outputFile)
            }
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("Incorrect password or failed to unlock PDF: ${e.message}", e))
        } finally {
            tempFile.delete()
        }
    }

    suspend fun addWatermark(
        context: Context,
        inputUri: Uri,
        watermarkText: String,
        fontSize: Float = 42f,
        opacity: Float = 0.35f,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        init(context)
        if (watermarkText.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Watermark text cannot be empty"))
        }
        val tempFile = StorageManager.copyUriToTempFile(context, inputUri)
        try {
            PDDocument.load(tempFile).use { doc ->
                val font = PDType1Font.HELVETICA_BOLD
                val total = doc.numberOfPages

                for (i in 0 until total) {
                    val page = doc.getPage(i)
                    val box = page.mediaBox
                    val textWidth = font.getStringWidth(watermarkText) / 1000 * fontSize

                    val cs = PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)
                    val gstate = PDExtendedGraphicsState()
                    gstate.nonStrokingAlphaConstant = opacity
                    cs.setGraphicsStateParameters(gstate)
                    cs.setNonStrokingColor(180, 180, 190)

                    cs.beginText()
                    cs.setFont(font, fontSize)

                    // Position in center and rotate 45 degrees
                    val cx = box.width / 2f
                    val cy = box.height / 2f
                    val rad = Math.toRadians(45.0)
                    val cos = Math.cos(rad).toFloat()
                    val sin = Math.sin(rad).toFloat()

                    val tx = cx - (textWidth / 2f * cos)
                    val ty = cy - (textWidth / 2f * sin)

                    cs.setTextMatrix(cos.toDouble(), sin.toDouble(), (-sin).toDouble(), cos.toDouble(), tx.toDouble(), ty.toDouble())
                    cs.showText(watermarkText)
                    cs.endText()
                    cs.close()
                }
                doc.save(outputFile)
                return@withContext Result.success(outputFile)
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        } finally {
            tempFile.delete()
        }
    }

    suspend fun addPageNumbers(
        context: Context,
        inputUri: Uri,
        position: String = "BOTTOM_CENTER", // BOTTOM_CENTER, BOTTOM_RIGHT, TOP_RIGHT
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        init(context)
        val tempFile = StorageManager.copyUriToTempFile(context, inputUri)
        try {
            PDDocument.load(tempFile).use { doc ->
                val font = PDType1Font.HELVETICA
                val fontSize = 10f
                val total = doc.numberOfPages

                for (i in 0 until total) {
                    val page = doc.getPage(i)
                    val box = page.mediaBox
                    val pageStr = "Page ${i + 1} of $total"
                    val strWidth = font.getStringWidth(pageStr) / 1000 * fontSize

                    val cs = PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)
                    cs.setNonStrokingColor(100, 116, 139)
                    cs.beginText()
                    cs.setFont(font, fontSize)

                    val x: Float
                    val y: Float

                    when (position) {
                        "BOTTOM_RIGHT" -> {
                            x = box.width - strWidth - 36f
                            y = 30f
                        }
                        "TOP_RIGHT" -> {
                            x = box.width - strWidth - 36f
                            y = box.height - 30f
                        }
                        else -> { // BOTTOM_CENTER
                            x = (box.width - strWidth) / 2f
                            y = 30f
                        }
                    }

                    cs.newLineAtOffset(x, y)
                    cs.showText(pageStr)
                    cs.endText()
                    cs.close()
                }
                doc.save(outputFile)
                return@withContext Result.success(outputFile)
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        } finally {
            tempFile.delete()
        }
    }

    suspend fun compressPdf(
        context: Context,
        inputUri: Uri,
        quality: Int = 65, // 0-100
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        init(context)
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        val outDoc = PDDocument()
        try {
            pfd = context.contentResolver.openFileDescriptor(inputUri, "r") ?: return@withContext Result.failure(Exception("Cannot open PDF file"))
            renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount

            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                val scale = 1.25f // Optimized resolution for compression
                val w = (page.width * scale).toInt()
                val h = (page.height * scale).toInt()

                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val pdPage = PDPage(PDRectangle(page.width.toFloat(), page.height.toFloat()))
                outDoc.addPage(pdPage)

                val pdImage = JPEGFactory.createFromImage(outDoc, bitmap, quality / 100f)
                val cs = PDPageContentStream(outDoc, pdPage)
                cs.drawImage(pdImage, 0f, 0f, page.width.toFloat(), page.height.toFloat())
                cs.close()
                bitmap.recycle()
            }

            outDoc.save(outputFile)
            return@withContext Result.success(outputFile)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        } finally {
            outDoc.close()
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    suspend fun extractTextFromPdf(context: Context, inputUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        init(context)
        val tempFile = StorageManager.copyUriToTempFile(context, inputUri)
        try {
            PDDocument.load(tempFile).use { doc ->
                val stripper = PDFTextStripper()
                val text = stripper.getText(doc)
                return@withContext Result.success(text)
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        } finally {
            tempFile.delete()
        }
    }

    private fun parsePageRanges(rangeText: String, totalPages: Int): List<Int> {
        val indices = linkedSetOf<Int>()
        val parts = rangeText.split(",", ";", " ")
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.equals("all", ignoreCase = true)) {
                return (0 until totalPages).toList()
            }
            if (trimmed.contains("-")) {
                val bounds = trimmed.split("-")
                if (bounds.size == 2) {
                    val start = bounds[0].trim().toIntOrNull()?.let { it - 1 } ?: 0
                    val end = bounds[1].trim().toIntOrNull()?.let { it - 1 } ?: (totalPages - 1)
                    val from = start.coerceIn(0, totalPages - 1)
                    val to = end.coerceIn(0, totalPages - 1)
                    if (from <= to) {
                        for (i in from..to) indices.add(i)
                    } else {
                        for (i in from downTo to) indices.add(i)
                    }
                }
            } else {
                val num = trimmed.toIntOrNull()
                if (num != null) {
                    val idx = num - 1
                    if (idx in 0 until totalPages) {
                        indices.add(idx)
                    }
                }
            }
        }
        return indices.toList()
    }
}
