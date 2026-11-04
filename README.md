# SmartDocs — All-in-One PDF & Document Toolkit for Android

SmartDocs is a native, production-quality Android application built with Kotlin and Jetpack Compose. It provides complete offline document management, multi-page scanning, on-device OCR text extraction, and a full suite of PDF manipulation tools without requiring any paid APIs, accounts, or external server uploads.

---

## 📱 Pre-Built Installable APK

The compiled Android application package is ready for direct installation:

- **Root APK Path**: `SmartDocs.apk` (75 MB)
- **Gradle Build Output**: `app/build/outputs/apk/debug/app-debug.apk`
- **Application ID**: `com.smartdocs.app`
- **Version**: 1.0.0
- **Build Type**: Debug APK (ready to install on Android devices)
- **Minimum Android SDK**: Android 8.0 (API Level 26)
- **Target SDK**: Android 15 / 16 (API Level 36)

### How to Install on an Android Phone:
1. Download `SmartDocs.apk` directly to your Android device (or transfer via USB / cloud drive).
2. Tap the APK file to install.
3. If prompted, allow "Install unknown apps" from your file manager or browser.
4. Launch **SmartDocs** from your app drawer.

---

## 🛠️ Implemented Features

### 1. PDF Reader & Viewer
- **High-Fidelity Rendering**: Powered by native `android.graphics.pdf.PdfRenderer`.
- **Zoom & Pan**: Multi-touch pinch-to-zoom (up to 400%), double-tap, and manual zoom controls.
- **Page Navigation**: Previous/Next buttons, jump-to-page dialog, and thumbnail drawer.
- **Reading Progress**: Remembers last-read page using Room Database.
- **Text Stripping**: Extract raw text directly from PDF pages.
- **Metadata Viewer**: Inspect page count, dimensions, file size, author, title, and encryption status.
- **Share**: Native Android Share Sheet via FileProvider.

### 2. PDF Tools
1. **Images to PDF**: Convert JPG, PNG, and other image formats with A4 or Fit-to-Image page sizing.
2. **Text to PDF**: Turn formatted text into styled PDFs with header, timestamp, and page numbers.
3. **Merge PDF**: Combine multiple PDFs into a single unified document using PDFBox.
4. **Split PDF**: Extract page ranges (e.g., `1-3, 5`) into separate PDF documents.
5. **Compress PDF**: Optimize and reduce PDF file size with adjustable compression quality (30% to 85%).
6. **Rotate PDF**: Rotate selected or all pages by 90°, 180°, or 270°.
7. **Protect PDF**: Add 128-bit standard password encryption.
8. **Unlock PDF**: Remove password protection from encrypted documents with user password.
9. **Add Watermark**: Apply custom text watermarks across pages with opacity and angle controls.
10. **Add Page Numbers**: Insert footer or header page numbers (Bottom-Center, Bottom-Right, Top-Right).
11. **Delete Pages**: Remove selected pages from documents.
12. **Extract Pages**: Save selected pages into a standalone PDF.
13. **PDF Information**: Detailed document properties inspection.

### 3. Multi-Page Document Scanner
- **Live Camera**: CameraX integration with visual document alignment frame.
- **Multi-Page Sessions**: Capture multiple pages in one scanning session.
- **Image Filters**: Original, Enhanced B&W (high contrast), Grayscale, and Magic Color.
- **Rotation & Review**: Rotate, reorder, or delete scanned pages before saving.
- **Export Options**: Export directly as multi-page PDF or high-resolution JPG images.
- **Gallery Import**: Alternative image import from phone gallery.

### 4. On-Device OCR (Text Recognition)
- **100% Offline AI**: Uses Google ML Kit Text Recognition running entirely on-device.
- **Instant Processing**: Extract text from photos or camera captures in seconds.
- **Export & Copy**: One-tap copy to clipboard, save as `.txt`, or convert directly to PDF.

### 5. Document Creator & File Manager
- **Text Editor**: Built-in notepad with live word count and character count.
- **Storage Management**: Browse, search, and sort documents (by date, name, size).
- **File Actions**: Open, rename, share, delete, and favorite documents.
- **System Integration**: Storage Access Framework (SAF) integration and intent filters for opening external PDFs.

### 6. Security & Privacy
- **100% Offline**: All PDF manipulation, scanning, and OCR operations execute on-device.
- **No Cloud Uploads**: Personal documents are never transmitted to external servers.
- **Zero Subscriptions or Logins**: No accounts, credit cards, or tracking.

---

## 🏗️ Building from Source

To compile the application in Android Studio or command-line:

```bash
# Clone the repository and navigate to the project directory
gradle assembleDebug
```

Output APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`
