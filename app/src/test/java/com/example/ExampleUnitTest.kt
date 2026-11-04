package com.example

import com.example.data.model.DocFilterType
import com.example.data.model.DocItem
import com.example.data.model.PdfToolType
import com.example.data.model.ScanFilter
import com.example.domain.StorageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartDocsUnitTest {

    @Test
    fun testFileSizeFormatting() {
        assertEquals("0 B", StorageManager.formatFileSize(0))
        assertEquals("500 B", StorageManager.formatFileSize(500))
        assertEquals("1.0 KB", StorageManager.formatFileSize(1024))
        assertEquals("2.5 MB", StorageManager.formatFileSize((2.5 * 1024 * 1024).toLong()))
        assertEquals("1.0 GB", StorageManager.formatFileSize((1024L * 1024 * 1024)))
    }

    @Test
    fun testPdfToolsCompleteness() {
        val tools = PdfToolType.values()
        assertTrue("Expected at least 14 PDF tools", tools.size >= 14)
        assertNotNull(PdfToolType.valueOf("IMAGES_TO_PDF"))
        assertNotNull(PdfToolType.valueOf("MERGE_PDF"))
        assertNotNull(PdfToolType.valueOf("SPLIT_PDF"))
        assertNotNull(PdfToolType.valueOf("COMPRESS_PDF"))
        assertNotNull(PdfToolType.valueOf("ROTATE_PDF"))
        assertNotNull(PdfToolType.valueOf("PROTECT_PDF"))
        assertNotNull(PdfToolType.valueOf("UNLOCK_PDF"))
        assertNotNull(PdfToolType.valueOf("ADD_WATERMARK"))
        assertNotNull(PdfToolType.valueOf("ADD_PAGE_NUMBERS"))
        assertNotNull(PdfToolType.valueOf("PDF_INFO"))
        assertNotNull(PdfToolType.valueOf("SCANNER"))
        assertNotNull(PdfToolType.valueOf("OCR"))
    }

    @Test
    fun testScanFilters() {
        val filters = ScanFilter.values()
        assertTrue(filters.contains(ScanFilter.ORIGINAL))
        assertTrue(filters.contains(ScanFilter.DOCUMENT_HIGH_CONTRAST))
        assertTrue(filters.contains(ScanFilter.GRAYSCALE))
        assertTrue(filters.contains(ScanFilter.MAGIC_COLOR))
    }
}
