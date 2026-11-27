package com.mawjalbarq.app
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

// Original offline games written for this app: no ads, no network, no third-party code.
@Composable
fun GamesScreen(onClose: () -> Unit) {
    var game by remember { mutableStateOf(0) }
    BackHandler { if (game != 0) game = 0 else onClose() }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (game != 0) game = 0 else onClose() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
            Text(when (game) { 1 -> "2048"; 2 -> "الثعبان"; 3 -> "لعبة الذاكرة"; 4 -> "إكس أو"; else -> "الألعاب" }, fontFamily = Cairo, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        when (game) {
            1 -> Game2048()
            2 -> SnakeGame()
            3 -> MemoryGame()
            4 -> XoGame()
            else -> {
                listOf("2048" to "ادمج الأرقام للوصول إلى 2048", "الثعبان" to "كل التفاح ولا تصطدم", "لعبة الذاكرة" to "اعثر على كل الأزواج المتشابهة", "إكس أو" to "العب ضد الجهاز").forEachIndexed { i, (t, d) ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { game = i + 1 }) {
                        Column(Modifier.padding(18.dp)) {
                            Text(t, fontFamily = Cairo, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(d, fontFamily = Cairo, fontSize = 14.sp)
                        }
                    }
                }
                Text("ألعاب بدون إعلانات وتعمل بدون إنترنت", fontFamily = Cairo, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}

@Composable private fun Cell(text: String, color: Color, size: androidx.compose.ui.unit.Dp, fs: Int = 20, onClick: (() -> Unit)? = null) {
    Box(Modifier.size(size).padding(2.dp).clip(RoundedCornerShape(8.dp)).background(color).then(if (onClick != null) Modifier.clickable { onClick() } else Modifier), contentAlignment = Alignment.Center) {
        Text(text, fontSize = fs.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
    }
}

// ---------- 2048 ----------
private fun slide(row: List<Int>): Pair<List<Int>, Int> {
    val n = row.filter { it != 0 }.toMutableList(); var gain = 0; var i = 0
    val out = mutableListOf<Int>()
    while (i < n.size) { if (i + 1 < n.size && n[i] == n[i + 1]) { out.add(n[i] * 2); gain += n[i] * 2; i += 2 } else { out.add(n[i]); i++ } }
    while (out.size < 4) out.add(0)
    return out to gain
}
@Composable fun Game2048() {
    var g by remember { mutableStateOf(List(16) { 0 }) }
    var score by remember { mutableIntStateOf(0) }
    fun addTile(b: List<Int>): List<Int> { val e = b.indices.filter { b[it] == 0 }; if (e.isEmpty()) return b; val m = b.toMutableList(); m[e.random()] = if (Random.nextInt(10) == 0) 4 else 2; return m }
    fun reset() { g = addTile(addTile(List(16) { 0 })); score = 0 }
    LaunchedEffect(Unit) { reset() }
    fun move(dir: Int) { // 0 left,1 right,2 up,3 down
        val nb = g.toMutableList(); var gained = 0
        for (k in 0 until 4) {
            val idx = if (dir < 2) List(4) { k * 4 + it } else List(4) { it * 4 + k }
            val line = if (dir == 1 || dir == 3) idx.reversed() else idx
            val (r, gn) = slide(line.map { g[it] }); gained += gn
            line.forEachIndexed { j, p -> nb[p] = r[j] }
        }
        if (nb != g) { g = addTile(nb); score += gained }
    }
    val over = g.none { it == 0 } && (0 until 4).none { r -> (0 until 4).any { c -> val v = g[r * 4 + c]; (c < 3 && g[r * 4 + c + 1] == v) || (r < 3 && g[(r + 1) * 4 + c] == v) } }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("النقاط: $score", fontFamily = Cairo, fontSize = 18.sp)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.pointerInput(g) {
            var dx = 0f; var dy = 0f
            detectDragGestures(onDragStart = { dx = 0f; dy = 0f }, onDragEnd = { if (abs(dx) > abs(dy)) { if (abs(dx) > 40) move(if (dx < 0) 0 else 1) } else if (abs(dy) > 40) move(if (dy < 0) 2 else 3) }) { _, d -> dx += d.x; dy += d.y }
        }.background(Color(0xFF37474F), RoundedCornerShape(10.dp)).padding(4.dp)) {
            for (r in 0 until 4) Row { for (c in 0 until 4) { val v = g[r * 4 + c]
                Cell(if (v == 0) "" else "$v", if (v == 0) Color(0xFF546E7A) else Color(0xFFEF6C00).copy(alpha = (0.45f + 0.05f * Integer.numberOfTrailingZeros(v)).coerceAtMost(1f)), 78.dp, if (v >= 1024) 16 else 22) } }
        }
        Spacer(Modifier.height(12.dp))
        Text(if (over) "انتهت اللعبة" else "اسحب بإصبعك في أي اتجاه", fontFamily = Cairo)
        Button(onClick = { reset() }, modifier = Modifier.padding(top = 8.dp)) { Text("لعبة جديدة", fontFamily = Cairo) }
    }
}

// ---------- Snake ----------
@Composable fun SnakeGame() {
    val n = 16
    var snake by remember { mutableStateOf(listOf(8 to 8, 7 to 8, 6 to 8)) }
    var dir by remember { mutableStateOf(1 to 0) }
    var apple by remember { mutableStateOf(12 to 8) }
    var dead by remember { mutableStateOf(false) }
    var score by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    fun reset() { snake = listOf(8 to 8, 7 to 8, 6 to 8); dir = 1 to 0; apple = 12 to 8; dead = false; score = 0; tick++ }
    LaunchedEffect(tick) {
        while (!dead) {
            delay((220 - score * 4L).coerceAtLeast(90))
            val h = snake.first(); val nh = ((h.first + dir.first + n) % n) to ((h.second + dir.second + n) % n)
            if (nh in snake) { dead = true; break }
            if (nh == apple) { snake = listOf(nh) + snake; score++; var a: Pair<Int, Int>; do { a = Random.nextInt(n) to Random.nextInt(n) } while (a in snake); apple = a }
            else snake = listOf(nh) + snake.dropLast(1)
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("النقاط: $score", fontFamily = Cairo, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.pointerInput(Unit) {
            var dx = 0f; var dy = 0f
            detectDragGestures(onDragStart = { dx = 0f; dy = 0f }, onDragEnd = {
                val nd = if (abs(dx) > abs(dy)) (if (dx > 0) 1 to 0 else -1 to 0) else (if (dy > 0) 0 to 1 else 0 to -1)
                if (nd.first != -dir.first || nd.second != -dir.second) dir = nd
            }) { _, d -> dx += d.x; dy += d.y }
        }.background(Color(0xFF263238), RoundedCornerShape(8.dp))) {
            for (y in 0 until n) Row { for (x in 0 until n) {
                val c = when { (x to y) == apple -> Color(0xFFE53935); snake.first() == (x to y) -> Color(0xFF66BB6A); (x to y) in snake -> Color(0xFF2E7D32); else -> Color.Transparent }
                Box(Modifier.size(21.dp).padding(1.dp).background(c, RoundedCornerShape(4.dp)))
            } }
        }
        Spacer(Modifier.height(8.dp))
        Text(if (dead) "انتهت اللعبة" else "اسحب لتغيير الاتجاه", fontFamily = Cairo)
        Button(onClick = { reset() }, modifier = Modifier.padding(top = 8.dp)) { Text("لعبة جديدة", fontFamily = Cairo) }
    }
}

// ---------- Memory ----------
@Composable fun MemoryGame() {
    val icons = listOf("🍎", "🚀", "⚽", "🎵", "🐱", "🌙", "🍕", "⭐")
    var cards by remember { mutableStateOf((icons + icons).shuffled()) }
    var open by remember { mutableStateOf(listOf<Int>()) }
    var done by remember { mutableStateOf(setOf<Int>()) }
    var moves by remember { mutableIntStateOf(0) }
    LaunchedEffect(open) { if (open.size == 2) { delay(700); if (cards[open[0]] == cards[open[1]]) done = done + open; open = emptyList() } }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("الحركات: $moves", fontFamily = Cairo, fontSize = 18.sp)
        Spacer(Modifier.height(12.dp))
        for (r in 0 until 4) Row { for (c in 0 until 4) { val i = r * 4 + c; val shown = i in open || i in done
            Cell(if (shown) cards[i] else "?", if (i in done) Color(0xFF2E7D32) else if (shown) Color(0xFFEF6C00) else Color(0xFF455A64), 78.dp, 30) {
                if (!shown && open.size < 2) { open = open + i; if (open.size == 2) moves++ } } } }
        Spacer(Modifier.height(12.dp))
        if (done.size == 16) Text("أحسنت! أنهيت اللعبة في $moves حركة", fontFamily = Cairo, fontWeight = FontWeight.Bold)
        Button(onClick = { cards = (icons + icons).shuffled(); open = emptyList(); done = emptySet(); moves = 0 }, modifier = Modifier.padding(top = 8.dp)) { Text("لعبة جديدة", fontFamily = Cairo) }
    }
}

// ---------- XO ----------
private val lines = listOf(listOf(0,1,2), listOf(3,4,5), listOf(6,7,8), listOf(0,3,6), listOf(1,4,7), listOf(2,5,8), listOf(0,4,8), listOf(2,4,6))
private fun winner(b: List<String>): String? = lines.firstOrNull { l -> b[l[0]] != "" && b[l[0]] == b[l[1]] && b[l[1]] == b[l[2]] }?.let { b[it[0]] }
private fun minimax(b: List<String>, aiTurn: Boolean): Int {
    when (winner(b)) { "O" -> return 1; "X" -> return -1 }
    val e = b.indices.filter { b[it] == "" }; if (e.isEmpty()) return 0
    val scores = e.map { minimax(b.toMutableList().also { m -> m[it] = if (aiTurn) "O" else "X" }, !aiTurn) }
    return if (aiTurn) scores.max() else scores.min()
}
@Composable fun XoGame() {
    var b by remember { mutableStateOf(List(9) { "" }) }
    val w = winner(b); val full = b.none { it == "" }
    fun play(i: Int) {
        if (b[i] != "" || w != null) return
        var nb = b.toMutableList(); nb[i] = "X"
        if (winner(nb) == null && nb.any { it == "" }) {
            val e = nb.indices.filter { nb[it] == "" }
            val best = e.maxBy { j -> minimax(nb.toMutableList().also { it[j] = "O" }, false) * 10 + Random.nextInt(3) }
            // AI is intentionally beatable 1 time in 4
            val pick = if (Random.nextInt(4) == 0) e.random() else best
            nb[pick] = "O"
        }
        b = nb
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(when { w == "X" -> "فزت! 🎉"; w == "O" -> "فاز الجهاز"; full -> "تعادل"; else -> "دورك (X)" }, fontFamily = Cairo, fontSize = 18.sp)
        Spacer(Modifier.height(12.dp))
        for (r in 0 until 3) Row { for (c in 0 until 3) { val i = r * 3 + c
            Cell(b[i], if (b[i] == "X") Color(0xFF1E88E5) else if (b[i] == "O") Color(0xFFE53935) else Color(0xFF455A64), 100.dp, 40) { play(i) } } }
        Button(onClick = { b = List(9) { "" } }, modifier = Modifier.padding(top = 16.dp)) { Text("لعبة جديدة", fontFamily = Cairo) }
    }
}
