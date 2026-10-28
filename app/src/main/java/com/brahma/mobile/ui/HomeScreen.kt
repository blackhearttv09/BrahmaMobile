package com.brahma.mobile.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.brahma.mobile.BrahmaViewModel

private data class Quick(val emoji: String, val en: String, val bn: String, val prompt: String)

private val QUICK = listOf(
    Quick("🔦", "Flashlight", "টর্চ", "Turn the flashlight on"),
    Quick("🔋", "Battery", "ব্যাটারি", "What is my battery level?"),
    Quick("🌤", "Weather", "আবহাওয়া", "What's the weather today?"),
    Quick("📱", "Read screen", "স্ক্রিন পড়ো", "Read my screen and tell me what is on it"),
    Quick("🔔", "Notifications", "নোটিফিকেশন", "Summarize my recent notifications"),
    Quick("⏱", "Timer 5m", "৫ মিনিট টাইমার", "Set a timer for 5 minutes")
)

@Composable
fun HomeScreen(vm: BrahmaViewModel) {
    val ctx = LocalContext.current
    val busy by vm.busy.collectAsState()
    val listening by vm.voice.listening.collectAsState()
    val speaking by vm.voice.speaking.collectAsState()
    val level by vm.voice.level.collectAsState()
    val status by vm.status.collectAsState()
    val msgs by vm.messages.collectAsState()

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.listen()
    }
    val state = when {
        listening -> OrbState.Listening
        busy -> OrbState.Thinking
        speaking -> OrbState.Speaking
        else -> OrbState.Idle
    }
    val last = msgs.lastOrNull { it.role == "assistant" || it.role == "error" }

    val caption = when (state) {
        OrbState.Listening -> tr("Listening…", "শুনছি…")
        OrbState.Thinking -> if (status.isNotBlank()) status.take(60) else tr("Thinking…", "ভাবছি…")
        OrbState.Speaking -> tr("Speaking – tap to interrupt", "বলছি – থামাতে ট্যাপ করুন")
        OrbState.Idle -> tr("Tap the orb and speak", "অরবে ট্যাপ করে বলুন")
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(14.dp))
        Text(
            tr("Brahma", "ব্রহ্ম"),
            style = TextStyle(
                brush = Brush.linearGradient(listOf(BColors.Accent2, BColors.Accent, BColors.Pink)),
                fontSize = 34.sp, fontWeight = FontWeight.ExtraBold
            )
        )
        Text(tr("Your voice-first phone assistant", "আপনার ভয়েস-ফার্স্ট ফোন সহকারী"), color = BColors.TextLo, fontSize = 13.sp)

        Spacer(Modifier.weight(1f))
        ClayOrb(state, level) {
            when {
                listening -> vm.voice.stopListening()
                speaking -> vm.voice.stopSpeaking()
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> vm.listen()
                else -> micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(caption, color = BColors.TextLo, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))

        if (busy) {
            ClayButton(onClick = { vm.stop() }, color = BColors.Pink) {
                Icon(Icons.Rounded.Stop, null, tint = Color.White)
                Text(tr("Stop", "থামাও"), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
        }

        if (last != null) {
            GlassCard(
                Modifier.fillMaxWidth().heightIn(max = 170.dp),
                tint = if (last.role == "error") BColors.Pink else Color.White
            ) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(last.text, color = BColors.TextHi, fontSize = 15.sp, lineHeight = 21.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
            items(QUICK) { q ->
                ClayButton(
                    onClick = { vm.send(q.prompt) },
                    color = BColors.Accent,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(q.emoji, fontSize = 16.sp)
                    Text(tr(q.en, q.bn), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}
