// Auto title/description for an uploaded video from its speech. Holds GROQ_KEY as a function secret.
// Input: { audio: base64 m4a (first ~45 s, speech only) }  Output: { title, description } (empty strings if nothing usable).
// Requires a signed-in user JWT.
const CORS = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, content-type, apikey", "Content-Type": "application/json" };
const J = (o: unknown, s = 200) => new Response(JSON.stringify(o), { status: s, headers: CORS });
const GROQ = "https://api.groq.com/openai/v1";

// Per-user rate limit (DB function rl_hit). Fails open if the check itself errors.
async function allowed(auth: string, kind: string): Promise<boolean> {
  try {
    const r = await fetch(`${Deno.env.get("SUPABASE_URL")}/rest/v1/rpc/rl_hit`, { method: "POST", headers: { Authorization: auth, apikey: Deno.env.get("SUPABASE_ANON_KEY") ?? "", "Content-Type": "application/json" }, body: JSON.stringify({ p_fn: kind, p_max: kind === "stt" ? 240 : 20, p_window_sec: 3600 }) });
    if (!r.ok) return true;
    return (await r.json()) === true;
  } catch (_) { return true; }
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  const key = Deno.env.get("GROQ_KEY");
  if (!key) return J({ title: "", description: "", reason: "not_configured" });
  const auth = req.headers.get("authorization") ?? "";
  const u = await fetch(`${Deno.env.get("SUPABASE_URL")}/auth/v1/user`, { headers: { Authorization: auth, apikey: Deno.env.get("SUPABASE_ANON_KEY") ?? "" } });
  if (!u.ok) return J({ error: "unauthorized" }, 401);
  let body: { audio?: string; text?: string; transcribe_only?: boolean };
  try { body = await req.json(); } catch { return J({ error: "bad_json" }, 400); }
  if (!(await allowed(auth, body.transcribe_only ? "stt" : "title"))) return J({ error: "rate_limited" }, 429);
  const a = body.audio;
  const given = typeof body.text === "string" ? body.text.trim().slice(0, 12000) : "";
  if (!given && (typeof a !== "string" || a.length < 1000 || a.length > 6_000_000)) return J({ title: "", description: "", reason: "no_audio" });
  try {
    let text = given;
    if (!text) {
    const bin = Uint8Array.from(atob(a as string), (c) => c.charCodeAt(0));
    const fd = new FormData();
    fd.set("file", new Blob([bin], { type: "audio/mp4" }), "a.m4a");
    fd.set("model", "whisper-large-v3-turbo");
    fd.set("response_format", "json");
    const tr = await fetch(`${GROQ}/audio/transcriptions`, { method: "POST", headers: { Authorization: `Bearer ${key}` }, body: fd });
    if (!tr.ok) return J({ title: "", description: "", reason: "stt_" + tr.status });
    text = ((await tr.json()).text ?? "").trim().slice(0, 3000);
    if (body.transcribe_only) return J({ text });
    }
    if (text.length < 4) return J({ title: "", description: "", reason: "short_" + text.length });
    const cr = await fetch(`${GROQ}/chat/completions`, {
      method: "POST", headers: { Authorization: `Bearer ${key}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        model: "openai/gpt-oss-20b", temperature: 0.3, max_tokens: 2000, reasoning_effort: "low", response_format: { type: "json_object" },
        messages: [
          { role: "system", content: "You write a title and description for a video from its transcript. Reply with JSON {\"title\":\"...\",\"description\":\"...\"}. Rules: 1) Use the same language as the transcript (Arabic stays Arabic). 2) Be accurate: say what is actually said in the video, including its main topic, and never add anything that is not in the transcript. 3) Never invent opinions, judgments, accusations, quotes or claims, and do not label the speakers or their intent. 4) Do not use a person's name unless it is clearly spoken in the transcript as a fact about who is speaking or who the video is about; never guess names. 5) Respectful, plain wording; no emojis, no quotes, no clickbait, no insults. 6) Title: up to 120 characters, one clear sentence or phrase. Description: one to three sentences, up to 300 characters. 7) If the speech is unclear, fragmentary or looks like a mistranscription, give a short generic title about the topic, or leave both empty." },
          { role: "user", content: text },
        ],
      }),
    });
    if (!cr.ok) return J({ title: "", description: "", reason: "llm_" + cr.status });
    let j: any = {};
    try { const raw = String((await cr.json()).choices?.[0]?.message?.content ?? "{}"); j = JSON.parse(raw.slice(raw.indexOf("{"), raw.lastIndexOf("}") + 1)); } catch { return J({ title: "", description: "", reason: "parse" }); }
    const clean = (s: unknown, n: number) => String(s ?? "").replace(/\s+/g, " ").trim().slice(0, n);
    return J({ title: clean(j.title, 120), description: clean(j.description, 300) });
  } catch (_) { return J({ title: "", description: "", reason: "error" }); }
});
