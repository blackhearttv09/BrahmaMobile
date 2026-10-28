package com.brahma.mobile.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.brahma.mobile.BrahmaViewModel
import com.brahma.mobile.automation.BrahmaAccessibilityService

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = BColors.TextHi, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun ToggleRow(label: String, hint: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = BColors.TextHi, fontSize = 15.sp)
            Text(hint, color = BColors.TextLo, fontSize = 12.sp)
        }
        Switch(
            checked = value, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = BColors.Accent2, checkedThumbColor = Color.White,
                uncheckedTrackColor = Color.White.copy(alpha = 0.15f), uncheckedThumbColor = Color.White.copy(alpha = 0.7f)
            )
        )
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ClayButton(
        onClick = onClick, modifier = modifier,
        color = if (selected) BColors.Accent else Color(0xFF3A3F63),
        shape = RoundedCornerShape(18.dp)
    ) { Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

private fun isNotifAccessOn(ctx: Context) =
    NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

@Composable
fun SettingsScreen(vm: BrahmaViewModel) {
    val ctx = LocalContext.current
    val prefs = vm.prefs
    var gemini by remember { mutableStateOf(prefs.geminiKey) }
    var geminiModel by remember { mutableStateOf(prefs.geminiModel) }
    var orKey by remember { mutableStateOf(prefs.openrouterKey) }
    var orModel by remember { mutableStateOf(prefs.openrouterModel) }
    var primary by remember { mutableStateOf(prefs.primary) }
    var lang by remember { mutableStateOf(prefs.language) }
    var speak by remember { mutableStateOf(prefs.speakReplies) }
    var ask by remember { mutableStateOf(prefs.askConfirm) }
    var facts by remember { mutableStateOf(vm.memory.all()) }
    var rules by remember { mutableStateOf(vm.memory.rules()) }
    var saved by remember { mutableStateOf(false) }

    // Re-check permission state whenever the user comes back from Android settings.
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Text(tr("Settings", "সেটিংস"), color = BColors.TextHi, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)

        // ---------------- AI providers ----------------
        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle(tr("AI brain", "এআই ব্রেইন"))
            OutlinedTextField(
                gemini, { gemini = it; saved = false }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Gemini API key") }, visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(18.dp), colors = glassFieldColors()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                geminiModel, { geminiModel = it; saved = false }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Gemini model") }, shape = RoundedCornerShape(18.dp), colors = glassFieldColors()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                orKey, { orKey = it; saved = false }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("OpenRouter API key (fallback)") }, visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(18.dp), colors = glassFieldColors()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                orModel, { orModel = it; saved = false }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("OpenRouter model") }, shape = RoundedCornerShape(18.dp), colors = glassFieldColors()
            )
            Spacer(Modifier.height(12.dp))
            Text(tr("Try first:", "আগে কোনটা ব্যবহার হবে:"), color = BColors.TextLo, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("Gemini", primary == "gemini", { primary = "gemini"; saved = false })
                Chip("OpenRouter", primary == "openrouter", { primary = "openrouter"; saved = false })
            }
            Spacer(Modifier.height(14.dp))
            ClayButton(
                onClick = {
                    prefs.geminiKey = gemini; prefs.geminiModel = geminiModel
                    prefs.openrouterKey = orKey; prefs.openrouterModel = orModel
                    prefs.primary = primary
                    saved = true
                },
                color = BColors.Green, modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (saved) tr("Saved ✓", "সেভ হয়েছে ✓") else tr("Save keys", "কী সেভ করুন"), color = Color(0xFF06231A), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                tr("Keys are stored encrypted on this phone only.", "কী শুধু এই ফোনে এনক্রিপ্টেড অবস্থায় থাকে।"),
                color = BColors.TextLo, fontSize = 12.sp
            )
        }

        // ---------------- Voice & behavior ----------------
        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle(tr("Voice & behavior", "ভয়েস ও আচরণ"))
            Text(tr("Language", "ভাষা"), color = BColors.TextLo, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("বাংলা", lang == "bn-BD", { lang = "bn-BD"; vm.setLanguage(lang) })
                Chip("English", lang == "en-US", { lang = "en-US"; vm.setLanguage(lang) })
                Chip("हिन्दी", lang == "hi-IN", { lang = "hi-IN"; vm.setLanguage(lang) })
            }
            Spacer(Modifier.height(10.dp))
            ToggleRow(tr("Speak replies", "উত্তর পড়ে শোনাও"), tr("Read answers aloud", "উত্তর জোরে পড়বে"), speak) { speak = it; prefs.speakReplies = it }
            ToggleRow(
                tr("Ask before sensitive actions", "সংবেদনশীল কাজের আগে জিজ্ঞেস করো"),
                tr("Calls, SMS, WhatsApp need your OK (recommended)", "কল, এসএমএস, হোয়াটসঅ্যাপে আপনার অনুমতি লাগবে (প্রস্তাবিত)"),
                ask
            ) { ask = it; prefs.askConfirm = it }
        }

        // ---------------- Permission center ----------------
        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle(tr("Phone control power", "ফোন কন্ট্রোল পাওয়ার"))
            key(tick) {
                fun has(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
                val accOn = BrahmaAccessibilityService.instance != null
                val notifOn = isNotifAccessOn(ctx)

                PermRow(
                    tr("Screen control (Accessibility)", "স্ক্রিন কন্ট্রোল (Accessibility)"),
                    tr("Read screen, tap, type, scroll, press back/home", "স্ক্রিন পড়া, ট্যাপ, টাইপ, স্ক্রল, ব্যাক/হোম"),
                    accOn
                ) { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                PermRow(
                    tr("Notification access", "নোটিফিকেশন অ্যাক্সেস"),
                    tr("Read & summarize notifications", "নোটিফিকেশন পড়া ও সারাংশ"),
                    notifOn
                ) { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

                val runtime = listOf(
                    Triple(tr("Microphone", "মাইক্রোফোন"), tr("Voice commands", "ভয়েস কমান্ড"), arrayOf(Manifest.permission.RECORD_AUDIO)),
                    Triple(tr("Contacts", "কন্টাক্টস"), tr("Call/SMS by name", "নাম দিয়ে কল/এসএমএস"), arrayOf(Manifest.permission.READ_CONTACTS)),
                    Triple(tr("Phone calls", "ফোন কল"), tr("Place calls directly", "সরাসরি কল করা"), arrayOf(Manifest.permission.CALL_PHONE)),
                    Triple(tr("SMS", "এসএমএস"), tr("Send text messages", "মেসেজ পাঠানো"), arrayOf(Manifest.permission.SEND_SMS)),
                    Triple(tr("Alarms", "অ্যালার্ম"), tr("Set alarms & timers silently", "অ্যালার্ম ও টাইমার সেট"), arrayOf(Manifest.permission.SET_ALARM))
                ).toMutableList()
                if (Build.VERSION.SDK_INT >= 33) {
                    runtime.add(Triple(tr("Notifications", "নোটিফিকেশন"), tr("Show Brahma alerts", "ব্রহ্মের অ্যালার্ট"), arrayOf(Manifest.permission.POST_NOTIFICATIONS)))
                }
                runtime.forEach { (title, hint, perms) ->
                    PermRow(title, hint, perms.all { has(it) }) { permLauncher.launch(perms) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                tr(
                    "Sideloaded app? Android 13+ may grey out Accessibility. Open App info → ⋮ → \"Allow restricted settings\", then enable it.",
                    "সাইডলোড করা অ্যাপে Android 13+ এ Accessibility ধূসর থাকতে পারে। App info → ⋮ → \"Allow restricted settings\" চাপুন, তারপর চালু করুন।"
                ),
                color = BColors.TextLo, fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            ClayButton(
                onClick = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${ctx.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                color = Color(0xFF3A3F63), modifier = Modifier.fillMaxWidth()
            ) { Text(tr("Open App info", "অ্যাপ ইনফো খুলুন"), color = Color.White) }
        }

        // ---------------- Memory ----------------
        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle(tr("Memory", "মেমোরি"))
            if (facts.isEmpty() && rules.isEmpty()) {
                Text(tr("Nothing remembered yet.", "এখনো কিছু মনে রাখা হয়নি।"), color = BColors.TextLo, fontSize = 13.sp)
            }
            facts.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("$k: $v", color = BColors.TextHi, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    ClayButton(
                        onClick = { vm.memory.remove(k); facts = vm.memory.all() },
                        color = Color(0xFF4A2A44), shape = RoundedCornerShape(14.dp)
                    ) { Icon(Icons.Rounded.Close, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                }
                Spacer(Modifier.height(6.dp))
            }
            rules.forEach { Text("📌 $it", color = BColors.Accent2, fontSize = 13.sp) }
            Spacer(Modifier.height(10.dp))
            ClayButton(
                onClick = { vm.memory.clear(); facts = vm.memory.all(); rules = vm.memory.rules() },
                color = BColors.Pink, modifier = Modifier.fillMaxWidth()
            ) { Text(tr("Forget everything", "সব ভুলে যাও"), color = Color.White, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(8.dp))
            ClayButton(
                onClick = { vm.clearChat() },
                color = Color(0xFF3A3F63), modifier = Modifier.fillMaxWidth()
            ) { Text(tr("Clear chat", "চ্যাট মুছুন"), color = Color.White) }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun PermRow(title: String, hint: String, ok: Boolean, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline, null,
            tint = if (ok) BColors.Green else BColors.Pink, modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = BColors.TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(hint, color = BColors.TextLo, fontSize = 12.sp)
        }
        if (!ok) {
            ClayButton(onClick = onFix, color = BColors.Accent, shape = RoundedCornerShape(16.dp)) {
                Text(tr("Enable", "চালু"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
