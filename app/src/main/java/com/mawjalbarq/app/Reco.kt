package com.mawjalbarq.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.exp
import kotlin.math.ln

/**
 * Recommendations v1 (on-device ranking, signals stored per user in Supabase).
 * Order of the feed: unseen videos that fit the user's taste, then unseen anything, then already-watched videos as a last resort.
 */
object Reco {
    class Sig(var watchMs: Int = 0, var completed: Boolean = false, var skipped: Boolean = false, var notInterested: Boolean = false, var views: Int = 0)

    private val sig = HashMap<String, Sig>()
    private val taste = HashMap<String, Float>()
    private val servedThisSession = HashSet<String>()
    private var loadedFor: String? = null
    private var curated: Set<String> = emptySet()
    private var newCreatorMax = 2
    private var exploreShare = 0.15f
    private var maxStreak = 2
    private val rnd = java.util.Random()
    var promptedThisSession = false

    // ---- categories (Arabic keyword matching on normalised text) ----
    private val CATS: List<Pair<String, List<String>>> = listOf(
        "قرآن" to listOf("قران", "تلاوه", "ترتيل", "سوره", "القارئ", "قارئ", "تجويد", "مصحف"),
        "ديني" to listOf("حديث", "خطبه", "محاضره دينيه", "دعاء", "صلاه", "استغفر", "سيره", "اسلام", "شيخ", "رمضان"),
        "كوميدي" to listOf("ضحك", "كوميدي", "نكته", "نكت", "مقلب", "مضحك", "ميمز", "فشل", "تحشيش", "هزار"),
        "رياضة" to listOf("كره", "مباراه", "هدف", "دوري", "رياضه", "الاهلي", "الزمالك", "الهلال", "النصر", "ريال", "برشلونه", "ملعب", "لاعب"),
        "ألعاب" to listOf("لعبه", "العاب", "ببجي", "فورت", "جيمنج", "ماين كرافت", "فري فاير", "gaming", "gameplay", "minecraft"),
        "طبخ" to listOf("طبخ", "وصفه", "اكل", "حلويات", "مطعم", "طبخات", "فطار"),
        "سيارات" to listOf("سياره", "سيارات", "موتور", "دريفت", "سباق"),
        "تعليم" to listOf("تعليم", "شرح", "درس", "تعلم", "كورس", "برمجه", "معلومه", "معلومات"),
        "تقنية" to listOf("تقنيه", "موبايل", "ايفون", "اندرويد", "ذكاء اصطناعي", "تطبيق", "لابتوب"),
        "سفر" to listOf("سفر", "رحله", "سياحه", "جوله", "فلوج", "vlog"),
        "أطفال" to listOf("اطفال", "طفل", "كرتون", "رسوم متحركه"),
        "أخبار" to listOf("خبر", "اخبار", "عاجل", "تقرير")
    )

    fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s.lowercase()) {
            when {
                ch in '\u064B'..'\u0652' || ch == '\u0670' || ch == '\u0640' -> {}
                ch == 'أ' || ch == 'إ' || ch == 'آ' -> sb.append('ا')
                ch == 'ى' -> sb.append('ي')
                ch == 'ة' -> sb.append('ه')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /** Title/description mention recitation words (used together with the audio check, never alone, and never shown to the uploader). */
    fun looksQuranic(text: String): Boolean {
        val t = normalize(text)
        return CATS.first().second.any { t.contains(normalize(it)) }
    }

    fun categoryOf(v: Video): String {
        if (v.isQuran) return "قرآن"
        val t = normalize(v.title + " " + v.desc)
        for ((c, kws) in CATS) if (kws.any { t.contains(normalize(it)) }) return c
        return "عام"
    }

    // ---- loading ----
    suspend fun ensureLoaded() {
        val u = App.userId
        if (u == null) { loadedFor = null; sig.clear(); taste.clear(); return }
        if (loadedFor == u) return
        sig.clear(); taste.clear()
        try {
            val (c, t) = api("GET", "/rest/v1/user_video_signals?user_id=eq.$u&select=video_id,watch_ms,completed,skipped,not_interested,views_count&order=updated_at.desc,video_id.desc&limit=1000")
            if (c == 200) { val a = JSONArray(t); for (i in 0 until a.length()) { val o = a.getJSONObject(i)
                sig[o.getString("video_id")] = Sig(o.optInt("watch_ms"), o.optBoolean("completed"), o.optBoolean("skipped"), o.optBoolean("not_interested"), o.optInt("views_count")) } }
            val (c2, t2) = api("GET", "/rest/v1/user_taste?user_id=eq.$u&select=key,score&limit=500")
            if (c2 == 200) { val a = JSONArray(t2); for (i in 0 until a.length()) { val o = a.getJSONObject(i); taste[o.getString("key")] = o.optDouble("score").toFloat() } }
            val (c3, t3) = api("GET", "/rest/v1/backend_settings?key=eq.reco_config&select=value")
            if (c3 == 200) JSONArray(t3).optJSONObject(0)?.optJSONObject("value")?.let { o ->
                val ids = o.optJSONArray("curated_user_ids") ?: JSONArray()
                curated = (0 until ids.length()).map { ids.getString(it) }.toSet()
                newCreatorMax = o.optInt("new_creator_max_videos", 2); exploreShare = o.optDouble("explore_share", 0.15).toFloat()
                maxStreak = o.optInt("max_same_creator_streak", 2).coerceAtLeast(1)
            }
            loadedFor = u
        } catch (_: Exception) {}
    }

    fun seen(v: Video): Boolean = sig[v.id]?.let { it.completed || it.watchMs >= 3000 } == true

    private fun affinity(v: Video): Float = (taste["cat:" + categoryOf(v)] ?: 0f) + 0.6f * (taste["cr:" + v.userId] ?: 0f)

    private fun score(v: Video): Float {
        val aff = affinity(v)
        val quality = ((v.likes + 2f * v.comments) / (v.views + 20f)).coerceIn(0f, 0.5f) * 4f + ln(1f + v.views) * 0.05f
        val ageH = try { (System.currentTimeMillis() - java.time.Instant.parse(v.createdAt).toEpochMilli()) / 3_600_000f } catch (_: Exception) { 24f * 30 }
        val fresh = exp(-ageH.coerceAtLeast(0f) / 72f)
        val followed = if (Feed.following[v.userId] == true) 1.2f else 0f
        val explore = if (rnd.nextFloat() < exploreShare) 1.0f else rnd.nextFloat() * 0.3f
        // borderline music (the check was unsure, so the video was published): push it down the feed. Recitation is exempt.
        val musicPenalty = if (v.isQuran) 0f else ((v.musicScore - 0.3f).coerceAtLeast(0f) * 2.5f)
        return aff * 1.0f + quality + fresh + followed + explore - musicPenalty
    }

    /** Ranks a batch: tiers (unseen+matching, unseen other, watched), score within tier, then a diversity pass. */
    fun rank(list: List<Video>): List<Video> {
        val cand = list.filter { sig[it.id]?.notInterested != true }
        val scored = cand.map { it to score(it) }
        fun matching(v: Video) = affinity(v) >= 0f || Feed.following[v.userId] == true
        val t0 = scored.filter { !seen(it.first) && matching(it.first) }.sortedByDescending { it.second }.map { it.first }
        val t1 = scored.filter { !seen(it.first) && !matching(it.first) }.sortedByDescending { it.second }.map { it.first }
        val t2 = scored.filter { seen(it.first) }.sortedByDescending { it.second }.map { it.first }
        return diversify(t0 + t1 + t2)
    }

    private fun diversify(ordered: List<Video>): List<Video> {
        val pool = ArrayList(ordered); val out = ArrayList<Video>(ordered.size)
        while (pool.isNotEmpty()) {
            val streakCreator = out.takeLastWhile { it.userId == (out.lastOrNull()?.userId ?: "") }.size
            val lastCats = out.takeLast(2).map { categoryOf(it) }
            val pick = pool.firstOrNull { v ->
                !(out.isNotEmpty() && v.userId == out.last().userId && streakCreator >= maxStreak) &&
                !(lastCats.size == 2 && lastCats.all { it == categoryOf(v) } && categoryOf(v) != "عام")
            } ?: pool.first()
            out += pick; pool.remove(pick)
        }
        return out
    }

    // ---- signals ----
    private fun bump(key: String, d: Float) { taste[key] = ((taste[key] ?: 0f) + d).coerceIn(-10f, 10f) }

    private fun persist(vid: String, s: Sig, keys: List<String>) {
        val u = App.userId ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val b = JSONObject().put("user_id", u).put("video_id", vid).put("watch_ms", s.watchMs).put("completed", s.completed)
                .put("skipped", s.skipped).put("not_interested", s.notInterested).put("views_count", s.views).put("updated_at", java.time.Instant.now().toString())
            api("POST", "/rest/v1/user_video_signals?on_conflict=user_id,video_id", b.toString(), prefer = "resolution=merge-duplicates,return=minimal")
            if (keys.isNotEmpty()) {
                val arr = JSONArray(); val now = java.time.Instant.now().toString()
                keys.distinct().forEach { k -> arr.put(JSONObject().put("user_id", u).put("key", k).put("score", (taste[k] ?: 0f).toDouble()).put("updated_at", now)) }
                api("POST", "/rest/v1/user_taste?on_conflict=user_id,key", arr.toString(), prefer = "resolution=merge-duplicates,return=minimal")
            }
        }
    }

    /** A watch session ended: [watchedMs] of a video with a known duration. */
    fun onWatched(v: Video, watchedMs: Long) {
        if (App.userId == null || watchedMs < 300) return
        val s = sig.getOrPut(v.id) { Sig() }
        val cap = if (v.duration > 0) (v.duration * 1500L) else 3_600_000L
        val ms = watchedMs.coerceAtMost(cap).toInt()
        s.views += 1; if (s.views > 1) bump("cat:" + categoryOf(v), 0.3f)  // rewatch
        s.watchMs = maxOf(s.watchMs, ms)
        val frac = if (v.duration > 0) ms / (v.duration * 1000f) else 0.5f
        s.completed = s.completed || frac >= 0.9f
        s.skipped = ms < 2000 && !s.completed
        val d = when { frac >= 0.9f -> 0.5f; ms < 2000 -> -0.3f; else -> 0.15f + 0.3f * frac.coerceAtMost(1f) }
        bump("cat:" + categoryOf(v), d); bump("cr:" + v.userId, d * 0.6f)
        persist(v.id, s, listOf("cat:" + categoryOf(v), "cr:" + v.userId))
    }

    fun onLike(v: Video, liked: Boolean) = explicit(v, if (liked) 1.0f else -0.5f)
    fun onSave(v: Video, saved: Boolean) = explicit(v, if (saved) 0.8f else -0.4f)
    fun onFollow(creatorId: String, followed: Boolean) { bump("cr:$creatorId", if (followed) 1.5f else -0.8f); persistTaste(listOf("cr:$creatorId")) }

    private fun explicit(v: Video, d: Float) {
        if (App.userId == null) return
        bump("cat:" + categoryOf(v), d); bump("cr:" + v.userId, d * 0.8f)
        persistTaste(listOf("cat:" + categoryOf(v), "cr:" + v.userId))
    }

    private fun persistTaste(keys: List<String>) {
        val u = App.userId ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val arr = JSONArray(); val now = java.time.Instant.now().toString()
            keys.distinct().forEach { k -> arr.put(JSONObject().put("user_id", u).put("key", k).put("score", (taste[k] ?: 0f).toDouble()).put("updated_at", now)) }
            api("POST", "/rest/v1/user_taste?on_conflict=user_id,key", arr.toString(), prefer = "resolution=merge-duplicates,return=minimal")
        }
    }

    // ---- interested / not interested cards ----
    private val answered = HashSet<String>()

    /** A video of a "new" creator (curated list, or a creator with very few videos) that the user has not been asked about. */
    fun promptCandidate(list: List<Video>): Video? {
        if (promptedThisSession || App.userId == null) return null
        return list.firstOrNull { v ->
            v.userId.isNotBlank() && v.userId != App.userId && !answered.contains(v.userId) && taste["cr:" + v.userId] == null &&
                Feed.following[v.userId] != true && sig[v.id]?.notInterested != true && !seen(v) &&
                (curated.contains(v.userId) || (curated.isEmpty() && list.count { it.userId == v.userId } <= newCreatorMax))
        }
    }

    fun answerPrompt(v: Video, interested: Boolean) {
        answered += v.userId; promptedThisSession = true
        val cat = "cat:" + categoryOf(v); val cr = "cr:" + v.userId
        bump(cat, if (interested) 1.0f else -1.0f); bump(cr, if (interested) 1.5f else -2.0f)
        val s = sig.getOrPut(v.id) { Sig() }
        if (!interested) s.notInterested = true
        persist(v.id, s, listOf(cat, cr))
        Feed.rerank()
    }

    /** Wall-clock watch tracker for a pager or player. */
    class Tracker {
        private var cur: Video? = null
        private var start = 0L
        fun switchTo(v: Video?) { stop(); cur = v; start = System.currentTimeMillis(); if (v != null) servedThisSession += v.id }
        fun stop() { val c = cur ?: return; onWatched(c, System.currentTimeMillis() - start); cur = null }
    }
}
