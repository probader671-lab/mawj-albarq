// Auto title/description for an uploaded video from its speech. Holds GROQ_KEY as a function secret.
// Input: { audio: base64 m4a (first ~45 s, speech only) }  Output: { title, description } (empty strings if nothing usable).
// Requires a signed-in user JWT.
const CORS = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, content-type, apikey", "Content-Type": "application/json" };
const J = (o: unknown, s = 200) => new Response(JSON.stringify(o), { status: s, headers: CORS });
const GROQ = "https://api.groq.com/openai/v1";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  const key = Deno.env.get("GROQ_KEY");
  if (!key) return J({ title: "", description: "", reason: "not_configured" });
  const auth = req.headers.get("authorization") ?? "";
  const u = await fetch(`${Deno.env.get("SUPABASE_URL")}/auth/v1/user`, { headers: { Authorization: auth, apikey: Deno.env.get("SUPABASE_ANON_KEY") ?? "" } });
  if (!u.ok) return J({ error: "unauthorized" }, 401);
  let body: { audio?: string };
  try { body = await req.json(); } catch { return J({ error: "bad_json" }, 400); }
  const a = body.audio;
  if (typeof a !== "string" || a.length < 1000 || a.length > 6_000_000) return J({ title: "", description: "" });
  try {
    const bin = Uint8Array.from(atob(a), (c) => c.charCodeAt(0));
    const fd = new FormData();
    fd.set("file", new Blob([bin], { type: "audio/mp4" }), "a.m4a");
    fd.set("model", "whisper-large-v3-turbo");
    fd.set("response_format", "json");
    const tr = await fetch(`${GROQ}/audio/transcriptions`, { method: "POST", headers: { Authorization: `Bearer ${key}` }, body: fd });
    if (!tr.ok) return J({ title: "", description: "", reason: "stt_" + tr.status });
    const text: string = ((await tr.json()).text ?? "").trim().slice(0, 3000);
    if (text.length < 12) return J({ title: "", description: "" });
    const cr = await fetch(`${GROQ}/chat/completions`, {
      method: "POST", headers: { Authorization: `Bearer ${key}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        model: "openai/gpt-oss-20b", temperature: 0.3, max_tokens: 400, response_format: { type: "json_object" },
        messages: [
          { role: "system", content: "You write a short title and description for a video from its transcript. Reply with JSON {\"title\":\"...\",\"description\":\"...\"}. Use the same language as the transcript (Arabic stays Arabic). Title: max 60 characters, plain, no emojis, no quotes. Description: one or two sentences, max 200 characters. Only describe what the transcript says. Never invent names, quotes or claims." },
          { role: "user", content: text },
        ],
      }),
    });
    if (!cr.ok) return J({ title: "", description: "", reason: "llm_" + cr.status });
    const j = JSON.parse((await cr.json()).choices?.[0]?.message?.content ?? "{}");
    const clean = (s: unknown, n: number) => String(s ?? "").replace(/\s+/g, " ").trim().slice(0, n);
    return J({ title: clean(j.title, 60), description: clean(j.description, 200) });
  } catch (_) { return J({ title: "", description: "", reason: "error" }); }
});
