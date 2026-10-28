package com.brahma.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brahma.mobile.ui.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BrahmaTheme {
                val vm: BrahmaViewModel = viewModel()
                BrahmaRoot(vm)
            }
        }
    }
}

private enum class Tab(val en: String, val bn: String, val icon: ImageVector) {
    Home("Home", "হোম", Icons.Rounded.AutoAwesome),
    Chat("Chat", "চ্যাট", Icons.Rounded.ChatBubble),
    Settings("Settings", "সেটিংস", Icons.Rounded.Tune)
}

@Composable
private fun BrahmaRoot(vm: BrahmaViewModel) {
    var tab by remember { mutableStateOf(Tab.Home) }
    val confirm by vm.confirm.collectAsState()

    AuroraBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    Tab.Home -> HomeScreen(vm)
                    Tab.Chat -> ChatScreen(vm)
                    Tab.Settings -> SettingsScreen(vm)
                }
            }
            GlassNavBar(tab) { tab = it }
        }
    }

    confirm?.let { req ->
        Dialog(onDismissRequest = { vm.answer(false) }) {
            GlassCard(Modifier.fillMaxWidth(), tint = BColors.Accent, padding = 20) {
                Text(tr("Confirm action", "অনুমতি দিন"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = BColors.TextHi)
                Spacer(Modifier.height(10.dp))
                Text(req.text, color = BColors.TextLo, fontSize = 14.sp)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    ClayButton(onClick = { vm.answer(false) }, color = Color(0xFF5B5F7A), modifier = Modifier.weight(1f)) {
                        Text(tr("Cancel", "বাতিল"), color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    ClayButton(onClick = { vm.answer(true) }, color = BColors.Green, modifier = Modifier.weight(1f)) {
                        Text(tr("Allow", "অনুমতি"), color = Color(0xFF06231A), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassNavBar(selected: Tab, onSelect: (Tab) -> Unit) {
    GlassCard(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(32.dp), padding = 8
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Tab.values().forEach { t ->
                val on = t == selected
                val itemMod = Modifier
                    .weight(1f)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(t) }
                if (on) {
                    ClayButton(onClick = { onSelect(t) }, modifier = itemMod.padding(horizontal = 4.dp), color = BColors.Accent, shape = RoundedCornerShape(24.dp)) {
                        Icon(t.icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Text(tr(t.en, t.bn), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Row(itemMod.padding(vertical = 13.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(t.icon, null, tint = BColors.TextLo, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
