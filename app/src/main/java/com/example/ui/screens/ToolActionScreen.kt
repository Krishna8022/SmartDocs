package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.data.model.PdfMetadata
import com.example.data.model.PdfToolType
import com.example.domain.PdfEngine
import com.example.domain.StorageManager
import com.example.ui.components.LoadingDialog
import com.example.ui.components.SmartTopBar
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.PdfRed
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolActionScreen(
    tool: PdfToolType,
    preselectedUri: Uri?,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isProcessing by remember { mutableStateOf(false) }
    var processingMsg by remember { mutableStateOf("Processing...") }

    // State for Images to PDF
    val selectedImageUris = remember { mutableStateListOf<Uri>() }
    var outputPdfName by remember { mutableStateOf("SmartDocs_${tool.title.replace(" ", "_")}") }
    var pageSizeOption by remember { mutableStateOf("A4") } // A4 or FIT

    // State for PDF Operations
    var singlePdfUri by remember { mutableStateOf<Uri?>(preselectedUri) }
    val multiplePdfUris = remember { mutableStateListOf<Uri>() }

    // Split / Delete / Extract state
    var pageRangeText by remember { mutableStateOf("1-2") }
    var totalPagesInDoc by remember { mutableIntStateOf(1) }
    val selectedPageIndices = remember { mutableStateListOf<Int>() }

    // Rotate state
    var rotateAngle by remember { mutableIntStateOf(90) }

    // Password state
    var userPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    // Watermark state
    var watermarkText by remember { mutableStateOf("CONFIDENTIAL") }
    var watermarkOpacity by remember { mutableFloatStateOf(0.35f) }
    var watermarkFontSize by remember { mutableFloatStateOf(42f) }

    // Page Number state
    var pageNumPosition by remember { mutableStateOf("BOTTOM_CENTER") }

    // Compress state
    var compressQuality by remember { mutableIntStateOf(65) }

    // Metadata state
    var pdfMetadata by remember { mutableStateOf<PdfMetadata?>(null) }

    BackHandler { onBack() }

    // Multi-image picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedImageUris.addAll(uris)
        }
    }

    // Single PDF picker
    val singlePdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            singlePdfUri = it
            scope.launch {
                totalPagesInDoc = PdfEngine.getPageCount(context, it)
                if (tool == PdfToolType.PDF_INFO) {
                    val metaRes = PdfEngine.readMetadata(context, it)
                    if (metaRes.isSuccess) pdfMetadata = metaRes.getOrNull()
                }
            }
        }
    }

    // Multi-PDF picker (for Merge)
    val multiPdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            multiplePdfUris.addAll(uris)
        }
    }

    // Auto-load metadata if PDF_INFO has preselected URI
    LaunchedEffect(singlePdfUri) {
        singlePdfUri?.let { uri ->
            totalPagesInDoc = PdfEngine.getPageCount(context, uri)
            if (tool == PdfToolType.PDF_INFO) {
                val res = PdfEngine.readMetadata(context, uri)
                if (res.isSuccess) pdfMetadata = res.getOrNull()
            }
        }
    }

    Scaffold(
        topBar = {
            SmartTopBar(
                title = tool.title,
                canNavigateBack = true,
                onNavigateBack = onBack
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                contentPadding = PaddingValues(bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Tool description header
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = tool.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // TOOL 1: IMAGES TO PDF
                if (tool == PdfToolType.IMAGES_TO_PDF) {
                    item {
                        Text("Selected Images (${selectedImageUris.size})", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))

                        if (selectedImageUris.isEmpty()) {
                            OutlinedButton(
                                onClick = { imagePickerLauncher.launch("image/*") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .testTag("btn_select_images"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(30.dp))
                                    Text("Select Images from Gallery")
                                }
                            }
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                itemsIndexed(selectedImageUris) { index, uri ->
                                    Box(
                                        modifier = Modifier
                                            .size(90.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    ) {
                                        Image(
                                            painter = rememberAsyncImagePainter(uri),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        IconButton(
                                            onClick = { selectedImageUris.removeAt(index) },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(24.dp)
                                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                                item {
                                    Box(
                                        modifier = Modifier
                                            .size(90.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable { imagePickerLauncher.launch("image/*") },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Add More", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = outputPdfName,
                            onValueChange = { outputPdfName = it },
                            label = { Text("PDF Document Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_pdf_name")
                        )
                    }

                    item {
                        Text("Page Sizing", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = pageSizeOption == "A4", onClick = { pageSizeOption = "A4" })
                                Text("Standard A4")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = pageSizeOption == "FIT", onClick = { pageSizeOption = "FIT" })
                                Text("Fit to Image Size")
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                if (selectedImageUris.isEmpty()) {
                                    viewModel.showToast("Please add at least 1 image")
                                    return@Button
                                }
                                scope.launch {
                                    isProcessing = true
                                    processingMsg = "Creating PDF..."
                                    val outFile = StorageManager.createUniqueFile(context, outputPdfName.ifBlank { "ImagesDoc" }, "pdf")
                                    val result = PdfEngine.imagesToPdf(context, selectedImageUris, outFile, pageSizeOption)
                                    isProcessing = false
                                    if (result.isSuccess) {
                                        viewModel.registerNewDocument(outFile)
                                        viewModel.showToast("PDF created successfully!")
                                        viewModel.openDocument(Uri.fromFile(outFile), outFile.name)
                                    } else {
                                        viewModel.showToast("Error creating PDF: ${result.exceptionOrNull()?.message}")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_convert_images"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Convert Images to PDF", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // TOOL 2: MERGE PDF
                else if (tool == PdfToolType.MERGE_PDF) {
                    item {
                        Text("Select PDFs to Merge (${multiplePdfUris.size} selected)", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { multiPdfPicker.launch(arrayOf("application/pdf")) },
                            modifier = Modifier.fillMaxWidth().height(56.dp).testTag("btn_select_merge_pdfs"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select PDF Files")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        multiplePdfUris.forEachIndexed { idx, uri ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${idx + 1}. ", fontWeight = FontWeight.Bold)
                                    Text(
                                        text = StorageManager.getFileName(context, uri),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1
                                    )
                                    IconButton(onClick = { multiplePdfUris.removeAt(idx) }) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove")
                                    }
                                }
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = outputPdfName,
                            onValueChange = { outputPdfName = it },
                            label = { Text("Merged File Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Button(
                            onClick = {
                                if (multiplePdfUris.size < 2) {
                                    viewModel.showToast("Select at least 2 PDFs to merge")
                                    return@Button
                                }
                                scope.launch {
                                    isProcessing = true
                                    processingMsg = "Merging PDFs..."
                                    val outFile = StorageManager.createUniqueFile(context, outputPdfName.ifBlank { "Merged_Doc" }, "pdf")
                                    val res = PdfEngine.mergePdfs(context, multiplePdfUris, outFile)
                                    isProcessing = false
                                    if (res.isSuccess) {
                                        viewModel.registerNewDocument(outFile)
                                        viewModel.showToast("PDFs merged successfully!")
                                        viewModel.openDocument(Uri.fromFile(outFile), outFile.name)
                                    } else {
                                        viewModel.showToast("Merge failed: ${res.exceptionOrNull()?.message}")
                                    }
                                }
                            },
                            enabled = multiplePdfUris.size >= 2,
                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_execute_merge"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Merge PDFs Now", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // COMMON PICKER FOR SINGLE-PDF TOOLS
                else {
                    item {
                        Text("Source PDF Document", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))

                        if (singlePdfUri == null) {
                            OutlinedButton(
                                onClick = { singlePdfPicker.launch(arrayOf("application/pdf")) },
                                modifier = Modifier.fillMaxWidth().height(60.dp).testTag("btn_select_source_pdf"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = PdfRed)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Choose PDF Document")
                            }
                        } else {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = PdfRed, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = StorageManager.getFileName(context, singlePdfUri!!),
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "$totalPagesInDoc pages • ${StorageManager.formatFileSize(StorageManager.getFileSize(context, singlePdfUri!!))}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    IconButton(onClick = { singlePdfUri = null }) {
                                        Icon(Icons.Default.Close, contentDescription = "Change")
                                    }
                                }
                            }
                        }
                    }

                    // TOOL: SPLIT PDF
                    if (tool == PdfToolType.SPLIT_PDF) {
                        item {
                            Text("Page Range to Extract", fontWeight = FontWeight.Bold)
                            Text("Example: '1-3', '1, 3, 5', or 'all'", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = pageRangeText,
                                onValueChange = { pageRangeText = it },
                                placeholder = { Text("e.g. 1-2") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("input_split_range")
                            )
                        }

                        item {
                            Button(
                                onClick = {
                                    singlePdfUri?.let { uri ->
                                        scope.launch {
                                            isProcessing = true
                                            processingMsg = "Splitting PDF..."
                                            val dir = StorageManager.getAppDocumentsDir(context)
                                            val res = PdfEngine.splitPdf(context, uri, pageRangeText, dir)
                                            isProcessing = false
                                            if (res.isSuccess && res.getOrNull()?.isNotEmpty() == true) {
                                                val f = res.getOrNull()!!.first()
                                                viewModel.registerNewDocument(f)
                                                viewModel.showToast("PDF split completed!")
                                                viewModel.openDocument(Uri.fromFile(f), f.name)
                                            } else {
                                                viewModel.showToast("Split failed: ${res.exceptionOrNull()?.message}")
                                            }
                                        }
                                    } ?: viewModel.showToast("Please choose a PDF first")
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_execute_split"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Split PDF Pages", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // TOOL: ROTATE PDF
                    else if (tool == PdfToolType.ROTATE_PDF) {
                        item {
                            Text("Rotation Angle", fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf(90 to "90° Clockwise", 180 to "180° Flip", 270 to "270° Left").forEach { (deg, label) ->
                                    FilterChip(
                                        selected = rotateAngle == deg,
                                        onClick = { rotateAngle = deg },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }

                        item {
                            Button(
                                onClick = {
                                    singlePdfUri?.let { uri ->
                                        scope.launch {
                                            isProcessing = true
                                            processingMsg = "Rotating pages..."
                                            val outFile = StorageManager.createUniqueFile(context, "Rotated_Doc", "pdf")
                                            val res = PdfEngine.rotatePdfPages(context, uri, rotateAngle, null, outFile)
                                            isProcessing = false
                                            if (res.isSuccess) {
                                                viewModel.registerNewDocument(outFile)
                                                viewModel.showToast("Pages rotated successfully!")
                                                viewModel.openDocument(Uri.fromFile(outFile), outFile.name)
                                            } else {
                                                viewModel.showToast("Rotate failed: ${res.exceptionOrNull()?.message}")
                                            }
                                        }
                                    } ?: viewModel.showToast("Please select a PDF first")
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_execute_rotate"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Rotate & Save PDF", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // TOOL: PROTECT PDF
                    else if (tool == PdfToolType.PROTECT_PDF) {
                        item {
                            Text("Set Password", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = userPassword,
                                onValueChange = { userPassword = it },
                                label = { Text("Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("input_protect_password")
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it },
                                label = { Text("Confirm Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        item {
                            Button(
                                onClick = {
                                    if (userPassword.isBlank()) {
                                        viewModel.showToast("Please enter a password")
                                        return@Button
                                    }
                                    if (userPassword != confirmPassword) {
                                        viewModel.showToast("Passwords do not match")
                                        return@Button
                                    }
                                    singlePdfUri?.let { uri ->
                                        scope.launch {
                                            isProcessing = true
                                            processingMsg = "Encrypting PDF..."
                                            val outFile = StorageManager.createUniqueFile(context, "Protected_Doc", "pdf")
                                            val res = PdfEngine.protectPdf(context, uri, userPassword, userPassword, outFile)
                                            isProcessing = false
                                            if (res.isSuccess) {
                                                viewModel.registerNewDocument(outFile)
                                                viewModel.showToast("PDF encrypted successfully!")
                                                viewModel.openDocument(Uri.fromFile(outFile), outFile.name)
                                            } else {
                                                viewModel.showToast("Encryption failed: ${res.exceptionOrNull()?.message}")
                                            }
                                        }
                                    } ?: viewModel.showToast("Please select a PDF first")
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_execute_protect"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Protect PDF with Password", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // TOOL: UNLOCK PDF
                    else if (tool == PdfToolType.UNLOCK_PDF) {
                        item {
                            Text("Enter Document Password", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = userPassword,
                                onValueChange = { userPassword = it },
                                label = { Text("Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("input_unlock_password")
                            )
                        }

                        item {
                            Button(
                                onClick = {
                                    singlePdfUri?.let { uri ->
                                        scope.launch {
                                            isProcessing = true
                                            processingMsg = "Unlocking PDF..."
                                            val outFile = StorageManager.createUniqueFile(context, "Unlocked_Doc", "pdf")
                                            val res = PdfEngine.unlockPdf(context, uri, userPassword, outFile)
                                            isProcessing = false
                                            if (res.isSuccess) {
                                                viewModel.registerNewDocument(outFile)
                                                viewModel.showToast("PDF unlocked successfully!")
                                                viewModel.openDocument(Uri.fromFile(outFile), outFile.name)
                                            } else {
                                                viewModel.showToast("Failed to unlock: Check password")
                                            }
                                        }
                                    } ?: viewModel.showToast("Please select a PDF first")
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_execute_unlock"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.LockOpen, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Unlock & Remove Password", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // TOOL: WATERMARK
                    else if (tool == PdfToolType.ADD_WATERMARK) {
                        item {
                            Text("Watermark Text", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = watermarkText,
                                onValueChange = { watermarkText = it },
                                placeholder = { Text("e.g. CONFIDENTIAL, DRAFT, APPROVED") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("input_watermark_text")
                            )
                        }

                        item {
                            Text("Opacity (${(watermarkOpacity * 100).toInt()}%)", fontWeight = FontWeight.SemiBold)
                            Slider(
                                value = watermarkOpacity,
                                onValueChange = { watermarkOpacity = it },
                                valueRange = 0.1f..0.9f
                            )
                        }

                        item {
                            Button(
                                onClick = {
                                    singlePdfUri?.let { uri ->
                                        scope.launch {
                                            isProcessing = true
                                            processingMsg = "Applying watermark..."
                                            val outFile = StorageManager.createUniqueFile(context, "Watermarked_Doc", "pdf")
                                            val res = PdfEngine.addWatermark(context, uri, watermarkText, watermarkFontSize, watermarkOpacity, outFile)
                                            isProcessing = false
                                            if (res.isSuccess) {
                                                viewModel.registerNewDocument(outFile)
                                                viewModel.showToast("Watermark applied successfully!")
                                                viewModel.openDocument(Uri.fromFile(outFile), outFile.name)
                                            } else {
                                                viewModel.showToast("Watermark failed: ${res.exceptionOrNull()?.message}")
                                            }
                                        }
                                    } ?: viewModel.showToast("Please select a PDF first")
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_execute_watermark"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Add Watermark", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // TOOL: PAGE NUMBERS
                    else if (tool == PdfToolType.ADD_PAGE_NUMBERS) {
                        item {
                            Text("Page Number Placement", fontWeight = FontWeight.Bold)
                            Column {
                                listOf(
                                    "BOTTOM_CENTER" to "Bottom Center (Page X of Y)",
                                    "BOTTOM_RIGHT" to "Bottom Right (Page X of Y)",
                                    "TOP_RIGHT" to "Top Right Header (Page X of Y)"
                                ).forEach { (pos, label) ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable { pageNumPosition = pos }.padding(vertical = 4.dp)
                                    ) {
                                        RadioButton(selected = pageNumPosition == pos, onClick = { pageNumPosition = pos })
                                        Text(label)
                                    }
                                }
                            }
                        }

                        item {
                            Button(
                                onClick = {
                                    singlePdfUri?.let { uri ->
                                        scope.launch {
                                            isProcessing = true
                                            processingMsg = "Adding page numbers..."
                                            val outFile = StorageManager.createUniqueFile(context, "Numbered_Doc", "pdf")
                                            val res = PdfEngine.addPageNumbers(context, uri, pageNumPosition, outFile)
                                            isProcessing = false
                                            if (res.isSuccess) {
                                                viewModel.registerNewDocument(outFile)
                                                viewModel.showToast("Page numbers inserted!")
                                                viewModel.openDocument(Uri.fromFile(outFile), outFile.name)
                                            } else {
                                                viewModel.showToast("Failed: ${res.exceptionOrNull()?.message}")
                                            }
                                        }
                                    } ?: viewModel.showToast("Please select a PDF first")
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_execute_page_numbers"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Insert Page Numbers", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // TOOL: COMPRESS PDF
                    else if (tool == PdfToolType.COMPRESS_PDF) {
                        item {
                            Text("Compression Level", fontWeight = FontWeight.Bold)
                            Text("Current Quality: $compressQuality%", style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = compressQuality.toFloat(),
                                onValueChange = { compressQuality = it.toInt() },
                                valueRange = 30f..85f,
                                steps = 10
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Smaller Size (30%)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("High Quality (85%)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        item {
                            Button(
                                onClick = {
                                    singlePdfUri?.let { uri ->
                                        scope.launch {
                                            isProcessing = true
                                            processingMsg = "Compressing PDF..."
                                            val outFile = StorageManager.createUniqueFile(context, "Compressed_Doc", "pdf")
                                            val res = PdfEngine.compressPdf(context, uri, compressQuality, outFile)
                                            isProcessing = false
                                            if (res.isSuccess) {
                                                val origSize = StorageManager.getFileSize(context, uri)
                                                val newSize = outFile.length()
                                                val savedPct = if (origSize > 0) ((origSize - newSize) * 100 / origSize) else 0
                                                viewModel.registerNewDocument(outFile)
                                                viewModel.showToast("Compressed: ${StorageManager.formatFileSize(newSize)} (Saved $savedPct%)")
                                                viewModel.openDocument(Uri.fromFile(outFile), outFile.name)
                                            } else {
                                                viewModel.showToast("Compression failed: ${res.exceptionOrNull()?.message}")
                                            }
                                        }
                                    } ?: viewModel.showToast("Please select a PDF first")
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_execute_compress"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Compress PDF", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // TOOL: PDF INFO
                    else if (tool == PdfToolType.PDF_INFO) {
                        item {
                            pdfMetadata?.let { meta ->
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.fillMaxWidth(),
                                    elevation = CardDefaults.cardElevation(2.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text("PDF Information & Metadata", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                        Text("File Name: ${meta.fileName}")
                                        Text("Page Count: ${meta.pageCount}")
                                        Text("File Size: ${meta.fileSizeFormatted}")
                                        Text("Page Dimensions: ${meta.dimensionsFormatted}")
                                        Text("PDF Version: ${meta.version}")
                                        Text("Protected / Encrypted: ${if (meta.isEncrypted) "Yes" else "No"}")
                                        if (!meta.title.isNullOrBlank()) Text("Title: ${meta.title}")
                                        if (!meta.author.isNullOrBlank()) Text("Author: ${meta.author}")
                                        if (!meta.creator.isNullOrBlank()) Text("Creator: ${meta.creator}")
                                        if (!meta.producer.isNullOrBlank()) Text("Producer: ${meta.producer}")
                                        if (!meta.creationDate.isNullOrBlank()) Text("Creation Date: ${meta.creationDate}")
                                    }
                                }
                            } ?: run {
                                if (singlePdfUri != null) {
                                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                        }
                    }

                    // TOOL: DELETE PAGES
                    else if (tool == PdfToolType.DELETE_PAGES) {
                        item {
                            Text("Pages to Delete", fontWeight = FontWeight.Bold)
                            Text("Enter comma-separated page numbers (e.g. 1, 3)", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = pageRangeText,
                                onValueChange = { pageRangeText = it },
                                placeholder = { Text("e.g. 1, 2") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("input_delete_pages")
                            )
                        }

                        item {
                            Button(
                                onClick = {
                                    singlePdfUri?.let { uri ->
                                        scope.launch {
                                            isProcessing = true
                                            processingMsg = "Removing pages..."
                                            val toDelete = pageRangeText.split(",", " ").mapNotNull { it.trim().toIntOrNull()?.let { p -> p - 1 } }.toSet()
                                            val outFile = StorageManager.createUniqueFile(context, "Cleaned_Doc", "pdf")
                                            val res = PdfEngine.deletePdfPages(context, uri, toDelete, outFile)
                                            isProcessing = false
                                            if (res.isSuccess) {
                                                viewModel.registerNewDocument(outFile)
                                                viewModel.showToast("Pages deleted successfully!")
                                                viewModel.openDocument(Uri.fromFile(outFile), outFile.name)
                                            } else {
                                                viewModel.showToast("Failed: ${res.exceptionOrNull()?.message}")
                                            }
                                        }
                                    } ?: viewModel.showToast("Please select a PDF first")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_execute_delete_pages"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Delete Pages & Save", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    LoadingDialog(isLoading = isProcessing, message = processingMsg)
}
