package com.example.data.model

import android.net.Uri

data class DocItem(
    val id: String,
    val name: String,
    val uri: Uri,
    val path: String,
    val mimeType: String,
    val sizeBytes: Long,
    val pageCount: Int = 1,
    val lastModified: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val lastReadPage: Int = 1
)

enum class DocFilterType {
    ALL, PDF, SCANS, TEXT
}

enum class SortOrder {
    DATE_DESC, DATE_ASC, NAME_ASC, SIZE_DESC
}

data class PdfMetadata(
    val fileName: String,
    val title: String? = null,
    val author: String? = null,
    val subject: String? = null,
    val creator: String? = null,
    val producer: String? = null,
    val creationDate: String? = null,
    val modDate: String? = null,
    val pageCount: Int = 0,
    val fileSizeFormatted: String = "",
    val dimensionsFormatted: String = "",
    val isEncrypted: Boolean = false,
    val version: Float = 1.4f
)

data class ScanPage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val uri: Uri,
    val rotationDegrees: Int = 0,
    val filter: ScanFilter = ScanFilter.ORIGINAL
)

enum class ScanFilter(val label: String) {
    ORIGINAL("Original"),
    DOCUMENT_HIGH_CONTRAST("Enhanced B&W"),
    GRAYSCALE("Grayscale"),
    MAGIC_COLOR("Vibrant")
}

enum class PdfToolType(
    val id: String,
    val title: String,
    val category: ToolCategory,
    val description: String,
    val iconName: String
) {
    IMAGES_TO_PDF("images_to_pdf", "Images to PDF", ToolCategory.CONVERT, "Convert photos & images into clean PDF documents", "image"),
    TEXT_TO_PDF("text_to_pdf", "Text to PDF", ToolCategory.CONVERT, "Turn formatted text and notes into PDF files", "edit_note"),
    MERGE_PDF("merge_pdf", "Merge PDF", ToolCategory.ORGANIZE, "Combine multiple PDF files into a single document", "call_merge"),
    SPLIT_PDF("split_pdf", "Split PDF", ToolCategory.ORGANIZE, "Extract specific page ranges into separate files", "call_split"),
    COMPRESS_PDF("compress_pdf", "Compress PDF", ToolCategory.OPTIMIZE, "Reduce PDF file size while preserving readability", "compress"),
    ROTATE_PDF("rotate_pdf", "Rotate PDF", ToolCategory.EDIT, "Rotate pages 90°, 180°, or 270° and save", "rotate_right"),
    REORDER_PDF("reorder_pdf", "Reorder Pages", ToolCategory.EDIT, "Rearrange page ordering visually", "reorder"),
    EXTRACT_PAGES("extract_pages", "Extract Pages", ToolCategory.ORGANIZE, "Extract selected pages into a new PDF", "content_copy"),
    DELETE_PAGES("delete_pages", "Delete Pages", ToolCategory.EDIT, "Remove unwanted pages from your document", "delete"),
    PROTECT_PDF("protect_pdf", "Protect PDF", ToolCategory.SECURITY, "Add password encryption to your PDF", "lock"),
    UNLOCK_PDF("unlock_pdf", "Unlock PDF", ToolCategory.SECURITY, "Remove password protection from encrypted PDFs", "lock_open"),
    ADD_WATERMARK("add_watermark", "Add Watermark", ToolCategory.EDIT, "Apply customizable text watermarks to pages", "branding_watermark"),
    ADD_PAGE_NUMBERS("add_page_numbers", "Add Page Numbers", ToolCategory.EDIT, "Insert clean header or footer page numbers", "format_list_numbered"),
    PDF_INFO("pdf_info", "PDF Information", ToolCategory.VIEW, "Inspect page count, dimensions, and metadata", "info"),
    SCANNER("scanner", "Doc Scanner", ToolCategory.SCAN, "Scan multi-page documents via camera", "document_scanner"),
    OCR("ocr", "Text OCR", ToolCategory.SCAN, "Extract editable text from images on-device", "text_snippet")
}

enum class ToolCategory(val title: String) {
    CONVERT("Convert & Create"),
    ORGANIZE("Merge & Split"),
    EDIT("Edit & Pages"),
    SECURITY("Security"),
    SCAN("Scan & OCR"),
    OPTIMIZE("Optimize"),
    VIEW("Information")
}
