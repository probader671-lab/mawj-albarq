package com.mawjalbarq.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private class ChatMsg(val role: String, val text: String, val id: Long = System.nanoTime())

/** Chat state lives here, not in the screen, so closing and reopening the chat (or a recomposition) never wipes the conversation. */
private object ChatState {
    val msgs = mutableStateListOf<ChatMsg>()
    var model by mutableStateOf("openai/gpt-oss-20b")
    var models by mutableStateOf(listOf("openai/gpt-oss-20b"))
    var busy by mutableStateOf(false)
}

/** AI chat via the ai-chat Edge Function (Groq). The key never leaves the server. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(onBack: () -> Unit, onNeedLogin: () -> Unit) {
    val scope = rememberCoroutineScope()
    val msgs = ChatState.msgs
    var models by ChatState::models
    var model by ChatState::model
    var menu by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var busy by ChatState::busy
    val list = rememberLazyListState()
    val loggedIn = App.token != null

    LaunchedEffect(loggedIn) {
        if (!loggedIn) return@LaunchedEffect
        val (c, t) = api("POST", "/functions/v1/ai-chat", "{\"action\":\"models\"}")
        if (c == 200) try {
            val a = JSONObject(t).getJSONArray("models"); val l = (0 until a.length()).map { a.getString(it) }
            if (l.isNotEmpty()) { models = l; if (model !in l) model = l.first() }
        } catch (_: Exception) {}
    }
    androidx.activity.compose.BackHandler(onBack = onBack)
    LaunchedEffect(msgs.size, busy) { if (msgs.isNotEmpty()) list.scrollToItem(msgs.size - 1 + (if (busy) 1 else 0)) }

    fun send() {
        val txt = input.trim(); if (txt.isEmpty() || busy) return
        input = ""; msgs.add(ChatMsg("user", txt)); busy = true
        scope.launch {
            val arr = JSONArray(); msgs.takeLast(20).forEach { arr.put(JSONObject().put("role", it.role).put("content", it.text)) }
            val (c, t) = api("POST", "/functions/v1/ai-chat", JSONObject().put("model", model).put("messages", arr).toString())
            val reply = if (c == 200) try { JSONObject(t).optString("reply").replace(Regex("(?s)<think>.*?</think>"), "").trim() } catch (_: Exception) { "" } else ""
            msgs.add(ChatMsg("assistant", if (reply.isNotBlank()) reply else if (c == 429 || c == 502) "النموذج مشغول أو وصلت للحد المجاني، جرّب نموذج تاني أو بعد شوية." else "حصل خطأ، جرّب تاني."))
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent().changes.forEach { it.consume() } } } }
        .statusBarsPadding().imePadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
            Text("المساعد الذكي", fontFamily = Cairo, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Box {
                TextButton(onClick = { menu = true }) {
                    Text(model.substringAfter('/'), fontSize = 12.sp, maxLines = 1); Icon(Icons.Filled.ArrowDropDown, null)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    models.forEach { m -> DropdownMenuItem(text = { Text(m, fontSize = 13.sp) }, onClick = { model = m; menu = false }) }
                }
            }
        }
        HorizontalDivider()
        if (!loggedIn) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("سجّل الدخول عشان تستخدم المساعد", fontFamily = Cairo)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { onBack(); onNeedLogin() }) { Text("تسجيل الدخول", fontFamily = Cairo) }
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (msgs.isEmpty()) item { Text("اسأل أي حاجة. اختار النموذج من فوق.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Cairo, modifier = Modifier.padding(24.dp)) }
                items(msgs, key = { it.id }) { m ->
                    val mine = m.role == "user"
                    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterStart else Alignment.CenterEnd) {
                        Text(m.text, fontFamily = Cairo, fontSize = 14.sp,
                            color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(14.dp))
                                .background(if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant).padding(10.dp))
                    }
                }
                if (busy) item { CircularProgressIndicator(Modifier.size(22.dp).padding(2.dp), strokeWidth = 2.dp) }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("اكتب رسالتك", fontFamily = Cairo) }, maxLines = 4, shape = RoundedCornerShape(24.dp))
                IconButton(onClick = { send() }, enabled = !busy && input.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, "إرسال") }
            }
        }
    }
}
