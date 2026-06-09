package org.stuhi.glyphpomodoro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.stuhi.glyphpomodoro.ui.Glyph
import org.stuhi.glyphpomodoro.ui.GlyphTheme
import kotlin.math.max

private enum class Screen { MAIN, DIGITS, ICONS }

private enum class Ctl { TOGGLE, RESET, SKIP, STOP }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TimerController.restore(applicationContext)
        setContent {
            GlyphTheme {
                Surface(color = Glyph.Bg) {
                    var screen by remember { mutableStateOf(Screen.MAIN) }
                    when (screen) {
                        Screen.MAIN -> HomeScreen(
                            onEditDigits = { screen = Screen.DIGITS },
                            onEditIcons = { screen = Screen.ICONS },
                        )
                        Screen.DIGITS -> DigitEditorScreen(onBack = { screen = Screen.MAIN })
                        Screen.ICONS -> IconEditorScreen(onBack = { screen = Screen.MAIN })
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Home / control

@Composable
private fun HomeScreen(onEditDigits: () -> Unit, onEditIcons: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepo(ctx.applicationContext) }
    val scope = rememberCoroutineScope()
    val settings by repo.flow.collectAsState(initial = Settings())
    val timer by TimerController.state.collectAsState()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(500) } }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}
    LaunchedEffect(Unit) {
        permLauncher.launch(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS))
    }

    // shake test
    var testOn by remember { mutableStateOf(false) }
    var accelAvg by remember { mutableFloatStateOf(0f) }
    var accelPeak by remember { mutableFloatStateOf(0f) }
    var shakeCount by remember { mutableIntStateOf(0) }
    var flash by remember { mutableStateOf(false) }
    var detector by remember { mutableStateOf<ShakeDetector?>(null) }

    LaunchedEffect(shakeCount) { if (shakeCount > 0) { flash = true; delay(450); flash = false } }
    DisposableEffect(testOn) {
        if (testOn) {
            val d = ShakeDetector(
                context = ctx.applicationContext,
                threshold = settings.shakeThreshold,
                onAccel = { accelAvg = accelAvg * 0.85f + it * 0.15f; accelPeak = max(it, accelPeak * 0.93f) },
                onShake = { shakeCount++ },
            ).also { it.start() }
            detector = d
            onDispose { d.stop(); detector = null }
        } else onDispose {}
    }
    LaunchedEffect(settings.shakeThreshold) { detector?.threshold = settings.shakeThreshold }

    fun control(c: Ctl) {
        val app = ctx.applicationContext
        when (c) {
            Ctl.TOGGLE -> { TimerController.onTrigger(app); PomodoroAlarm.scheduleOrCancel(app) }
            Ctl.SKIP -> { TimerController.advance(app); PomodoroAlarm.scheduleOrCancel(app) }
            Ctl.RESET -> { TimerController.reset(app); PomodoroAlarm.cancel(app) }
            Ctl.STOP -> { TimerController.setDormant(app); PomodoroAlarm.cancel(app) }
        }
        PomodoroNotification.update(app)
    }

    val running = timer.state == RunState.RUNNING
    val primaryLabel = when (timer.state) {
        RunState.RUNNING -> "PAUSE"
        RunState.PAUSED -> "RESUME"
        else -> "START"
    }

    Scaffold(
        containerColor = Glyph.Bg,
        topBar = {
            Column(
                Modifier.background(Glyph.Bg).statusBarsPadding().padding(horizontal = 20.dp)
            ) {
                Header(running)
                Spacer(Modifier.height(16.dp))
                StatusCard(timer, TimerController.remainingMs(now))
            }
        },
        bottomBar = { ControlBar(primaryLabel, ::control) },
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(2.dp))
            SectionLabel("CUSTOMISE")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("Digit font", Modifier.weight(1f), onEditDigits)
                Tile("Icons", Modifier.weight(1f), onEditIcons)
            }

            SectionLabel("BRIGHTNESS")
            Panel {
                SliderRow("RUNNING", "${settings.brightness}", settings.brightness.toFloat(), 1f..255f) { v ->
                    scope.launch { repo.setBrightness(v.toInt()) }
                }
                Spacer(Modifier.height(6.dp))
                SliderRow("PAUSED", "${settings.pausedBrightness}", settings.pausedBrightness.toFloat(), 0f..255f) { v ->
                    scope.launch { repo.setPausedBrightness(v.toInt()) }
                }
            }

            SectionLabel("SHAKE")
            ShakeCard(
                threshold = settings.shakeThreshold,
                testOn = testOn,
                onTest = { shakeCount = 0; testOn = it },
                flash = flash,
                shakeCount = shakeCount,
                accelAvg = accelAvg,
                accelPeak = accelPeak,
                onThreshold = { v -> scope.launch { repo.setShakeThreshold(v) } },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Header(running: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (running) Glyph.Red else Glyph.Muted))
        Spacer(Modifier.width(10.dp))
        Text("GLYPH POMODORO", style = MaterialTheme.typography.titleMedium, color = Glyph.Text)
    }
}

@Composable
private fun StatusCard(timer: TimerSnapshot, remainingMs: Long) {
    val isBreak = timer.phase != Phase.WORK
    val showTime = timer.state == RunState.RUNNING || timer.state == RunState.PAUSED
    val secs = (remainingMs / 1000).toInt()
    val timeText = if (showTime) "%02d:%02d".format(secs / 60, secs % 60) else "––:––"
    val phaseText = timer.phase.name.replace('_', ' ')

    Panel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                phaseText,
                style = MaterialTheme.typography.titleMedium,
                color = if (isBreak) Glyph.Muted else Glyph.Red,
            )
            Spacer(Modifier.weight(1f))
            BlockDots(timer.completedWorkBlocks)
        }
        Spacer(Modifier.height(14.dp))
        Text(timeText, style = MaterialTheme.typography.displaySmall, color = Glyph.Text)
        Spacer(Modifier.height(6.dp))
        Text(timer.state.name, style = MaterialTheme.typography.bodySmall, color = Glyph.Muted)
    }
}

@Composable
private fun BlockDots(completed: Int) {
    val filled = completed % Pomodoro.WORK_BLOCKS_PER_LONG_BREAK
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(Pomodoro.WORK_BLOCKS_PER_LONG_BREAK) { i ->
            Box(
                Modifier.size(7.dp).clip(CircleShape)
                    .background(if (i < filled) Glyph.Red else Glyph.CellOff)
            )
        }
    }
}

@Composable
private fun ControlBar(primaryLabel: String, onCtl: (Ctl) -> Unit) {
    Surface(color = Glyph.Black) {
        Column(Modifier.navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Glyph.Line))
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("RESET", Modifier.weight(1f)) { onCtl(Ctl.RESET) }
                    GhostButton("SKIP", Modifier.weight(1f)) { onCtl(Ctl.SKIP) }
                    GhostButton("STOP", Modifier.weight(1f)) { onCtl(Ctl.STOP) }
                }
                Button(
                    onClick = { onCtl(Ctl.TOGGLE) },
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Glyph.Red, contentColor = Color.White),
                ) { Text(primaryLabel, style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

// ---------------------------------------------------------------- Back-tap card

@Composable
private fun ShakeCard(
    threshold: Float,
    testOn: Boolean,
    onTest: (Boolean) -> Unit,
    flash: Boolean,
    shakeCount: Int,
    accelAvg: Float,
    accelPeak: Float,
    onThreshold: (Float) -> Unit,
) {
    Panel {
        Text(
            "Shake to start, then shake again to pause or resume. Tune the threshold just under your shake's peak.",
            style = MaterialTheme.typography.bodySmall, color = Glyph.Muted,
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("RUN TEST", style = MaterialTheme.typography.labelMedium, color = Glyph.Text)
            Spacer(Modifier.weight(1f))
            Switch(checked = testOn, onCheckedChange = onTest)
        }

        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(if (flash) Glyph.Red else Glyph.PanelHi)
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (flash) "SHAKE  ·  $shakeCount" else "WAITING  ·  $shakeCount",
                style = MaterialTheme.typography.labelLarge,
                color = if (flash) Color.White else Glyph.Muted,
            )
        }

        Spacer(Modifier.height(16.dp))
        SliderRow(
            "JOLT",
            "avg ${"%.1f".format(accelAvg)}  pk ${"%.1f".format(accelPeak)}  ▸ ${"%.1f".format(threshold)}",
            threshold, 0f..30f, onThreshold,
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: String,
    sliderValue: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Glyph.Text)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = Glyph.Muted)
    }
    Slider(
        value = sliderValue, onValueChange = onChange, valueRange = range,
        colors = SliderDefaults.colors(
            thumbColor = Glyph.Red, activeTrackColor = Glyph.Red, inactiveTrackColor = Glyph.Line,
        ),
    )
}

// ---------------------------------------------------------------- Digit editor

@Composable
private fun DigitEditorScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var digit by remember { mutableIntStateOf(0) }
    val cells = remember { mutableStateListOf<Boolean>() }
    LaunchedEffect(digit) {
        cells.clear(); cells.addAll(DigitFont.cells(ctx, digit).toList())
    }

    EditorScaffold("DIGIT FONT", onBack) {
        Text("SELECT", style = MaterialTheme.typography.labelMedium, color = Glyph.Muted)
        for (rowStart in intArrayOf(0, 5)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (d in rowStart until rowStart + 5) {
                    Chip("$d", d == digit, Modifier.weight(1f)) { digit = d }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        GridPanel(
            w = DigitFont.W, h = DigitFont.H, circular = false, key = digit, maxWidth = 190.dp,
            spec = { x, y ->
                val idx = y * DigitFont.W + x
                CellSpec(inside = true, on = cells.getOrElse(idx) { false }, ghost = false)
            },
            paint = { x, y, v ->
                val idx = y * DigitFont.W + x
                if (idx < cells.size && cells[idx] != v) {
                    cells[idx] = v
                    DigitFont.set(ctx, digit, cells.toBooleanArray())
                }
            },
        )

        GhostButton("CLEAR", Modifier.fillMaxWidth()) {
            for (i in cells.indices) cells[i] = false
            DigitFont.set(ctx, digit, cells.toBooleanArray())
        }
        Text(
            "Empty columns are cropped, so digits can differ in width. Saves live.",
            style = MaterialTheme.typography.bodySmall, color = Glyph.Muted,
        )
    }
}

// ---------------------------------------------------------------- Icon editor

@Composable
private fun IconEditorScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val n = remember { MatrixRenderer.size() }
    var icon by remember { mutableStateOf(IconStore.Icon.PLAY) }
    var frameIdx by remember { mutableIntStateOf(0) }
    var frameCount by remember { mutableIntStateOf(IconStore.frameCount(ctx, IconStore.Icon.PLAY)) }
    var durMs by remember { mutableIntStateOf(IconStore.frameDurationMs(ctx)) }
    var holdFirst by remember { mutableIntStateOf(IconStore.holdFirstMs(ctx)) }
    var holdLast by remember { mutableIntStateOf(IconStore.holdLastMs(ctx)) }
    val cells = remember { mutableStateListOf<Boolean>() }
    val prevCells = remember { mutableStateListOf<Boolean>() }

    LaunchedEffect(icon) { frameCount = IconStore.frameCount(ctx, icon); frameIdx = 0 }
    LaunchedEffect(icon, frameIdx, frameCount) {
        cells.clear(); cells.addAll(IconStore.getFrame(ctx, icon, frameIdx).toList())
        prevCells.clear()
        if (frameIdx > 0) prevCells.addAll(IconStore.getFrame(ctx, icon, frameIdx - 1).toList())
    }

    EditorScaffold("ICONS", onBack) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (ic in IconStore.Icon.entries) {
                Chip(ic.name, ic == icon, Modifier.weight(1f)) { icon = ic }
            }
        }

        Text(
            if (frameCount <= 1) "1 FRAME · STATIC ICON" else "$frameCount FRAMES · ANIMATION",
            style = MaterialTheme.typography.labelMedium, color = Glyph.Muted,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (f in 0 until frameCount) Chip("${f + 1}", f == frameIdx) { frameIdx = f }
            Chip("+", false) {
                IconStore.addFrame(ctx, icon)
                frameCount = IconStore.frameCount(ctx, icon)
                frameIdx = frameCount - 1
            }
            if (frameCount > 1) Chip("−", false) {
                IconStore.removeFrame(ctx, icon, frameIdx)
                frameCount = IconStore.frameCount(ctx, icon)
                frameIdx = frameIdx.coerceAtMost(frameCount - 1)
            }
        }

        GridPanel(
            w = n, h = n, circular = true, key = icon to frameIdx,
            spec = { x, y ->
                val idx = y * n + x
                CellSpec(
                    inside = MatrixRenderer.inCircle(x, y, n),
                    on = cells.getOrElse(idx) { false },
                    ghost = prevCells.getOrElse(idx) { false },
                )
            },
            paint = { x, y, v ->
                val idx = y * n + x
                if (MatrixRenderer.inCircle(x, y, n) && idx < cells.size && cells[idx] != v) {
                    cells[idx] = v
                    IconStore.setFrame(ctx, icon, frameIdx, cells.toBooleanArray())
                }
            },
        )

        GhostButton("CLEAR FRAME", Modifier.fillMaxWidth()) {
            for (i in cells.indices) cells[i] = false
            IconStore.setFrame(ctx, icon, frameIdx, cells.toBooleanArray())
        }

        SliderRow("FRAME TIME", "$durMs ms", durMs.toFloat(), 40f..1000f) {
            durMs = it.toInt(); IconStore.setFrameDurationMs(ctx, durMs)
        }
        SliderRow("HOLD FIRST", "$holdFirst ms", holdFirst.toFloat(), 0f..2000f) {
            holdFirst = it.toInt(); IconStore.setHoldFirstMs(ctx, holdFirst)
        }
        SliderRow("HOLD LAST", "$holdLast ms", holdLast.toFloat(), 0f..2000f) {
            holdLast = it.toInt(); IconStore.setHoldLastMs(ctx, holdLast)
        }
    }
}

// ---------------------------------------------------------------- shared pieces

@Composable
private fun EditorScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .statusBarsPadding().navigationBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", style = MaterialTheme.typography.displaySmall, color = Glyph.Text,
                modifier = Modifier.clickable(onClick = onBack))
            Spacer(Modifier.width(14.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Glyph.Text)
        }
        content()
        Spacer(Modifier.height(24.dp))
    }
}

/** A bordered dark card. */
@Composable
private fun Panel(content: @Composable () -> Unit) {
    Surface(
        color = Glyph.Panel,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Glyph.Line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) { content() }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = Glyph.Muted,
        modifier = Modifier.padding(top = 4.dp))
}

/** A square-ish tile button used for the customise actions. */
@Composable
private fun Tile(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = Glyph.Panel,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Glyph.Line),
        modifier = modifier.height(76.dp).clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = Glyph.Text,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun GhostButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Glyph.Line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Glyph.Text),
    ) { Text(label, style = MaterialTheme.typography.labelMedium) }
}

@Composable
private fun Chip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(
            onClick = onClick, modifier = modifier, shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Glyph.Red, contentColor = Color.White),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        ) { Text(label, style = MaterialTheme.typography.labelMedium) }
    } else {
        OutlinedButton(
            onClick = onClick, modifier = modifier, shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Glyph.Line),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Glyph.Text),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        ) { Text(label, style = MaterialTheme.typography.labelMedium) }
    }
}

private class CellSpec(val inside: Boolean, val on: Boolean, val ghost: Boolean)

/**
 * A w×h editable LED grid on a rounded black panel. Tap toggles a cell; drag paints — the
 * first touched cell decides whether the stroke draws or erases.
 */
@Composable
private fun GridPanel(
    w: Int,
    h: Int,
    circular: Boolean,
    key: Any,
    spec: (x: Int, y: Int) -> CellSpec,
    paint: (x: Int, y: Int, value: Boolean) -> Unit,
    maxWidth: Dp = Dp.Unspecified,
) {
    val gap = 2.dp
    val widthMod = if (maxWidth == Dp.Unspecified) Modifier.fillMaxWidth() else Modifier.widthIn(max = maxWidth)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            widthMod
                .clip(RoundedCornerShape(if (circular) 50 else 18))
                .background(Glyph.Black)
                .padding(if (circular) 8.dp else 12.dp),
        ) {
            Column(
            Modifier
                .fillMaxWidth()
                .pointerInput(key, w, h) {
                    val gapPx = gap.toPx()
                    fun cell(o: Offset): Pair<Int, Int> {
                        val pitch = (size.width.toFloat() - (w - 1) * gapPx) / w + gapPx
                        return (o.x / pitch).toInt().coerceIn(0, w - 1) to
                            (o.y / pitch).toInt().coerceIn(0, h - 1)
                    }
                    detectTapGestures { o ->
                        val (c, r) = cell(o)
                        if (spec(c, r).inside) paint(c, r, !spec(c, r).on)
                    }
                }
                .pointerInput(key, w, h) {
                    val gapPx = gap.toPx()
                    fun cell(o: Offset): Pair<Int, Int> {
                        val pitch = (size.width.toFloat() - (w - 1) * gapPx) / w + gapPx
                        return (o.x / pitch).toInt().coerceIn(0, w - 1) to
                            (o.y / pitch).toInt().coerceIn(0, h - 1)
                    }
                    var target = false
                    detectDragGestures(
                        onDragStart = { o ->
                            val (c, r) = cell(o)
                            target = !spec(c, r).on
                            if (spec(c, r).inside) paint(c, r, target)
                        },
                        onDrag = { change, _ ->
                            val (c, r) = cell(change.position)
                            if (spec(c, r).inside) paint(c, r, target)
                        },
                    )
                },
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            for (y in 0 until h) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (x in 0 until w) {
                        val s = spec(x, y)
                        val color = when {
                            !s.inside -> Glyph.Black
                            s.on -> Color.White
                            s.ghost -> Glyph.CellGhost
                            else -> Glyph.CellOff
                        }
                        Box(Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(color))
                    }
                }
            }
            }
        }
    }
}

