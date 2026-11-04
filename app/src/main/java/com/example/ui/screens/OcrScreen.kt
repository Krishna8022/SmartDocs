package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.domain.OcrEngine
import com.example.domain.PdfEngine
import com.example.domain.StorageManager
import com.example.ui.components.LoadingDialog
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreen(
    initialImageUri: Uri? = null,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var selectedImageUri by remember { mutableStateOf<Uri?>(initialImageUri) }
    var recognizedText by remember { mutableStateOf("") }
    var isRunningOcr by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    BackHandler { onBack() }

    fun runOcr(uri: Uri) {
        scope.launch {
            isRunningOcr = true
            errorMessage = null
            val res = OcrEngine.recognizeTextFromUri(context, uri)
            isRunningOcr = false
            if (res.isSuccess) {
                recognizedText = res.getOrNull() ?: ""
            } else {
                errorMessage = res.exceptionOrNull()?.message ?: "Failed to recognize text"
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedImageUri = it
            recognizedText = ""
            errorMessage = null
            runOcr(it)
        }
    }

    LaunchedEffect(initialImageUri) {
        initialImageUri?.let { runOcr(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OCR Text Recognition", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("ocr_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Image Selector / Preview Box
            if (selectedImageUri == null) {
                OutlinedButton(
                    onClick = { imagePicker.launch("image/*") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .testTag("btn_select_ocr_image"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Select Document Image to Extract Text", fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(selectedImageUri),
                            contentDescription = null,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Image selected", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (isRunningOcr) "Analyzing text with on-device OCR..." else "Ready",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        OutlinedButton(onClick = { imagePicker.launch("image/*") }) {
                            Text("Change")
                        }
                    }
                }
            }

            // Results Area
            if (isRunningOcr) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Extracting text with on-device AI...")
                    }
                }
            } else if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Recognition Issue", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(errorMessage ?: "", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            } else {
                OutlinedTextField(
                    value = recognizedText,
                    onValueChange = { recognizedText = it },
                    label = { Text("Extracted Text") },
                    placeholder = { Text("Extracted text will appear here...") },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("ocr_text_output")
                )
            }

            // Action row
            if (recognizedText.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(recognizedText))
                            viewModel.showToast("Text copied to clipboard")
                        },
                        modifier = Modifier.weight(1f).testTag("btn_copy_ocr")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy")
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val txtFile = StorageManager.createUniqueFile(context, "OCR_Extracted", "txt")
                                txtFile.writeText(recognizedText)
                                viewModel.registerNewDocument(txtFile)
                                viewModel.showToast("Saved as TXT: ${txtFile.name}")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("TXT")
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                val pdfFile = StorageManager.createUniqueFile(context, "OCR_Document", "pdf")
                                val res = PdfEngine.textToPdf(context, "OCR Extracted Text", recognizedText, pdfFile)
                                if (res.isSuccess) {
                                    viewModel.registerNewDocument(pdfFile)
                                    viewModel.showToast("Exported to PDF successfully!")
                                    viewModel.openDocument(Uri.fromFile(pdfFile), pdfFile.name)
                                } else {
                                    viewModel.showToast("Export failed")
                                }
                            }
                        },
                        modifier = Modifier.weight(1.2f).testTag("btn_export_ocr_pdf")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export PDF")
                    }
                }
            }
        }
    }
}
