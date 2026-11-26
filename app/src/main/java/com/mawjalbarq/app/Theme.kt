package com.mawjalbarq.app
import android.content.Context
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

val Cairo = FontFamily(Font(R.font.cairo))
val Tajawal = FontFamily(Font(R.font.tajawal))

data class Accent(val name: String, val main: Color, val second: Color)
val Accents = listOf(
    Accent("برتقالي", Color(0xFFFF7A1A), Color(0xFF10B981)),
    Accent("موجة زرقاء", Color(0xFF2EA8FF), Color(0xFFFFD21F)),
    Accent("برق ذهبي", Color(0xFFFFD21F), Color(0xFF2EA8FF)),
    Accent("بنفسجي", Color(0xFFA066FF), Color(0xFFFF5CA8)),
    Accent("أخضر", Color(0xFF2BE38B), Color(0xFF2EA8FF)),
    Accent("قرمزي", Color(0xFFFF3B5C), Color(0xFF10B981)),
    Accent("سماوي", Color(0xFF06D4D4), Color(0xFFF59E0B)),
    Accent("وردي", Color(0xFFEC4899), Color(0xFFF59E0B)),
    Accent("أحمر", Color(0xFFFF4D5E), Color(0xFFFFD21F)),
)

class ThemePrefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("theme", Context.MODE_PRIVATE)
    var index by mutableStateOf(sp.getInt("accent2", 0).coerceIn(0, Accents.lastIndex))
        private set
    fun hasChoice() = sp.contains("accent2")
    fun set(i: Int, sync: Boolean = true) {
        index = i; sp.edit().putInt("accent2", i).apply()
        val u = App.userId
        if (sync && u != null) CoroutineScope(Dispatchers.IO).launch {
            val a = Accents[i]
            api("PATCH", "/rest/v1/profiles?id=eq.$u", org.json.JSONObject()
                .put("theme_accent_argb", a.main.toArgb().toLong() and 0xFFFFFFFFL)
                .put("theme_secondary_argb", a.second.toArgb().toLong() and 0xFFFFFFFFL).toString())
        }
    }
}

@Composable
fun MawjTheme(accent: Accent, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = accent.main, secondary = accent.second, tertiary = accent.second,
        background = Color(0xFF0E131B), surface = Color(0xFF141A24), surfaceVariant = Color(0xFF1D2634), outline = Color(0xFF313D50), onSurfaceVariant = Color(0xFFAAB4C4), primaryContainer = Color(0xFF18202C), onBackground = Color.White, onSurface = Color.White,
    )
    MaterialTheme(colorScheme = scheme, typography = Typography(
        bodyLarge = TextStyle(fontFamily = Tajawal), bodyMedium = TextStyle(fontFamily = Tajawal),
        titleMedium = TextStyle(fontFamily = Cairo), titleLarge = TextStyle(fontFamily = Cairo),
        labelSmall = TextStyle(fontFamily = Tajawal)), content = content)
}
