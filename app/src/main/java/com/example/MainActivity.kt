package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.PdfToolType
import com.example.ui.components.LoadingDialog
import com.example.ui.components.SmartBottomBar
import com.example.ui.screens.FileManagerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OcrScreen
import com.example.ui.screens.PdfReaderScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TextEditorScreen
import com.example.ui.screens.ToolActionScreen
import com.example.ui.screens.ToolsHubScreen
import com.example.ui.theme.SmartDocsTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.Screen
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private lateinit var mainViewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            mainViewModel = viewModel()
            val uiState by mainViewModel.uiState.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val isDark = uiState.isDarkTheme ?: systemDark

            SmartDocsTheme(darkTheme = isDark) {
                MainAppContent(viewModel = mainViewModel)
            }
        }

        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val data: Uri? = intent.data
        if (action == Intent.ACTION_VIEW && data != null) {
            if (::mainViewModel.isInitialized) {
                mainViewModel.openDocument(data)
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.toastEvent.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Determine bottom nav tab index
    val currentTab = when (uiState.currentScreen) {
        is Screen.Home -> 0
        is Screen.ToolsHub -> 1
        is Screen.Files -> 2
        is Screen.Settings -> 3
        else -> -1
    }

    val isTopLevelScreen = currentTab != -1

    BackHandler(enabled = !isTopLevelScreen) {
        viewModel.navigateBack()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (isTopLevelScreen) {
                SmartBottomBar(
                    currentTab = currentTab,
                    onTabSelected = { tab ->
                        when (tab) {
                            0 -> viewModel.navigateTo(Screen.Home)
                            1 -> viewModel.navigateTo(Screen.ToolsHub)
                            2 -> viewModel.navigateTo(Screen.Files)
                            3 -> viewModel.navigateTo(Screen.Settings)
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val screen = uiState.currentScreen) {
                is Screen.Home -> {
                    HomeScreen(
                        viewModel = viewModel,
                        onOpenTool = { tool ->
                            when (tool) {
                                PdfToolType.SCANNER -> viewModel.navigateTo(Screen.Scanner)
                                PdfToolType.OCR -> viewModel.navigateTo(Screen.Ocr())
                                PdfToolType.TEXT_TO_PDF -> viewModel.navigateTo(Screen.TextEditor())
                                else -> viewModel.navigateTo(Screen.ToolAction(tool))
                            }
                        },
                        onOpenFilesTab = { viewModel.navigateTo(Screen.Files) }
                    )
                }

                is Screen.ToolsHub -> {
                    ToolsHubScreen(
                        viewModel = viewModel,
                        onSelectTool = { tool ->
                            when (tool) {
                                PdfToolType.SCANNER -> viewModel.navigateTo(Screen.Scanner)
                                PdfToolType.OCR -> viewModel.navigateTo(Screen.Ocr())
                                PdfToolType.TEXT_TO_PDF -> viewModel.navigateTo(Screen.TextEditor())
                                else -> viewModel.navigateTo(Screen.ToolAction(tool))
                            }
                        }
                    )
                }

                is Screen.Files -> {
                    FileManagerScreen(
                        viewModel = viewModel,
                        onOpenDoc = { doc -> viewModel.openDocument(doc.uri, doc.name) }
                    )
                }

                is Screen.Settings -> {
                    SettingsScreen(viewModel = viewModel)
                }

                is Screen.Reader -> {
                    PdfReaderScreen(
                        uri = screen.uri,
                        title = screen.title,
                        startPage = screen.startPage,
                        viewModel = viewModel,
                        onBack = { viewModel.navigateBack() }
                    )
                }

                is Screen.Scanner -> {
                    ScannerScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.navigateBack() }
                    )
                }

                is Screen.TextEditor -> {
                    TextEditorScreen(
                        existingFile = screen.existingFile,
                        viewModel = viewModel,
                        onBack = { viewModel.navigateBack() }
                    )
                }

                is Screen.Ocr -> {
                    OcrScreen(
                        initialImageUri = screen.initialImageUri,
                        viewModel = viewModel,
                        onBack = { viewModel.navigateBack() }
                    )
                }

                is Screen.ToolAction -> {
                    ToolActionScreen(
                        tool = screen.tool,
                        preselectedUri = screen.preselectedUri,
                        viewModel = viewModel,
                        onBack = { viewModel.navigateBack() }
                    )
                }
            }

            LoadingDialog(
                isLoading = uiState.isLoading,
                message = uiState.loadingMessage.ifBlank { "Processing document..." }
            )
        }
    }
}
