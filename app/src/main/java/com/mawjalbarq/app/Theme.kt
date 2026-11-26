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
    Accent("نيلي", Color(0xFF5B6CFF), Color(0xFF2BE38B)),
    Accent("ليموني", Color(0xFFB6F23A), Color(0xFFA066FF)),
    Accent("كرزي", Color(0xFFD81B60), Color(0xFFFFD21F)),
)

class ThemePrefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("theme", Context.MODE_PRIVATE)
    var index by mutableStateOf(sp.getInt("accent2", 0).coerceIn(0, Accents.lastIndex))
        private set
    var dark by mutableStateOf(sp.getBoolean("dark", true))
        private set
    init { Pal.dark = dark }
    fun setDark(b: Boolean) { dark = b; Pal.dark = b; sp.edit().putBoolean("dark", b).apply() }
    fun hasChoice() = sp.contains("accent2")
    // local only: the accent choice stays on this device and is never sent to the account
    fun set(i: Int) { index = i.coerceIn(0, Accents.lastIndex); sp.edit().putInt("accent2", index).apply() }
}

/** Palette state. Screens that must stay dark (clips, player, login) call DarkLock(). */
object Pal {
    var dark by mutableStateOf(true)
    var lock by mutableIntStateOf(0)
    val isDark get() = dark || lock > 0
}
val Ink: Color get() = if (Pal.isDark) Color.White else Color(0xFF111827)
val PageBg: Color get() = if (Pal.isDark) Color.Black else Color(0xFFF2F4F8)

@Composable
fun DarkLock() { DisposableEffect(Unit) { Pal.lock++; onDispose { Pal.lock-- } } }

@Composable
fun MawjTheme(accent: Accent, content: @Composable () -> Unit) {
    val dark = Pal.isDark
    val view = androidx.compose.ui.platform.LocalView.current
    SideEffect {
        (view.context as? android.app.Activity)?.window?.let { w ->
            androidx.core.view.WindowCompat.getInsetsController(w, view).isAppearanceLightStatusBars = !dark
        }
    }
    val scheme = if (!dark) lightColorScheme(
        primary = accent.main, secondary = accent.second, tertiary = accent.second, onPrimary = Color.Black,
        background = Color(0xFFF2F4F8), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFE6EAF2), outline = Color(0xFFC5CCD8),
        onSurfaceVariant = Color(0xFF596275), primaryContainer = Color(0xFFE8EDF7), onBackground = Color(0xFF111827), onSurface = Color(0xFF111827),
    ) else darkColorScheme(
        primary = accent.main, secondary = accent.second, tertiary = accent.second,
        background = Color(0xFF0E131B), surface = Color(0xFF141A24), surfaceVariant = Color(0xFF1D2634), outline = Color(0xFF313D50), onSurfaceVariant = Color(0xFFAAB4C4), primaryContainer = Color(0xFF18202C), onBackground = Color.White, onSurface = Color.White,
    )
    MaterialTheme(colorScheme = scheme, typography = Typography(
        bodyLarge = TextStyle(fontFamily = Tajawal), bodyMedium = TextStyle(fontFamily = Tajawal),
        titleMedium = TextStyle(fontFamily = Cairo), titleLarge = TextStyle(fontFamily = Cairo),
        labelSmall = TextStyle(fontFamily = Tajawal)), content = content)
}
