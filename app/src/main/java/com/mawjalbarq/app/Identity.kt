package com.mawjalbarq.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray

/** Unique immutable public IDs (server-generated, shown instead of an @handle). */
object Pids {
    val map = mutableStateMapOf<String, String>()
    private val asked = HashSet<String>()
    suspend fun ensure(userId: String) {
        if (userId.isBlank() || map.containsKey(userId) || !asked.add(userId)) return
        try {
            val (c, t) = api("GET", "/rest/v1/profiles?id=eq.$userId&select=public_id")
            if (c == 200) JSONArray(t).optJSONObject(0)?.optString("public_id")?.takeIf { it.isNotBlank() }?.let { map[userId] = it } else asked.remove(userId)
        } catch (_: Exception) { asked.remove(userId) }
    }
}

@Composable
fun pidOf(userId: String): String {
    LaunchedEffect(userId) { Pids.ensure(userId) }
    return Pids.map[userId] ?: if (userId == App.userId && App.publicId.isNotBlank()) App.publicId else ""
}

/** Accent picker. compact = one scrolling row of swatches; full = grid with names and a live preview. */
@Composable
fun AccentPicker(prefs: ThemePrefs, compact: Boolean, modifier: Modifier = Modifier) {
    @Composable fun Swatch(i: Int, a: Accent, size: Int) {
        val on = i == prefs.index
        val sc by animateFloatAsState(if (on) 1.12f else 1f, label = "sw")
        Box(
            Modifier.size(size.dp).scale(sc).clip(CircleShape)
                .background(Brush.linearGradient(listOf(a.main, a.second)))
                .border(if (on) 3.dp else 1.dp, if (on) Color.White else Color(0x55FFFFFF), CircleShape)
                .clickable { prefs.set(i) },
            contentAlignment = Alignment.Center
        ) { if (on) Icon(Icons.Filled.Check, a.name, tint = Color.Black, modifier = Modifier.size((size / 2).dp)) }
    }
    if (compact) {
        LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            itemsIndexed(Accents) { i, a -> Swatch(i, a, 26) }
        }
        return
    }
    val cur = Accents[prefs.index]
    Column(modifier.fillMaxWidth()) {
        Accents.chunked(4).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEachIndexed { k, a ->
                    val i = r * 4 + k
                    Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Swatch(i, a, 48)
                        Spacer(Modifier.height(4.dp))
                        Text(a.name, color = if (i == prefs.index) Color.White else Color(0xFF8A8F9C), fontSize = 11.sp, maxLines = 1)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.width(72.dp)) }
            }
        }
        Spacer(Modifier.height(10.dp))
        // live preview of the chosen color
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF141A24)).padding(14.dp)) {
            Text("معاينة اللون: " + cur.name, color = Color.White, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(cur.main, cur.second))))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("زر رئيسي", color = Color.Black, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(50)).background(cur.main).padding(horizontal = 16.dp, vertical = 8.dp))
                Text("زر ثانوي", color = cur.main, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(50)).border(1.5.dp, cur.main, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 7.dp))
                Icon(Icons.Filled.Favorite, null, tint = cur.second, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("اللون يُحفظ على هذا الجهاز فقط", color = Color(0xFF8A8F9C), fontSize = 11.sp)
    }
}

/** Settings sheet reached from the gear next to the account title. Holds the accent color. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(prefs: ThemePrefs, onClose: () -> Unit) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onClose, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp).navigationBarsPadding()) {
            Text("الإعدادات", color = Color.White, fontSize = 20.sp)
            Spacer(Modifier.height(14.dp))
            Text("لون التطبيق", color = Color.White, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            AccentPicker(prefs, compact = false)
        }
    }
}
