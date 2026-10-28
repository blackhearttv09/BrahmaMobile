package com.brahma.mobile.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brahma.mobile.BrahmaViewModel
import com.brahma.mobile.UiMsg
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun ChatScreen(vm: BrahmaViewModel) {
    val ctx = LocalContext.current
    val msgs by vm.messages.collectAsState()
    val busy by vm.busy.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) vm.listen() }

    LaunchedEffect(msgs.size) {
        if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.lastIndex)
    }

    Column(Modifier.fillMaxSize()) {
        if (msgs.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                GlassCard(Modifier.padding(32.dp)) {
                    Text(
                        tr("Ask anything, or tell me to do something on your phone.", "যেকোনো প্রশ্ন করুন, বা ফোনে কিছু করতে বলুন।"),
                        color = BColors.TextLo, fontSize = 15.sp
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(), state = listState,
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(msgs, key = { it.id }) { Bubble(it) }
            }
        }

        Row(
            Modifier.fillMaxWidth().imePadding().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(tr("Message Brahma…", "ব্রহ্মকে বলুন…"), color = BColors.TextLo) },
                shape = RoundedCornerShape(26.dp),
                colors = glassFieldColors(),
                maxLines = 4
            )
            ClayButton(
                onClick = {
                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.listen()
                    else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                color = BColors.Pink, shape = RoundedCornerShape(22.dp)
            ) { Icon(Icons.Rounded.Mic, null, tint = Color.White) }
            if (busy) {
                ClayButton(onClick = { vm.stop() }, color = BColors.Pink, shape = RoundedCornerShape(22.dp)) {
                    Icon(Icons.Rounded.Stop, null, tint = Color.White)
                }
            } else {
                ClayButton(
                    onClick = { vm.send(input); input = "" },
                    color = BColors.Accent, shape = RoundedCornerShape(22.dp)
                ) { Icon(Icons.AutoMirrored.Rounded.Send, null, tint = Color.White) }
            }
        }
    }
}

@Composable
private fun Bubble(m: UiMsg) {
    when (m.role) {
        "user" -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ClayButton(onClick = {}, color = BColors.Accent, shape = RoundedCornerShape(24.dp), modifier = Modifier.widthIn(max = 300.dp)) {
                Text(m.text, color = Color.White, fontSize = 15.sp)
            }
        }
        "assistant" -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            GlassCard(Modifier.widthIn(max = 320.dp), shape = RoundedCornerShape(24.dp)) {
                SelectionContainer { Text(m.text, color = BColors.TextHi, fontSize = 15.sp, lineHeight = 21.sp) }
            }
        }
        "error" -> GlassCard(Modifier.fillMaxWidth(), tint = BColors.Pink, shape = RoundedCornerShape(20.dp)) {
            Text(m.text, color = BColors.TextHi, fontSize = 13.sp)
        }
        else -> Text(m.text.take(140), color = BColors.Accent2.copy(alpha = 0.85f), fontSize = 12.sp, fontStyle = FontStyle.Italic)
    }
}
