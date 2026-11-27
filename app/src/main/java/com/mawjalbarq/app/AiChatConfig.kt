package com.mawjalbarq.app

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ShortText
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VideoCameraFront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.aiChatConfigDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ai_chat_config"
)

enum class AiIntelligenceLevel(
    val title: String,
    val subtitle: String,
    val directive: String,
    val temperature: Float,
    val depthBars: Int,
    val icon: ImageVector
) {
    QUICK(
        title = "سريع ومباشر",
        subtitle = "إجابات خفيفة وفورية بأقل قدر من الإطالة",
        directive = "كن سريعاً ومباشراً جداً. أعطِ الخلاصة فوراً في نقاط قصيرة بدون مقدمات طويلة.",
        temperature = 0.35f,
        depthBars = 1,
        icon = Icons.Filled.Bolt
    ),
    BALANCED(
        title = "متوازن",
        subtitle = "توازن مثالي بين الوضوح، الدقة، والأمثلة المفيدة",
        directive = "قدّم إجابات متوازنة تجمع بين الوضوح والتنظيم والأمثلة العملية المفيدة.",
        temperature = 0.6f,
        depthBars = 2,
        icon = Icons.Filled.Speed
    ),
    SMART(
        title = "ذكي ومتعمق",
        subtitle = "تحليل منطقي معمّق وخطوات منظمة وحلول دقيقة",
        directive = "فكّر بعمق ومنطقية خطوة بخطوة قبل الصياغة. حلّل الجوانب المهمة وقدّم خطوات دقيقة وحلولاً عملية متينة.",
        temperature = 0.5f,
        depthBars = 3,
        icon = Icons.Filled.Psychology
    ),
    GENIUS(
        title = "عبقري وإبداعي",
        subtitle = "أقصى درجات الاستنتاج والابتكار وتغطية التفاصيل",
        directive = "استخدم أعلى مستويات الذكاء والتحليل الاستراتيجي والإبداع. اقترح زوايا مبتكرة وتفاصيل احترافية متكاملة وتوقع الأسئلة التالية.",
        temperature = 0.78f,
        depthBars = 4,
        icon = Icons.Filled.AutoAwesome
    )
}

enum class AiPersonalityPreset(
    val title: String,
    val prompt: String,
    val icon: ImageVector
) {
    FRIENDLY_MAWJ(
        title = "ودود وشامل",
        prompt = "أنت المساعد الذكي الرسمي لتطبيق موج البرق (Mawj Albarq). تتحدث باللغة العربية بطلاقة وبأسلوب ودود وملهم. تساعد المستخدمين في الإجابة على الأسئلة العامة، النصائح اليومية، وإنتاج المحتوى.",
        icon = Icons.Filled.SmartToy
    ),
    CONTENT_CREATOR(
        title = "خبير محتوى",
        prompt = "أنت خبير استراتيجي في صناعة الفيديوهات القصيرة والطويلة على منصة موج البرق. ساعد المستخدم في كتابة خطافات جذب المشاهدين (Hooks)، السيناريوهات، العناوين القوية، وأفكار زيادة التفاعل.",
        icon = Icons.Filled.VideoCameraFront
    ),
    TECH_EXPERT(
        title = "خبير تقني",
        prompt = "أنت مهندس برمجيات وخبير تقني دقيق. تشرح المفاهيم التقنية والبرمجية بوضوح، وتقدّم خطوات عملية مرتبة وخالية من التعقيد غير الضروري.",
        icon = Icons.Filled.Code
    ),
    CREATIVE_WRITER(
        title = "أديب ومبدع",
        prompt = "أنت كاتب مبدع وملهم تجيد البلاغة العربية الحديثة. تصوغ الأفكار والقصص والخواطر بأسلوب أدبي جذاب ومؤثر.",
        icon = Icons.Filled.EditNote
    ),
    CONCISE_PRO(
        title = "مختصر واحترافي",
        prompt = "أنت مساعد تنفيذي شديد الاختصار والاحترافية. قدّم المعلومة المطلوبة مباشرة في نقاط واضحة دون حشو أو مجاملات.",
        icon = Icons.Filled.ShortText
    ),
    CUSTOM(
        title = "مخصص",
        prompt = "",
        icon = Icons.Filled.AutoAwesome
    )
}

data class AiChatConfig(
    val preset: AiPersonalityPreset = AiPersonalityPreset.FRIENDLY_MAWJ,
    val systemPrompt: String = AiPersonalityPreset.FRIENDLY_MAWJ.prompt,
    val intelligence: AiIntelligenceLevel = AiIntelligenceLevel.SMART
) {
    fun effectiveSystemPrompt(): String {
        val base = systemPrompt.trim().ifBlank { AiPersonalityPreset.FRIENDLY_MAWJ.prompt }
        return "$base\n\nمستوى الذكاء وأسلوب التفكير المطلوب (${intelligence.title}): ${intelligence.directive}"
    }
}

object AiChatConfigStore {
    private val KEY_PRESET = stringPreferencesKey("personality_preset")
    private val KEY_PROMPT = stringPreferencesKey("system_prompt")
    private val KEY_INTELLIGENCE = stringPreferencesKey("intelligence_level")

    fun configFlow(ctx: Context): Flow<AiChatConfig> =
        ctx.applicationContext.aiChatConfigDataStore.data.map { prefs ->
            val presetName = prefs[KEY_PRESET]
            val preset = AiPersonalityPreset.entries.firstOrNull { it.name == presetName }
                ?: AiPersonalityPreset.FRIENDLY_MAWJ
            val prompt = prefs[KEY_PROMPT]?.takeIf { it.isNotBlank() }
                ?: AiPersonalityPreset.FRIENDLY_MAWJ.prompt
            val intelName = prefs[KEY_INTELLIGENCE]
            val intel = AiIntelligenceLevel.entries.firstOrNull { it.name == intelName }
                ?: AiIntelligenceLevel.SMART
            AiChatConfig(preset = preset, systemPrompt = prompt, intelligence = intel)
        }

    suspend fun save(ctx: Context, config: AiChatConfig) {
        val cleaned = config.systemPrompt.trim().ifBlank { AiPersonalityPreset.FRIENDLY_MAWJ.prompt }
        ctx.applicationContext.aiChatConfigDataStore.edit { prefs ->
            prefs[KEY_PRESET] = config.preset.name
            prefs[KEY_PROMPT] = cleaned
            prefs[KEY_INTELLIGENCE] = config.intelligence.name
        }
    }

    suspend fun reset(ctx: Context) {
        save(ctx, AiChatConfig())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiConfigScreen(
    current: AiChatConfig,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var selectedPreset by remember(current) { mutableStateOf(current.preset) }
    var promptText by remember(current) { mutableStateOf(current.systemPrompt) }
    var selectedIntel by remember(current) { mutableStateOf(current.intelligence) }
    var saving by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

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
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("ai_config_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع للمحادثة")
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                "تخصيص ذكاء وشخصية المساعد",
                                fontFamily = Cairo,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "يُحفظ تلقائياً في الجهاز عبر DataStore",
                                fontFamily = Tajawal,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedPreset = AiPersonalityPreset.FRIENDLY_MAWJ
                                promptText = AiPersonalityPreset.FRIENDLY_MAWJ.prompt
                                selectedIntel = AiIntelligenceLevel.SMART
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("ai_config_reset_button")
                        ) {
                            Icon(
                                Icons.Filled.RestartAlt,
                                contentDescription = "استعادة الافتراضي",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                }
            }
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Active Status Banner Card
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                selectedIntel.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                "الشخصية: ${selectedPreset.title}",
                                fontFamily = Cairo,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Ink
                            )
                            Text(
                                "مستوى الذكاء: ${selectedIntel.title} • حرارة الإبداع: ${(selectedIntel.temperature * 100).toInt()}%",
                                fontFamily = Tajawal,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Intelligence Level Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "مستوى الذكاء وعمق التحليل",
                        fontFamily = Cairo,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Ink
                    )
                    AiIntelligenceLevel.entries.forEach { level ->
                        val isSelected = selectedIntel == level
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.75.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedIntel = level
                                }
                                .testTag("ai_intel_${level.name.lowercase()}")
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    level.icon,
                                    contentDescription = level.title,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        level.title,
                                        fontFamily = Cairo,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Ink
                                    )
                                    Text(
                                        level.subtitle,
                                        fontFamily = Tajawal,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // Depth meter bars
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    for (bar in 1..4) {
                                        Box(
                                            Modifier
                                                .width(4.dp)
                                                .height((6 + bar * 4).dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(
                                                    if (bar <= level.depthBars) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                                )
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = "محدد",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Personality Presets Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "شخصية المساعد (قوالب سريعة)",
                        fontFamily = Cairo,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Ink
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AiPersonalityPreset.entries.filter { it != AiPersonalityPreset.CUSTOM }.forEach { preset ->
                            val isSelected = selectedPreset == preset
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedPreset = preset
                                    promptText = preset.prompt
                                },
                                leadingIcon = {
                                    Icon(
                                        preset.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        preset.title,
                                        fontFamily = Cairo,
                                        fontSize = 12.5.sp
                                    )
                                },
                                modifier = Modifier
                                    .heightIn(min = 42.dp)
                                    .testTag("ai_preset_${preset.name.lowercase()}")
                            )
                        }
                    }
                }

                // Custom System Prompt Editor
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "توجيه النظام المخصص (System Prompt)",
                        fontFamily = Cairo,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Ink
                    )
                    Text(
                        "حدد بدقة كيف يتحدث المساعد، مجال خبرته، والقواعد التي يلتزم بها في كل رد:",
                        fontFamily = Tajawal,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = promptText,
                        onValueChange = {
                            promptText = it
                            val match = AiPersonalityPreset.entries.firstOrNull { p ->
                                p != AiPersonalityPreset.CUSTOM && p.prompt.trim() == it.trim()
                            }
                            selectedPreset = match ?: AiPersonalityPreset.CUSTOM
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_system_prompt_input"),
                        placeholder = {
                            Text(
                                "مثال: أنت خبير تسويق ومحتوى عربي، استخدم أمثلة قصيرة ولغة واضحة...",
                                fontFamily = Tajawal,
                                fontSize = 13.sp
                            )
                        },
                        minLines = 4,
                        maxLines = 8,
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            // Anchored Bottom Save Bar
            Surface(
                tonalElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onBack,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .heightIn(min = 50.dp)
                                .testTag("ai_config_cancel_button")
                        ) {
                            Text("إلغاء", fontFamily = Cairo)
                        }
                        Button(
                            enabled = !saving && promptText.isNotBlank(),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 50.dp)
                                .testTag("ai_config_save_button"),
                            onClick = {
                                saving = true
                                scope.launch {
                                    AiChatConfigStore.save(
                                        ctx,
                                        AiChatConfig(
                                            preset = selectedPreset,
                                            systemPrompt = promptText.trim(),
                                            intelligence = selectedIntel
                                        )
                                    )
                                    saving = false
                                    Toast.makeText(ctx, "تم حفظ إعدادات الذكاء والشخصية", Toast.LENGTH_SHORT).show()
                                    onBack()
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (saving) "جاري الحفظ..." else "حفظ الإعدادات", fontFamily = Cairo, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
