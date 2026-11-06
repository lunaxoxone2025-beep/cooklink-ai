package com.cooklink.ai.data.importer

import com.cooklink.ai.data.ai.AiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ImportState {
    data object Idle : ImportState
    data class Running(val stage: ImportStage, val what: String, val progress: Float? = null) : ImportState
    data class Done(val recipeId: Long, val notice: String?, val opened: Boolean = false) : ImportState
    data class Failed(val message: String) : ImportState
}

/**
 * Runs imports in an app-wide scope so they keep going when the user switches tabs
 * or rotates the phone, and exposes progress as a StateFlow.
 */
class ImportController(private val importer: RecipeImporter) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()
    private var job: Job? = null

    val busy get() = _state.value is ImportState.Running

    fun importLink(url: String) = start("link") { importer.importLink(url) { stage -> _state.value = ImportState.Running(stage, "link") } }

    fun importText(text: String) = start("text") { importer.importText(text) { stage -> _state.value = ImportState.Running(stage, "text") } }

    fun importMedia(images: List<android.net.Uri>, video: VideoInfo?, notes: String, link: String?) = start("media") {
        importer.importMedia(
            images, video, notes, link,
            onStage = { stage -> _state.value = ImportState.Running(stage, "media") },
            onProgress = { p -> _state.value = ImportState.Running(ImportStage.UPLOADING_VIDEO, "media", p) },
        )
    }

    fun cancel() {
        job?.cancel()
        _state.value = ImportState.Idle
    }

    fun markOpened() {
        (_state.value as? ImportState.Done)?.let { _state.value = it.copy(opened = true) }
    }

    fun dismiss() {
        if (!busy) _state.value = ImportState.Idle
    }

    private fun start(what: String, block: suspend () -> ImportResult) {
        if (busy) return
        _state.value = ImportState.Running(ImportStage.READING_LINK, what)
        job = scope.launch {
            _state.value = try {
                val result = block()
                ImportState.Done(result.recipeId, result.notice)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiException) {
                ImportState.Failed(e.message ?: "Import failed.")
            } catch (e: Exception) {
                ImportState.Failed("Something went wrong: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }
}
