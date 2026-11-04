package com.example.domain

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.data.model.DocItem
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object StorageManager {

    fun getAppDocumentsDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "SmartDocs")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getScansDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "SmartDocs_Scans")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "smartdocs_temp")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun createUniqueFile(context: Context, prefix: String, extension: String): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "${prefix}_${timeStamp}.${extension.removePrefix(".")}"
        return File(getAppDocumentsDir(context), fileName)
    }

    fun getFileName(context: Context, uri: Uri): String {
        var name: String? = null
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = it.getString(nameIndex)
                    }
                }
            }
        }
        if (name.isNullOrBlank()) {
            name = uri.lastPathSegment ?: "document_${System.currentTimeMillis()}.pdf"
        }
        return name!!
    }

    fun getFileSize(context: Context, uri: Uri): Long {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        return it.getLong(sizeIndex)
                    }
                }
            }
        } else if (uri.scheme == ContentResolver.SCHEME_FILE) {
            uri.path?.let {
                val f = File(it)
                if (f.exists()) return f.length()
            }
        }
        return 0L
    }

    fun copyUriToTempFile(context: Context, uri: Uri, suffix: String = ".pdf"): File {
        val tempFile = File(getCacheDir(context), "input_${UUID.randomUUID()}$suffix")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        return tempFile
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun shareFile(context: Context, file: File, mimeType: String = "application/pdf") {
        try {
            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share Document via")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun shareUri(context: Context, uri: Uri, mimeType: String = "application/pdf") {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share Document via")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun listAppFiles(context: Context): List<DocItem> {
        val items = mutableListOf<DocItem>()
        val dir = getAppDocumentsDir(context)
        val files = dir.listFiles() ?: emptyArray()

        files.filter { it.isFile && (it.name.endsWith(".pdf", true) || it.name.endsWith(".txt", true)) }
            .sortedByDescending { it.lastModified() }
            .forEach { file ->
                val uri = Uri.fromFile(file)
                val isPdf = file.name.endsWith(".pdf", true)
                items.add(
                    DocItem(
                        id = file.absolutePath,
                        name = file.name,
                        uri = uri,
                        path = file.absolutePath,
                        mimeType = if (isPdf) "application/pdf" else "text/plain",
                        sizeBytes = file.length(),
                        pageCount = 1,
                        lastModified = file.lastModified()
                    )
                )
            }
        return items
    }

    fun renameFile(oldFile: File, newName: String): File? {
        val targetName = if (!newName.contains(".")) {
            val ext = oldFile.extension
            if (ext.isNotEmpty()) "$newName.$ext" else newName
        } else {
            newName
        }
        val targetFile = File(oldFile.parentFile, targetName)
        return if (oldFile.renameTo(targetFile)) targetFile else null
    }

    fun deleteFile(file: File): Boolean {
        return try {
            file.delete()
        } catch (e: Exception) {
            false
        }
    }
}
