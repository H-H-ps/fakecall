package com.example.fakecall

import android.content.Context
import android.graphics.Color as AndroidColor
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class Phase { RINGING, IN_CALL }

class IncomingCallActivity : ComponentActivity() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var done = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )

        // فُتحت الشاشة: أوقف إشعار الرنين (لتجنّب صوتين) واعتبر المكالمة «مستلمة».
        CallState.consumed = true
        CallNotifications.cancelIncoming(this)

        val contact = Prefs.activeContact(this)
        val startAnswered = intent.getBooleanExtra(CallConfig.EXTRA_AUTO_ANSWER, false)
        val ringStart = intent.getLongExtra(CallConfig.EXTRA_RING_START, 0L)
        val ringMs = if (ringStart > 0) {
            (CallConfig.RING_MS - (System.currentTimeMillis() - ringStart))
                .coerceIn(800L, CallConfig.RING_MS)
        } else CallConfig.RING_MS
        val photo = ImageStore.loadBitmap(this, contact).asImageBitmap()

        if (!startAnswered) startRinging()

        setContent {
            CallScreen(
                name = contact.name,
                photo = photo,
                ringMs = ringMs,
                startAnswered = startAnswered,
                onRingStop = ::stopRinging,
                onMissed = {
                    stopRinging()
                    CallNotifications.showMissed(this, contact)
                    endCall()
                },
                onFinish = {
                    stopRinging()
                    endCall()
                }
            )
        }
    }

    private fun startRinging() {
        val uri = Prefs.ringtoneUri(this)?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            isLooping = true
            play()
        }
        if (Prefs.vibrate(this)) {
            vibrator = getSystemService(Vibrator::class.java)
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 700), 0))
        }
    }

    private fun stopRinging() {
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun endCall() {
        if (done) return
        done = true
        finishAndRemoveTask()
    }

    /** أزرار الصوت تُسكِت الرنين كما في الهاتف الحقيقي. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            stopRinging()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }
}

// =====================================================================================
//  الواجهة
// =====================================================================================

@Composable
private fun CallScreen(
    name: String,
    photo: ImageBitmap,
    ringMs: Long,
    startAnswered: Boolean,
    onRingStop: () -> Unit,
    onMissed: () -> Unit,
    onFinish: () -> Unit
) {
    var phase by remember { mutableStateOf(if (startAnswered) Phase.IN_CALL else Phase.RINGING) }
    var seconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(phase) {
        if (phase == Phase.RINGING) {
            delay(ringMs)          // انتهى الرنين دون رد
            onMissed()
        } else {
            onRingStop()
            repeat((CallConfig.ANSWERED_MS / 1000).toInt()) {
                delay(1000)
                seconds++
            }
            onFinish()             // انتهت المكالمة بعد الرد
        }
    }

    BackHandler { if (phase == Phase.RINGING) onMissed() else onFinish() }

    when (phase) {
        Phase.RINGING -> RingingContent(
            name = name,
            photo = photo,
            onAnswer = { phase = Phase.IN_CALL },
            onDecline = onMissed
        )
        Phase.IN_CALL -> InCallContent(name = name, seconds = seconds, photo = photo, onEnd = onFinish)
    }
}

@Composable
private fun RingingContent(name: String, photo: ImageBitmap, onAnswer: () -> Unit, onDecline: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Image(
            bitmap = photo,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0xB3000000),
                    0.30f to Color.Transparent,
                    0.60f to Color.Transparent,
                    1f to Color(0xE6000000)
                )
            )
        )
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))
            Text(stringResource(R.string.incoming_call), color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                name,
                color = Color.White,
                fontSize = 38.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.weight(1f))
            SwipeToAnswer(onAnswer = onAnswer, onDecline = onDecline)
        }
    }
}

/** مقبض واحد: اسحب للأعلى = رد، اسحب للأسفل = رفض. */
@Composable
private fun SwipeToAnswer(onAnswer: () -> Unit, onDecline: () -> Unit) {
    val maxDrag = with(LocalDensity.current) { 120.dp.toPx() }
    val threshold = maxDrag * 0.75f
    var dy by remember { mutableFloatStateOf(0f) }
    val answerCb by rememberUpdatedState(onAnswer)
    val declineCb by rememberUpdatedState(onDecline)

    val transition = rememberInfiniteTransition(label = "hint")
    val hint by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "hintAlpha"
    )

    val p = (dy / maxDrag).coerceIn(-1f, 1f) // سالب = للأعلى (رد)
    val green = Color(0xFF1E8E3E)
    val red = Color(0xFFD93025)
    val handleColor = if (p < 0) lerp(Color.White, green, -p) else lerp(Color.White, red, p)
    val iconTint = lerp(Color(0xFF202124), Color.White, abs(p))

    Box(Modifier.fillMaxWidth().height(340.dp)) {
        Column(Modifier.align(Alignment.TopCenter).alpha(hint), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.KeyboardArrowUp, null, tint = Color.White)
            Text(stringResource(R.string.answer), color = Color.White, fontSize = 15.sp)
        }
        Column(Modifier.align(Alignment.BottomCenter).alpha(hint), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.decline), color = Color.White, fontSize = 15.sp)
            Icon(Icons.Filled.KeyboardArrowDown, null, tint = Color.White)
        }
        Box(
            Modifier
                .align(Alignment.Center)
                .offset { IntOffset(0, dy.roundToInt()) }
                .size(76.dp)
                .clip(CircleShape)
                .background(handleColor)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            when {
                                dy <= -threshold -> answerCb()
                                dy >= threshold -> declineCb()
                                else -> dy = 0f
                            }
                        },
                        onDragCancel = { dy = 0f },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            dy = (dy + amount).coerceIn(-maxDrag, maxDrag)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (p > 0.25f) Icons.Filled.CallEnd else Icons.Filled.Call,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(34.dp)
            )
        }
    }
}

@Composable
private fun InCallContent(name: String, seconds: Int, photo: ImageBitmap, onEnd: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Image(
            bitmap = photo,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(Modifier.fillMaxSize().background(Color(0xCC000000)))
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))
            Text(
                name,
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "%02d:%02d".format(seconds / 60, seconds % 60),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 18.sp
            )
            Spacer(Modifier.weight(1f))

            val buttons: List<Pair<ImageVector, String>> = listOf(
                Icons.Filled.MicOff to stringResource(R.string.btn_mute),
                Icons.Filled.Dialpad to stringResource(R.string.btn_keypad),
                Icons.AutoMirrored.Filled.VolumeUp to stringResource(R.string.btn_speaker),
                Icons.Filled.Add to stringResource(R.string.btn_add),
                Icons.Filled.Videocam to stringResource(R.string.btn_video),
                Icons.Filled.Pause to stringResource(R.string.btn_hold)
            )
            buttons.chunked(3).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { (icon, label) -> CallToggle(icon, label) }
                }
            }

            Spacer(Modifier.height(24.dp))
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFD93025))
                    .clickable(onClick = onEnd),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.CallEnd, stringResource(R.string.end_call), tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CallToggle(icon: ImageVector, label: String) {
    var on by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(if (on) Color.White else Color.White.copy(alpha = 0.16f))
                .clickable { on = !on },
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = if (on) Color.Black else Color.White)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
    }
}
