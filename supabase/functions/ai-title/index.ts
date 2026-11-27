// Auto title/description for an uploaded video from its speech. Holds GROQ_KEY as a function secret.
// Input: { audio: base64 m4a (first ~45 s, speech only) }  Output: { title, description } (empty strings if nothing usable).
// Requires a signed-in user JWT.
const CORS = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, content-type, apikey", "Content-Type": "application/json" };
const J = (o: unknown, s = 200) => new Response(JSON.stringify(o), { status: s, headers: CORS });
const GROQ = "https://api.groq.com/openai/v1";

// Per-user rate limit (DB function rl_hit). Fails open if the check itself errors.
async function allowed(auth: string): Promise<boolean> {
  try {
    const r = await fetch(`${Deno.env.get("SUPABASE_URL")}/rest/v1/rpc/rl_hit`, { method: "POST", headers: { Authorization: auth, apikey: Deno.env.get("SUPABASE_ANON_KEY") ?? "", "Content-Type": "application/json" }, body: JSON.stringify({ p_fn: "title", p_max: 20, p_window_sec: 3600 }) });
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
  if (!(await allowed(auth))) return J({ error: "rate_limited" }, 429);
  let body: { audio?: string; text?: string };
  try { body = await req.json(); } catch { return J({ error: "bad_json" }, 400); }
  const a = body.audio;
  const given = typeof body.text === "string" ? body.text.trim().slice(0, 3000) : "";
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
    }
    if (text.length < 4) return J({ title: "", description: "", reason: "short_" + text.length });
    const cr = await fetch(`${GROQ}/chat/completions`, {
      method: "POST", headers: { Authorization: `Bearer ${key}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        model: "openai/gpt-oss-20b", temperature: 0.3, max_tokens: 2000, reasoning_effort: "low", response_format: { type: "json_object" },
        messages: [
          { role: "system", content: "You write a short title and description for a video from its transcript. Reply with JSON {\"title\":\"...\",\"description\":\"...\"}. Use the same language as the transcript (Arabic stays Arabic). Title: max 60 characters, plain, no emojis, no quotes. Description: one or two sentences, max 200 characters. Only describe what the transcript says. Never invent names, quotes or claims." },
          { role: "user", content: text },
        ],
      }),
    });
    if (!cr.ok) return J({ title: "", description: "", reason: "llm_" + cr.status });
    let j: any = {};
    try { const raw = String((await cr.json()).choices?.[0]?.message?.content ?? "{}"); j = JSON.parse(raw.slice(raw.indexOf("{"), raw.lastIndexOf("}") + 1)); } catch { return J({ title: "", description: "", reason: "parse" }); }
    const clean = (s: unknown, n: number) => String(s ?? "").replace(/\s+/g, " ").trim().slice(0, n);
    return J({ title: clean(j.title, 60), description: clean(j.description, 200) });
  } catch (_) { return J({ title: "", description: "", reason: "error" }); }
});
