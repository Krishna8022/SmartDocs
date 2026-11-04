package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.PdfEngine
import com.example.domain.StorageManager
import com.example.ui.components.LoadingDialog
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(
    existingFile: File? = null,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var docTitle by remember { mutableStateOf(existingFile?.nameWithoutExtension ?: "Untitled Document") }
    var docContent by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    LaunchedEffect(existingFile) {
        existingFile?.let { file ->
            if (file.exists()) {
                docContent = file.readText()
            }
        }
    }

    val wordCount = remember(docContent) {
        if (docContent.isBlank()) 0 else docContent.trim().split("\\s+".toRegex()).size
    }
    val charCount = docContent.length

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Document Editor", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("editor_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                val txtFile = StorageManager.createUniqueFile(context, docTitle.ifBlank { "Document" }, "txt")
                                txtFile.writeText(docContent)
                                viewModel.registerNewDocument(txtFile)
                                isSaving = false
                                viewModel.showToast("Saved as \"${txtFile.name}\"")
                            }
                        },
                        modifier = Modifier.testTag("btn_save_txt")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save TXT")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Title input
            OutlinedTextField(
                value = docTitle,
                onValueChange = { docTitle = it },
                label = { Text("Document Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("editor_title_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Word and Char Counters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$wordCount words • $charCount characters",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Body Content Editor
            OutlinedTextField(
                value = docContent,
                onValueChange = { docContent = it },
                placeholder = { Text("Start typing your document content here...") },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("editor_body_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons: Save TXT & Export PDF
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isSaving = true
                            val txtFile = StorageManager.createUniqueFile(context, docTitle.ifBlank { "Document" }, "txt")
                            txtFile.writeText(docContent)
                            viewModel.registerNewDocument(txtFile)
                            isSaving = false
                            viewModel.showToast("Saved as TXT")
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save TXT")
                }

                Button(
                    onClick = {
                        if (docContent.isBlank()) {
                            viewModel.showToast("Cannot export empty document to PDF")
                            return@Button
                        }
                        scope.launch {
                            isSaving = true
                            val pdfFile = StorageManager.createUniqueFile(context, docTitle.ifBlank { "Document" }, "pdf")
                            val res = PdfEngine.textToPdf(context, docTitle, docContent, pdfFile)
                            isSaving = false
                            if (res.isSuccess) {
                                viewModel.registerNewDocument(pdfFile)
                                viewModel.showToast("Exported to PDF successfully!")
                                viewModel.openDocument(Uri.fromFile(pdfFile), pdfFile.name)
                            } else {
                                viewModel.showToast("Export failed: ${res.exceptionOrNull()?.message}")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("btn_export_pdf")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export PDF")
                }
            }
        }
    }

    LoadingDialog(isLoading = isSaving, message = "Processing document...")
}
