@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cooklink.ai.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooklink.ai.AppContainer
import com.cooklink.ai.model.Recipe
import com.cooklink.ai.model.formatQuantity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** A countdown for one step. Remaining time is derived from wall-clock time so it stays accurate. */
private class StepTimer(val step: Int, val label: String, val totalSeconds: Int) {
    var remaining by mutableStateOf(totalSeconds)
    var running by mutableStateOf(false)
    var finished by mutableStateOf(false)
    var endsAt = 0L

    fun start() {
        endsAt = System.currentTimeMillis() + remaining * 1000L
        running = true
        finished = false
    }

    fun pause() {
        tick()
        running = false
    }

    fun reset() {
        running = false
        finished = false
        remaining = totalSeconds
    }

    /** Returns true the moment the timer reaches zero. */
    fun tick(): Boolean {
        if (!running) return false
        remaining = ((endsAt - System.currentTimeMillis() + 999) / 1000).toInt().coerceAtLeast(0)
        if (remaining == 0) {
            running = false
            finished = true
            return true
        }
        return false
    }
}

/**
 * Full-screen, one-step-per-page cooking mode: big text, read-aloud, step timers with an alarm,
 * ingredient checklist and progress. Keeps the screen on while open.
 */
@Composable
fun CookingModeScreen(container: AppContainer, recipeId: Long, servings: Int, onClose: () -> Unit) {
    val recipe by remember(recipeId) { container.recipes.recipe(recipeId) }.collectAsStateWithLifecycle(initialValue = null)
    val r = recipe ?: run {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    CookingContent(r, servings.takeIf { it > 0 } ?: r.servings, onClose)
}

@Composable
private fun CookingContent(recipe: Recipe, servings: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val factor = if (recipe.servings > 0) servings.toDouble() / recipe.servings else 1.0

    // Pages: 0 = ingredients, 1..n = steps, n+1 = done.
    val stepCount = recipe.steps.size
    val pager = rememberPagerState { stepCount + 2 }
    val checked = remember { mutableStateListOf<Int>() }
    // One timer per step that has a duration; page number == step number.
    val timers = remember {
        mutableStateMapOf<Int, StepTimer>().apply {
            recipe.steps.forEachIndexed { i, st ->
                st.durationMinutes?.let { put(i + 1, StepTimer(i + 1, st.text.take(40), it * 60)) }
            }
        }
    }
    var showChecklist by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var alarmFor by remember { mutableStateOf<StepTimer?>(null) }

    // Keep the screen on and hide system bars while cooking.
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        val window = (context as? android.app.Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            view.keepScreenOn = false
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Text-to-speech in the recipe's language if the phone has it, otherwise the phone's language.
    var ttsReady by remember { mutableStateOf(false) }
    var readAloud by remember { mutableStateOf(false) }
    val tts = remember {
        TextToSpeech(context.applicationContext) { status -> ttsReady = status == TextToSpeech.SUCCESS }
    }
    LaunchedEffect(ttsReady) {
        if (ttsReady) runCatching { tts.language = Locale.getDefault() }
    }
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 90) }.getOrNull() }
    DisposableEffect(Unit) {
        onDispose {
            tts.stop()
            tts.shutdown()
            tone?.release()
        }
    }

    fun speak(text: String) {
        if (ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cooklink-step")
    }

    fun pageText(page: Int): String = when (page) {
        0 -> "Ingredients for ${recipe.title}. " + recipe.ingredients.joinToString(". ") { ingredientText(it.quantity?.times(factor), it.unit, it.name) }
        in 1..stepCount -> "Step $page. " + recipe.steps[page - 1].text
        else -> "All done. Enjoy your meal!"
    }

    // Read each new page aloud when read-aloud is on.
    LaunchedEffect(pager.currentPage, readAloud, ttsReady) {
        if (readAloud) speak(pageText(pager.currentPage)) else if (ttsReady) tts.stop()
    }

    // One ticker drives every timer.
    LaunchedEffect(Unit) {
        while (true) {
            timers.values.forEach { t ->
                if (t.tick()) {
                    alarmFor = t
                    if (readAloud) speak("Timer done: ${t.label}")
                }
            }
            delay(250)
        }
    }
    // Ring until the alarm is dismissed.
    LaunchedEffect(alarmFor) {
        while (alarmFor != null) {
            tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 600)
            delay(1500)
        }
    }

    BackHandler { confirmExit = true }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding(),
    ) {
        // Top bar
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { confirmExit = true }) { Icon(Icons.Filled.Close, contentDescription = "Exit cooking mode") }
            Column(Modifier.weight(1f)) {
                Text(recipe.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    when (pager.currentPage) {
                        0 -> "Get ready · $servings servings"
                        in 1..stepCount -> "Step ${pager.currentPage} of $stepCount"
                        else -> "Finished"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { showChecklist = true }) { Icon(Icons.Outlined.Checklist, contentDescription = "Ingredient checklist") }
            IconButton(onClick = { readAloud = !readAloud }, enabled = ttsReady) {
                Icon(
                    if (readAloud) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = if (readAloud) "Stop reading aloud" else "Read steps aloud",
                    tint = if (readAloud) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LinearProgressIndicator(
            progress = { if (stepCount == 0) 1f else (pager.currentPage.coerceAtMost(stepCount)).toFloat() / stepCount },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )

        // Running timers, visible from any step.
        val active = timers.values.filter { it.running || it.finished }.sortedBy { it.step }
        if (active.isNotEmpty()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(active, key = { it.step }) { t ->
                    AssistChip(
                        onClick = { scope.launch { pager.animateScrollToPage(t.step) } },
                        label = { Text("Step ${t.step}: ${if (t.finished) "done!" else clock(t.remaining)}") },
                        leadingIcon = { Icon(Icons.Outlined.Timer, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
        }

        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            when {
                page == 0 -> IngredientsPage(recipe, factor, checked)
                page <= stepCount -> {
                    val step = recipe.steps[page - 1]
                    StepPage(page, stepCount, step.text, step.temperature, timers[page])
                }
                else -> DonePage(recipe.title, onClose)
            }
        }

        // Big previous / next controls
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                enabled = pager.currentPage > 0,
                modifier = Modifier.weight(1f).height(64.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Back", fontSize = 18.sp)
            }
            Button(
                onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                enabled = pager.currentPage < stepCount + 1,
                modifier = Modifier.weight(1f).height(64.dp),
            ) {
                Text(if (pager.currentPage == 0) "Start" else "Next", fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }

    if (showChecklist) {
        ModalBottomSheet(onDismissRequest = { showChecklist = false }) {
            Text("Ingredients", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                itemsIndexed(recipe.ingredients) { i, ing ->
                    ChecklistRow(ingredientText(ing.quantity?.times(factor), ing.unit, ing.name), i in checked) {
                        if (i in checked) checked.remove(i) else checked.add(i)
                    }
                }
            }
        }
    }

    alarmFor?.let { t ->
        AlertDialog(
            onDismissRequest = { alarmFor = null },
            icon = { Icon(Icons.Outlined.Timer, contentDescription = null) },
            title = { Text("Timer done") },
            text = { Text("Step ${t.step}: ${t.label}…", style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                Button(onClick = {
                    alarmFor = null
                    scope.launch { pager.animateScrollToPage(t.step) }
                }) { Text("OK") }
            },
        )
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Leave cooking mode?") },
            text = { Text(if (timers.values.any { it.running }) "Running timers will stop." else "You can come back any time.") },
            confirmButton = { TextButton(onClick = { confirmExit = false; onClose() }) { Text("Leave") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Keep cooking") } },
        )
    }
}

@Composable
private fun IngredientsPage(recipe: Recipe, factor: Double, checked: MutableList<Int>) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            Text("Get everything ready", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Tick off each ingredient as you set it out.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (recipe.equipment.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("You'll need: ${recipe.equipment.joinToString()}", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(12.dp))
        }
        itemsIndexed(recipe.ingredients) { i, ing ->
            ChecklistRow(ingredientText(ing.quantity?.times(factor), ing.unit, ing.name), i in checked, big = true) {
                if (i in checked) checked.remove(i) else checked.add(i)
            }
        }
    }
}

@Composable
private fun StepPage(number: Int, total: Int, text: String, temperature: String, timer: StepTimer?) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "STEP $number / $total",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        Text(text, fontSize = 28.sp, lineHeight = 38.sp, style = MaterialTheme.typography.headlineSmall)
        if (temperature.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Thermostat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(temperature, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (timer != null) {
            Spacer(Modifier.height(24.dp))
            TimerCard(timer)
        }
    }
}

@Composable
private fun TimerCard(t: StepTimer) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (t.finished) "Done!" else clock(t.remaining),
                fontSize = 40.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            if (t.running) {
                FilledTonalButton(onClick = { t.pause() }) {
                    Icon(Icons.Filled.Pause, contentDescription = null)
                    Text("Pause")
                }
            } else if (!t.finished) {
                Button(onClick = { t.start() }) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text(if (t.remaining == t.totalSeconds) "Start" else "Resume")
                }
            }
            if (t.remaining != t.totalSeconds || t.finished) {
                IconButton(onClick = { t.reset() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reset timer", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

@Composable
private fun DonePage(title: String, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🍽️", fontSize = 64.sp)
        Spacer(Modifier.height(12.dp))
        Text("Enjoy your $title!", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onClose) { Text("Finish") }
    }
}

@Composable
private fun ChecklistRow(text: String, checked: Boolean, big: Boolean = false, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClick = onToggle).padding(horizontal = 12.dp, vertical = if (big) 6.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(
            text,
            style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
            color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (checked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
        )
    }
}

private fun ingredientText(qty: Double?, unit: String, name: String) =
    listOf(formatQuantity(qty), unit, name).filter { it.isNotBlank() }.joinToString(" ")

private fun clock(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
