package com.mawjalbarq.app
import android.content.Context
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
    Accent("موجة زرقاء", Color(0xFF2EA8FF), Color(0xFFFFD21F)),
    Accent("برق ذهبي", Color(0xFFFFD21F), Color(0xFF2EA8FF)),
    Accent("بنفسجي", Color(0xFFA066FF), Color(0xFFFF5CA8)),
    Accent("أخضر", Color(0xFF2BE38B), Color(0xFF2EA8FF)),
    Accent("أحمر", Color(0xFFFF4D5E), Color(0xFFFFD21F)),
)

class ThemePrefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("theme", Context.MODE_PRIVATE)
    var index by mutableStateOf(sp.getInt("accent", 0).coerceIn(0, Accents.lastIndex))
        private set
    fun set(i: Int) { index = i; sp.edit().putInt("accent", i).apply() }
}

@Composable
fun MawjTheme(accent: Accent, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = accent.main, secondary = accent.second, tertiary = accent.second,
        background = Color(0xFF06070A), surface = Color(0xFF12141A), onBackground = Color.White, onSurface = Color.White,
    )
    MaterialTheme(colorScheme = scheme, typography = Typography(
        bodyLarge = TextStyle(fontFamily = Tajawal), bodyMedium = TextStyle(fontFamily = Tajawal),
        titleMedium = TextStyle(fontFamily = Cairo), titleLarge = TextStyle(fontFamily = Cairo),
        labelSmall = TextStyle(fontFamily = Tajawal)), content = content)
}
