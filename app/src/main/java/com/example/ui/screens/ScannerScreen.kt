package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import com.example.data.model.ScanFilter
import com.example.data.model.ScanPage
import com.example.domain.ScannerEngine
import com.example.domain.StorageManager
import com.example.ui.components.LoadingDialog
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            viewModel.showToast("Camera permission is required for live scanning. You can still import from gallery.")
        }
    }

    val scannedPages = remember { mutableStateListOf<ScanPage>() }
    var selectedPageIndex by remember { mutableIntStateOf(0) }
    var showPreviewSheet by remember { mutableStateOf(false) }

    var isSaving by remember { mutableStateOf(false) }
    var docTitle by remember { mutableStateOf("SmartDocs_Scan") }

    val imageCapture = remember { ImageCapture.Builder().build() }

    BackHandler { onBack() }

    // Multi-image gallery picker fallback
    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { uri ->
            scannedPages.add(ScanPage(uri = uri))
        }
        if (scannedPages.isNotEmpty()) {
            selectedPageIndex = scannedPages.size - 1
            showPreviewSheet = true
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Document Scanner", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("scanner_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { galleryPicker.launch("image/*") }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Import Gallery")
                    }
                    if (scannedPages.isNotEmpty()) {
                        Button(
                            onClick = { showPreviewSheet = true },
                            modifier = Modifier.padding(end = 8.dp).testTag("btn_review_scans")
                        ) {
                            Text("Done (${scannedPages.size})")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black)
        ) {
            if (hasCameraPermission) {
                // Live CameraX Preview
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    ctx as androidx.lifecycle.LifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageCapture
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Document frame guide overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp, vertical = 70.dp)
                        .border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                )

                // Bottom Camera Controls
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(bottom = 24.dp, top = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Scanned thumbnails strip
                    if (scannedPages.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            itemsIndexed(scannedPages) { index, page ->
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                                        .clickable {
                                            selectedPageIndex = index
                                            showPreviewSheet = true
                                        }
                                ) {
                                    Image(
                                        painter = rememberAsyncImagePainter(page.uri),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }

                    // Shutter Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { galleryPicker.launch("image/*") },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery", tint = Color.White)
                        }

                        // Shutter
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable {
                                    val photoFile = File(
                                        StorageManager.getCacheDir(context),
                                        "scan_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg"
                                    )
                                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                                    imageCapture.takePicture(
                                        outputOptions,
                                        ContextCompat.getMainExecutor(context),
                                        object : ImageCapture.OnImageSavedCallback {
                                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                                scannedPages.add(ScanPage(uri = Uri.fromFile(photoFile)))
                                                viewModel.showToast("Captured page ${scannedPages.size}")
                                            }

                                            override fun onError(exception: ImageCaptureException) {
                                                viewModel.showToast("Capture failed: ${exception.message}")
                                            }
                                        }
                                    )
                                }
                                .testTag("btn_capture_scan"),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Color.Transparent)
                                    .border(3.dp, Color.Black, CircleShape)
                            )
                        }

                        // Counter or placeholder
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${scannedPages.size}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                // Permission Denied View
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Camera Permission Needed",
                        style = MaterialTheme.typography.titleLarge.copy(color = Color.White, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Grant camera permission to scan physical documents with your device camera.",
                        color = Color.LightGray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.testTag("btn_request_camera")
                    ) {
                        Text("Grant Permission")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = { galleryPicker.launch("image/*") }) {
                        Text("Or Import Images from Gallery", color = Color.White)
                    }
                }
            }

            // Preview and Filter Sheet Dialog
            if (showPreviewSheet && scannedPages.isNotEmpty()) {
                val currentPage = scannedPages.getOrNull(selectedPageIndex) ?: scannedPages.first()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Page ${selectedPageIndex + 1} of ${scannedPages.size}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Row {
                                IconButton(onClick = {
                                    // Rotate page
                                    val curr = scannedPages[selectedPageIndex]
                                    scannedPages[selectedPageIndex] = curr.copy(rotationDegrees = (curr.rotationDegrees + 90) % 360)
                                }) {
                                    Icon(Icons.Default.RotateRight, contentDescription = "Rotate")
                                }
                                IconButton(onClick = {
                                    scannedPages.removeAt(selectedPageIndex)
                                    if (scannedPages.isEmpty()) {
                                        showPreviewSheet = false
                                    } else {
                                        selectedPageIndex = selectedPageIndex.coerceAtMost(scannedPages.size - 1)
                                    }
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Delete Page")
                                }
                            }
                        }

                        // Preview Image
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFE2E8F0)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(currentPage.uri),
                                contentDescription = "Page Preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Filter selection
                        Text("Enhancement Filter", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            ScanFilter.values().forEach { filter ->
                                FilterChip(
                                    selected = currentPage.filter == filter,
                                    onClick = {
                                        scannedPages[selectedPageIndex] = currentPage.copy(filter = filter)
                                    },
                                    label = { Text(filter.label) }
                                )
                            }
                        }

                        // Document Name
                        OutlinedTextField(
                            value = docTitle,
                            onValueChange = { docTitle = it },
                            label = { Text("Document Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("scan_doc_name")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showPreviewSheet = false },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("+ Add Page")
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        isSaving = true
                                        val outFile = StorageManager.createUniqueFile(context, docTitle.ifBlank { "Scan" }, "pdf")
                                        val res = ScannerEngine.saveScanSessionAsPdf(context, scannedPages, outFile)
                                        isSaving = false
                                        if (res.isSuccess) {
                                            viewModel.registerNewDocument(outFile)
                                            viewModel.showToast("Scan saved as PDF successfully!")
                                            viewModel.openDocument(Uri.fromFile(outFile), outFile.name)
                                        } else {
                                            viewModel.showToast("Failed to save scan: ${res.exceptionOrNull()?.message}")
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f).testTag("btn_save_scan_pdf")
                            ) {
                                Text("Save as PDF")
                            }
                        }
                    }
                }
            }
        }
    }

    LoadingDialog(isLoading = isSaving, message = "Generating scan PDF...")
}
