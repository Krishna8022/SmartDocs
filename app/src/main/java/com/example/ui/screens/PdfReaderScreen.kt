package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PdfMetadata
import com.example.domain.PdfEngine
import com.example.domain.StorageManager
import com.example.ui.components.SmartTopBar
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    uri: Uri,
    title: String,
    startPage: Int,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pageCount by remember { mutableIntStateOf(1) }
    var currentPage by remember { mutableIntStateOf((startPage - 1).coerceAtLeast(0)) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingPage by remember { mutableStateOf(true) }
    var thumbnails by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var showThumbnails by remember { mutableStateOf(false) }

    // Zoom & pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Dialogs
    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpPageText by remember { mutableStateOf("") }
    var showInfoDialog by remember { mutableStateOf(false) }
    var metadata by remember { mutableStateOf<PdfMetadata?>(null) }
    var showTextExtractDialog by remember { mutableStateOf(false) }
    var extractedText by remember { mutableStateOf("") }

    BackHandler {
        onBack()
    }

    // Load initial page count and thumbnails
    LaunchedEffect(uri) {
        val count = PdfEngine.getPageCount(context, uri)
        pageCount = count
        currentPage = (startPage - 1).coerceIn(0, (count - 1).coerceAtLeast(0))
        thumbnails = PdfEngine.renderThumbnails(context, uri, maxPages = 25)
    }

    // Load page bitmap when currentPage changes
    LaunchedEffect(uri, currentPage) {
        isLoadingPage = true
        // Reset pan on page change
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        val bmp = PdfEngine.renderPageToBitmap(context, uri, currentPage)
        currentBitmap = bmp
        isLoadingPage = false
        // Update reading progress
        viewModel.updateReadingProgress(uri, currentPage + 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Page ${currentPage + 1} of $pageCount",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("reader_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showThumbnails = !showThumbnails }) {
                        Icon(
                            Icons.Default.ViewCarousel,
                            contentDescription = "Thumbnails",
                            tint = if (showThumbnails) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = {
                        scope.launch {
                            val res = PdfEngine.extractTextFromPdf(context, uri)
                            if (res.isSuccess) {
                                extractedText = res.getOrNull() ?: ""
                                showTextExtractDialog = true
                            } else {
                                viewModel.showToast("Could not extract text: ${res.exceptionOrNull()?.message}")
                            }
                        }
                    }) {
                        Icon(Icons.Default.TextFields, contentDescription = "Extract Text")
                    }
                    IconButton(onClick = {
                        scope.launch {
                            val res = PdfEngine.readMetadata(context, uri)
                            if (res.isSuccess) {
                                metadata = res.getOrNull()
                                showInfoDialog = true
                            } else {
                                viewModel.showToast("Failed to read metadata")
                            }
                        }
                    }) {
                        Icon(Icons.Default.Info, contentDescription = "Info")
                    }
                    IconButton(onClick = { StorageManager.shareUri(context, uri, "application/pdf") }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    // Thumbnail strip if toggled
                    AnimatedVisibility(visible = showThumbnails && thumbnails.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(vertical = 8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(thumbnails) { idx, thumb ->
                                val isSelected = idx == currentPage
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { currentPage = idx }
                                        .padding(2.dp)
                                ) {
                                    Image(
                                        bitmap = thumb.asImageBitmap(),
                                        contentDescription = "Page ${idx + 1}",
                                        modifier = Modifier
                                            .height(70.dp)
                                            .width(50.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                                shape = RoundedCornerShape(6.dp)
                                            ),
                                        contentScale = ContentScale.Crop
                                    )
                                    Text(
                                        text = "${idx + 1}",
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Navigation & Zoom controls bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous page
                        IconButton(
                            onClick = { if (currentPage > 0) currentPage-- },
                            enabled = currentPage > 0,
                            modifier = Modifier.testTag("reader_prev")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Page")
                        }

                        // Page Jump Button
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.clickable {
                                jumpPageText = "${currentPage + 1}"
                                showJumpDialog = true
                            }
                        ) {
                            Text(
                                text = "Page ${currentPage + 1} / $pageCount",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }

                        // Next page
                        IconButton(
                            onClick = { if (currentPage < pageCount - 1) currentPage++ },
                            enabled = currentPage < pageCount - 1,
                            modifier = Modifier.testTag("reader_next")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Page")
                        }

                        // Zoom controls
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                scale = (scale - 0.25f).coerceAtLeast(0.75f)
                            }) {
                                Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out")
                            }
                            IconButton(onClick = {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            }) {
                                Icon(Icons.Default.ZoomOutMap, contentDescription = "Reset Zoom")
                            }
                            IconButton(onClick = {
                                scale = (scale + 0.25f).coerceAtMost(4f)
                            }) {
                                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In")
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFE2E8F0))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.75f, 4f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (isLoadingPage) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            } else if (currentBitmap != null) {
                Image(
                    bitmap = currentBitmap!!.asImageBitmap(),
                    contentDescription = "PDF Page ${currentPage + 1}",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        ),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text("Failed to render PDF page", color = Color.Gray)
            }
        }
    }

    // Jump to page dialog
    if (showJumpDialog) {
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("Jump to Page") },
            text = {
                Column {
                    Text("Enter page number (1 to $pageCount):", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = jumpPageText,
                        onValueChange = { jumpPageText = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("jump_page_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = jumpPageText.toIntOrNull()
                        if (target != null && target in 1..pageCount) {
                            currentPage = target - 1
                            showJumpDialog = false
                        } else {
                            viewModel.showToast("Invalid page number")
                        }
                    },
                    modifier = Modifier.testTag("jump_page_confirm")
                ) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Info Dialog
    if (showInfoDialog && metadata != null) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("Document Information") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("File: ${metadata?.fileName}", fontWeight = FontWeight.Bold)
                    Text("Pages: ${metadata?.pageCount}")
                    Text("File Size: ${metadata?.fileSizeFormatted}")
                    Text("Dimensions: ${metadata?.dimensionsFormatted}")
                    if (!metadata?.title.isNullOrBlank()) Text("Title: ${metadata?.title}")
                    if (!metadata?.author.isNullOrBlank()) Text("Author: ${metadata?.author}")
                    if (!metadata?.creationDate.isNullOrBlank()) Text("Created: ${metadata?.creationDate}")
                    Text("Encrypted: ${if (metadata?.isEncrypted == true) "Yes (Protected)" else "No"}")
                }
            },
            confirmButton = {
                Button(onClick = { showInfoDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Extracted Text Dialog
    if (showTextExtractDialog) {
        AlertDialog(
            onDismissRequest = { showTextExtractDialog = false },
            title = { Text("Extracted Document Text") },
            text = {
                Column(modifier = Modifier.height(300.dp)) {
                    OutlinedTextField(
                        value = extractedText,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("PDF Text", extractedText)
                    clipboard.setPrimaryClip(clip)
                    viewModel.showToast("Copied to clipboard")
                    showTextExtractDialog = false
                }) {
                    Text("Copy Text")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTextExtractDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
