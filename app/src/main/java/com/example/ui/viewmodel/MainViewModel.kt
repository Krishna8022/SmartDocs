package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.DocEntity
import com.example.data.model.DocFilterType
import com.example.data.model.DocItem
import com.example.data.model.PdfToolType
import com.example.data.model.SortOrder
import com.example.domain.PdfEngine
import com.example.domain.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class Screen {
    object Home : Screen()
    object ToolsHub : Screen()
    object Files : Screen()
    object Settings : Screen()
    data class Reader(val uri: Uri, val title: String, val startPage: Int = 1) : Screen()
    object Scanner : Screen()
    data class TextEditor(val existingFile: File? = null) : Screen()
    data class Ocr(val initialImageUri: Uri? = null) : Screen()
    data class ToolAction(val tool: PdfToolType, val preselectedUri: Uri? = null) : Screen()
}

data class UiState(
    val currentScreen: Screen = Screen.Home,
    val screenHistory: List<Screen> = listOf(Screen.Home),
    val recentDocs: List<DocItem> = emptyList(),
    val allFiles: List<DocItem> = emptyList(),
    val filteredFiles: List<DocItem> = emptyList(),
    val searchQuery: String = "",
    val activeFilter: DocFilterType = DocFilterType.ALL,
    val activeSort: SortOrder = SortOrder.DATE_DESC,
    val isLoading: Boolean = false,
    val loadingMessage: String = "",
    val isDarkTheme: Boolean? = null // null = system default
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val docDao = db.docDao()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    init {
        PdfEngine.init(application)
        observeRecentDocs()
        refreshAllFiles()
    }

    private fun observeRecentDocs() {
        viewModelScope.launch {
            docDao.getRecentDocs().collectLatest { entities ->
                val items = entities.map { it.toDocItem() }
                _uiState.update { it.copy(recentDocs = items) }
            }
        }
    }

    fun refreshAllFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val appFiles = StorageManager.listAppFiles(getApplication())
            withContext(Dispatchers.Main) {
                _uiState.update { state ->
                    val filtered = applyFilterAndSort(appFiles, state.searchQuery, state.activeFilter, state.activeSort)
                    state.copy(allFiles = appFiles, filteredFiles = filtered)
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { state ->
            val filtered = applyFilterAndSort(state.allFiles, query, state.activeFilter, state.activeSort)
            state.copy(searchQuery = query, filteredFiles = filtered)
        }
    }

    fun setFilter(filter: DocFilterType) {
        _uiState.update { state ->
            val filtered = applyFilterAndSort(state.allFiles, state.searchQuery, filter, state.activeSort)
            state.copy(activeFilter = filter, filteredFiles = filtered)
        }
    }

    fun setSort(sort: SortOrder) {
        _uiState.update { state ->
            val filtered = applyFilterAndSort(state.allFiles, state.searchQuery, state.activeFilter, sort)
            state.copy(activeSort = sort, filteredFiles = filtered)
        }
    }

    private fun applyFilterAndSort(
        list: List<DocItem>,
        query: String,
        filter: DocFilterType,
        sort: SortOrder
    ): List<DocItem> {
        return list.filter { item ->
            val matchesQuery = query.isBlank() || item.name.contains(query, ignoreCase = true)
            val matchesFilter = when (filter) {
                DocFilterType.ALL -> true
                DocFilterType.PDF -> item.name.endsWith(".pdf", true)
                DocFilterType.SCANS -> item.name.startsWith("SmartDocs_Scan", true) || item.name.contains("Scan", true)
                DocFilterType.TEXT -> item.name.endsWith(".txt", true)
            }
            matchesQuery && matchesFilter
        }.let { filtered ->
            when (sort) {
                SortOrder.DATE_DESC -> filtered.sortedByDescending { it.lastModified }
                SortOrder.DATE_ASC -> filtered.sortedBy { it.lastModified }
                SortOrder.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
                SortOrder.SIZE_DESC -> filtered.sortedByDescending { it.sizeBytes }
            }
        }
    }

    fun navigateTo(screen: Screen) {
        _uiState.update { state ->
            val newHistory = if (screen == Screen.Home) {
                listOf(Screen.Home)
            } else {
                state.screenHistory + screen
            }
            state.copy(currentScreen = screen, screenHistory = newHistory)
        }
    }

    fun navigateBack(): Boolean {
        val currentHistory = _uiState.value.screenHistory
        if (currentHistory.size > 1) {
            val updatedHistory = currentHistory.dropLast(1)
            val previousScreen = updatedHistory.last()
            _uiState.update { it.copy(currentScreen = previousScreen, screenHistory = updatedHistory) }
            return true
        }
        return false
    }

    fun openDocument(uri: Uri, name: String? = null) {
        val displayName = name ?: StorageManager.getFileName(getApplication(), uri)
        viewModelScope.launch(Dispatchers.IO) {
            val pageCount = if (displayName.endsWith(".pdf", true)) {
                PdfEngine.getPageCount(getApplication(), uri)
            } else 1

            val existing = docDao.getDocByUri(uri.toString())
            val lastPage = existing?.lastReadPage ?: 1

            val docItem = DocItem(
                id = uri.toString(),
                name = displayName,
                uri = uri,
                path = uri.path ?: "",
                mimeType = if (displayName.endsWith(".pdf", true)) "application/pdf" else "text/plain",
                sizeBytes = StorageManager.getFileSize(getApplication(), uri),
                pageCount = pageCount,
                lastModified = System.currentTimeMillis(),
                lastReadPage = lastPage
            )
            docDao.insertDoc(DocEntity.fromDocItem(docItem))

            withContext(Dispatchers.Main) {
                navigateTo(Screen.Reader(uri = uri, title = displayName, startPage = lastPage))
            }
        }
    }

    fun updateReadingProgress(uri: Uri, page: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            docDao.updateReadingProgress(uri.toString(), page)
        }
    }

    fun toggleFavorite(doc: DocItem) {
        viewModelScope.launch(Dispatchers.IO) {
            docDao.setFavorite(doc.id, !doc.isFavorite)
            refreshAllFiles()
        }
    }

    fun deleteFile(doc: DocItem) {
        viewModelScope.launch(Dispatchers.IO) {
            if (doc.uri.scheme == "file") {
                doc.uri.path?.let { File(it).delete() }
            }
            docDao.deleteDoc(doc.id)
            docDao.deleteDocByUri(doc.uri.toString())
            refreshAllFiles()
            _toastEvent.emit("Deleted \"${doc.name}\"")
        }
    }

    fun renameFile(doc: DocItem, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (doc.uri.scheme == "file") {
                doc.uri.path?.let { path ->
                    val oldF = File(path)
                    val renamedF = StorageManager.renameFile(oldF, newName)
                    if (renamedF != null) {
                        docDao.deleteDoc(doc.id)
                        val newItem = doc.copy(
                            id = renamedF.absolutePath,
                            name = renamedF.name,
                            uri = Uri.fromFile(renamedF),
                            path = renamedF.absolutePath
                        )
                        docDao.insertDoc(DocEntity.fromDocItem(newItem))
                        refreshAllFiles()
                        _toastEvent.emit("Renamed to \"${renamedF.name}\"")
                    } else {
                        _toastEvent.emit("Failed to rename file")
                    }
                }
            } else {
                _toastEvent.emit("System-picked files cannot be renamed directly")
            }
        }
    }

    fun registerNewDocument(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val uri = Uri.fromFile(file)
            val isPdf = file.name.endsWith(".pdf", true)
            val pages = if (isPdf) PdfEngine.getPageCount(getApplication(), uri) else 1
            val item = DocItem(
                id = file.absolutePath,
                name = file.name,
                uri = uri,
                path = file.absolutePath,
                mimeType = if (isPdf) "application/pdf" else "text/plain",
                sizeBytes = file.length(),
                pageCount = pages,
                lastModified = file.lastModified()
            )
            docDao.insertDoc(DocEntity.fromDocItem(item))
            refreshAllFiles()
        }
    }

    fun showToast(message: String) {
        viewModelScope.launch {
            _toastEvent.emit(message)
        }
    }

    fun setLoading(isLoading: Boolean, message: String = "") {
        _uiState.update { it.copy(isLoading = isLoading, loadingMessage = message) }
    }

    fun setThemePreference(isDark: Boolean?) {
        _uiState.update { it.copy(isDarkTheme = isDark) }
    }
}
