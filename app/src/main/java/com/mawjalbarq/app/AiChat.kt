package com.mawjalbarq.app

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
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
@Composable
fun AiChatScreen(onBack: () -> Unit, onNeedLogin: () -> Unit) {
    val ctx = LocalContext.current
    val aiConfig by AiChatConfigStore.configFlow(ctx).collectAsState(initial = AiChatConfig())
    var showConfigScreen by remember { mutableStateOf(false) }

    if (showConfigScreen) {
        AiConfigScreen(
            current = aiConfig,
            onBack = { showConfigScreen = false }
        )
        return
    }

    val scope = rememberCoroutineScope()
    val msgs = ChatState.msgs
    var models by ChatState::models
    var model by ChatState::model
    var menu by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var busy by ChatState::busy
    val list = rememberLazyListState()
    val loggedIn = App.token != null
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val imeVisible = imeBottom > 0
    val welcomeText = "أهلاً بك! أنا المساعد الذكي لموج البرق (Mawj AI). كيف يمكنني مساعدتك اليوم؟ يمكنك تخصيص شخصيتي ومستوى ذكائي من زر الإعدادات في الأعلى."

    LaunchedEffect(Unit) {
        if (msgs.isEmpty() && loggedIn) {
            msgs.add(ChatMsg("assistant", welcomeText))
        }
    }

    LaunchedEffect(loggedIn) {
        if (!loggedIn) return@LaunchedEffect
        val (c, t) = api("POST", "/functions/v1/ai-chat", "{\"action\":\"models\"}")
        if (c == 200) try {
            val a = JSONObject(t).getJSONArray("models")
            val l = (0 until a.length()).map { a.getString(it) }
            if (l.isNotEmpty()) {
                models = l
                if (model !in l) model = l.first()
            }
        } catch (_: Exception) {}
    }

    androidx.activity.compose.BackHandler(onBack = onBack)

    LaunchedEffect(msgs.size, busy, imeVisible) {
        if (msgs.isNotEmpty()) {
            val target = (msgs.size - 1) + (if (busy) 1 else 0)
            list.animateScrollToItem(target.coerceAtLeast(0))
        }
    }

    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = list.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            layoutInfo.totalItemsCount == 0 || lastVisible >= layoutInfo.totalItemsCount - 2
        }
    }

    fun fetchReply() {
        busy = true
        val activeConfig = aiConfig
        val effectivePrompt = activeConfig.effectiveSystemPrompt()
        scope.launch {
            val arr = JSONArray()
            arr.put(
                JSONObject()
                    .put("role", "system")
                    .put("content", effectivePrompt)
            )
            // Ensure active persona & intelligence directives are honored even if upstream edge filters role=system
            arr.put(
                JSONObject()
                    .put("role", "user")
                    .put("content", "[تعليمات الشخصية ومستوى الذكاء للمحادثة]:\n$effectivePrompt")
            )
            arr.put(
                JSONObject()
                    .put("role", "assistant")
                    .put("content", "مفهوم تماماً، سألتزم بهذه الشخصية ومستوى الذكاء (${activeConfig.intelligence.title}) في جميع ردودي القادمة.")
            )
            msgs.takeLast(16).forEach { arr.put(JSONObject().put("role", it.role).put("content", it.text)) }
            val payload = JSONObject()
                .put("model", model)
                .put("system_prompt", effectivePrompt)
                .put("temperature", activeConfig.intelligence.temperature.toDouble())
                .put("intelligence", activeConfig.intelligence.name)
                .put("messages", arr)
                .toString()
            val (c, t) = api("POST", "/functions/v1/ai-chat", payload)
            val reply = if (c == 200) try {
                JSONObject(t).optString("reply").replace(Regex("(?s)<think>.*?</think>"), "").trim()
            } catch (_: Exception) { "" } else ""
            val finalReply = if (reply.isNotBlank()) reply else when (c) {
                429, 502 -> "النموذج الذكي مشغول حالياً أو تم استنفاد الحد المؤقت، جرب نموذج آخر من القائمة في الأعلى أو حاول بعد لحظات."
                else -> "عذراً، حدث خطأ في الاتصال بالخادم الذكي. تأكد من اتصالك بالإنترنت وحاول مرة أخرى."
            }
            msgs.add(ChatMsg("assistant", finalReply))
            busy = false
        }
    }

    fun send(txtArg: String = input) {
        val txt = txtArg.trim()
        if (txt.isEmpty() || busy) return
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (txtArg == input) input = ""
        msgs.add(ChatMsg("user", txt))
        fetchReply()
    }

    fun regenerate() {
        if (busy || msgs.size < 2) return
        if (msgs.lastOrNull()?.role == "assistant") {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            msgs.removeAt(msgs.lastIndex)
            fetchReply()
        }
    }

    fun clearChat() {
        if (busy) return
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        msgs.clear()
        if (loggedIn) msgs.add(ChatMsg("assistant", welcomeText))
    }

    val suggestions = listOf(
        "كيف أصنع فيديو مميز في موج البرق؟",
        "اقترح علي أفكار محتوى إبداعي",
        "كيف أزيد عدد المتابعين والتفاعل؟",
        "ما هي مميزات منصة موج البرق؟"
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            Surface(
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(
                            WindowInsets.statusBars.union(
                                WindowInsets.displayCutout.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                            )
                        )
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                keyboard?.hide()
                                onBack()
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("ai_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.SmartToy,
                                    null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    "المساعد الذكي",
                                    fontFamily = Cairo,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${aiConfig.preset.title} • ${aiConfig.intelligence.title}",
                                    fontFamily = Tajawal,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                keyboard?.hide()
                                showConfigScreen = true
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("ai_prompt_config_button")
                        ) {
                            Icon(
                                Icons.Filled.Tune,
                                contentDescription = "تخصيص شخصية وذكاء المساعد",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (loggedIn && msgs.size > 1) {
                            IconButton(
                                onClick = { clearChat() },
                                enabled = !busy,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("ai_clear_button")
                            ) {
                                Icon(
                                    Icons.Filled.DeleteSweep,
                                    "مسح المحادثة",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Box {
                            OutlinedButton(
                                onClick = { menu = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("ai_model_picker")
                            ) {
                                Text(model.substringAfter('/'), fontSize = 11.sp, maxLines = 1, fontFamily = Cairo)
                                Spacer(Modifier.width(2.dp))
                                Icon(Icons.Filled.ArrowDropDown, "اختيار النموذج", modifier = Modifier.size(16.dp))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                models.forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(m, fontSize = 12.sp, fontFamily = Cairo) },
                                        onClick = { model = m; menu = false }
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                }
            }
        }
    ) { innerPadding ->
        // Consume Scaffold navigationBars padding first so Modifier.imePadding() smoothly tracks the keyboard with zero gap
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
        ) {
            if (!loggedIn) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Filled.SmartToy, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "سجّل الدخول للبدء في المحادثة مع المساعد الذكي",
                        fontFamily = Cairo,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { onBack(); onNeedLogin() },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("ai_login_button")
                    ) {
                        Text("تسجيل الدخول", fontFamily = Cairo)
                    }
                }
            } else {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Column(Modifier.fillMaxSize()) {
                        AnimatedVisibility(visible = msgs.size <= 2 && !imeVisible) {
                            LazyRow(
                                Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(suggestions) { s ->
                                    SuggestionChip(
                                        onClick = { send(s) },
                                        label = {
                                            Text(
                                                s,
                                                fontSize = 11.5.sp,
                                                fontFamily = Cairo,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.testTag("ai_suggestion_chip")
                                    )
                                }
                            }
                        }

                        LazyColumn(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            state = list,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(msgs, key = { _, m -> m.id }) { idx, m ->
                                val mine = m.role == "user"
                                val isLastAssistant = !mine && idx == msgs.lastIndex && idx > 0
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (mine) Arrangement.Start else Arrangement.End
                                ) {
                                    if (!mine) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.padding(end = 6.dp).size(30.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Filled.SmartToy,
                                                    null,
                                                    modifier = Modifier.size(17.dp),
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }
                                    Column(horizontalAlignment = if (mine) Alignment.Start else Alignment.End) {
                                        Box(
                                            Modifier
                                                .widthIn(max = 310.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                                .then(
                                                    if (!mine) Modifier.border(
                                                        0.75.dp,
                                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                                        RoundedCornerShape(16.dp)
                                                    ) else Modifier
                                                )
                                                .padding(horizontal = 13.dp, vertical = 10.dp)
                                        ) {
                                            SelectionContainer {
                                                Text(
                                                    text = m.text,
                                                    fontFamily = if (mine) Cairo else Tajawal,
                                                    fontSize = 14.sp,
                                                    lineHeight = 21.sp,
                                                    color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                        if (!mine) {
                                            Row(
                                                Modifier.padding(top = 4.dp, end = 4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    Modifier
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .clickable {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            clipboard.setText(AnnotatedString(m.text))
                                                            Toast.makeText(ctx, "تم نسخ الرد", Toast.LENGTH_SHORT).show()
                                                        }
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                        .testTag("ai_copy_${m.id}"),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Filled.ContentCopy,
                                                        "نسخ",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Text(
                                                        "نسخ",
                                                        fontFamily = Cairo,
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (isLastAssistant) {
                                                    Row(
                                                        Modifier
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .clickable(enabled = !busy) { regenerate() }
                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                            .testTag("ai_regenerate_button"),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Filled.Refresh,
                                                            "إعادة التوليد",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                        Text(
                                                            "إعادة التوليد",
                                                            fontFamily = Cairo,
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (busy) {
                                item {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.padding(4.dp)
                                        ) {
                                            Row(
                                                Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                                Text(
                                                    "المساعد الذكي يفكر...",
                                                    fontFamily = Cairo,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isAtBottom && msgs.isNotEmpty(),
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp)
                    ) {
                        SmallFloatingActionButton(
                            onClick = {
                                scope.launch {
                                    val target = (msgs.size - 1) + (if (busy) 1 else 0)
                                    list.animateScrollToItem(target.coerceAtLeast(0))
                                }
                            },
                            shape = CircleShape,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("ai_scroll_bottom_button")
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, "الانتقال للأسفل")
                        }
                    }
                }

                Surface(
                    tonalElevation = 4.dp,
                    shadowElevation = 6.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .widthIn(max = 720.dp)
                                .align(Alignment.CenterHorizontally)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = input,
                                onValueChange = { input = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ai_chat_input"),
                                placeholder = {
                                    Text(
                                        "اكتب رسالتك هنا...",
                                        fontFamily = Cairo,
                                        fontSize = 13.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                minLines = 1,
                                maxLines = 4,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Send
                                ),
                                keyboardActions = KeyboardActions(
                                    onSend = { send() }
                                ),
                                trailingIcon = if (input.isNotEmpty()) {
                                    {
                                        IconButton(
                                            onClick = { input = "" },
                                            modifier = Modifier.testTag("ai_clear_input_button")
                                        ) {
                                            Icon(
                                                Icons.Filled.Close,
                                                contentDescription = "مسح النص",
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else null,
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                )
                            )

                            Button(
                                onClick = { send() },
                                enabled = !busy && input.isNotBlank(),
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                modifier = Modifier
                                    .heightIn(min = 50.dp)
                                    .testTag("ai_send_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "إرسال",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "إرسال",
                                    fontFamily = Cairo,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
