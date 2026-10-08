package io.github.chiragbhatn.expensetracker.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.chiragbhatn.expensetracker.AppContainer
import io.github.chiragbhatn.expensetracker.backup.BackupReadResult
import io.github.chiragbhatn.expensetracker.backup.CsvImportResult
import io.github.chiragbhatn.expensetracker.backup.CsvKind
import io.github.chiragbhatn.expensetracker.backup.CsvPreview
import io.github.chiragbhatn.expensetracker.backup.DuplicateChoice
import io.github.chiragbhatn.expensetracker.backup.MergeResult
import io.github.chiragbhatn.expensetracker.data.RestoreMode
import io.github.chiragbhatn.expensetracker.data.RestoreSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

sealed interface ExportState {
    data object Idle : ExportState
    data object Working : ExportState
    data class Done(val message: String) : ExportState
    data class Failed(val message: String) : ExportState
}

sealed interface RestoreState {
    data object Idle : RestoreState
    data object Reading : RestoreState

    /** The backup was read and checked; [merge] previews a merge when this phone already has data. */
    data class Checked(val fileName: String?, val result: BackupReadResult, val hasExistingData: Boolean, val merge: MergeResult?) : RestoreState
    data object Restoring : RestoreState
    data class Done(val summary: RestoreSummary) : RestoreState
    data class Failed(val message: String) : RestoreState
}

class BackupViewModel(private val container: AppContainer) : ViewModel() {
    private val _export = MutableStateFlow<ExportState>(ExportState.Idle)
    val export: StateFlow<ExportState> = _export.asStateFlow()

    private val _restore = MutableStateFlow<RestoreState>(RestoreState.Idle)
    val restore: StateFlow<RestoreState> = _restore.asStateFlow()

    val suggestedFileName: String get() = container.backup.backupFileName()

    /** Writes the full backup to [open]'s stream (a file the user chose, or one to share). */
    fun exportTo(open: () -> OutputStream?, done: String, onWritten: () -> Unit = {}) {
        _export.value = ExportState.Working
        viewModelScope.launch {
            _export.value = try {
                withContext(Dispatchers.IO) {
                    val out = open() ?: throw IllegalStateException("The file could not be created")
                    out.use { container.backup.exportWorkbook(it) }
                }
                onWritten()
                ExportState.Done(done)
            } catch (e: Exception) {
                ExportState.Failed("Export failed: ${e.message}")
            }
        }
    }

    /** Reads and checks a backup. Nothing is restored until [restore] is called. */
    fun check(fileName: String?, open: () -> InputStream?) {
        _restore.value = RestoreState.Reading
        viewModelScope.launch {
            _restore.value = try {
                val result = withContext(Dispatchers.IO) {
                    val input = open() ?: throw IllegalStateException("The file could not be opened")
                    input.use { container.backup.readWorkbook(it, fileName) }
                }
                val existing = container.backup.snapshot()
                val merge = result.data?.takeIf { existing.hasUserData }?.let { container.backup.previewMerge(it) }
                RestoreState.Checked(fileName, result, existing.hasUserData, merge)
            } catch (e: Exception) {
                RestoreState.Failed("The file could not be read: ${e.message}")
            }
        }
    }

    fun restore(mode: RestoreMode) {
        val checked = _restore.value as? RestoreState.Checked ?: return
        val data = checked.result.data ?: return
        _restore.value = RestoreState.Restoring
        viewModelScope.launch {
            _restore.value = try {
                RestoreState.Done(container.backup.restore(data, mode))
            } catch (e: Exception) {
                RestoreState.Failed("Restore failed and nothing was changed: ${e.message}")
            }
        }
    }

    fun resetRestore() {
        _restore.value = RestoreState.Idle
    }

    fun resetExport() {
        _export.value = ExportState.Idle
    }
}

sealed interface CsvState {
    data object Idle : CsvState
    data object Working : CsvState
    data class Preview(val fileName: String?, val preview: CsvPreview) : CsvState
    data class Imported(val result: CsvImportResult) : CsvState
    data class Message(val text: String, val isError: Boolean) : CsvState
}

class CsvViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<CsvState>(CsvState.Idle)
    val state: StateFlow<CsvState> = _state.asStateFlow()

    fun fileName(kind: CsvKind) = container.backup.csvFileName(kind)

    fun exportTo(kind: CsvKind, open: () -> OutputStream?, done: String, onWritten: () -> Unit = {}) {
        _state.value = CsvState.Working
        viewModelScope.launch {
            _state.value = try {
                val text = container.backup.exportCsv(kind)
                withContext(Dispatchers.IO) {
                    val out = open() ?: throw IllegalStateException("The file could not be created")
                    out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
                onWritten()
                CsvState.Message(done, isError = false)
            } catch (e: Exception) {
                CsvState.Message("Export failed: ${e.message}", isError = true)
            }
        }
    }

    fun check(kind: CsvKind, fileName: String?, open: () -> InputStream?) {
        _state.value = CsvState.Working
        viewModelScope.launch {
            _state.value = try {
                val text = withContext(Dispatchers.IO) {
                    val input = open() ?: throw IllegalStateException("The file could not be opened")
                    input.use { it.readBytes() }.toString(Charsets.UTF_8)
                }
                CsvState.Preview(fileName, container.backup.previewCsv(kind, text))
            } catch (e: Exception) {
                CsvState.Message("The file could not be read: ${e.message}", isError = true)
            }
        }
    }

    fun import(choice: DuplicateChoice) {
        val preview = (_state.value as? CsvState.Preview)?.preview ?: return
        _state.value = CsvState.Working
        viewModelScope.launch {
            _state.value = try {
                CsvState.Imported(container.backup.importCsv(preview, choice))
            } catch (e: Exception) {
                CsvState.Message("Import failed and nothing was changed: ${e.message}", isError = true)
            }
        }
    }

    fun reset() {
        _state.value = CsvState.Idle
    }
}
