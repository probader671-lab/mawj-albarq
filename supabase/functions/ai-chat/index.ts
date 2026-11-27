// AI chat proxy to Groq. Holds GROQ_KEY as a function secret. Requires a signed-in user JWT.
// GET-style:  { action: "models" }  -> { models: [id, ...] }  (chat models only)
// Chat:       { model, messages: [{role, content}] } -> { reply }
const CORS = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, content-type, apikey", "Content-Type": "application/json" };
const J = (o: unknown, s = 200) => new Response(JSON.stringify(o), { status: s, headers: CORS });
const GROQ = "https://api.groq.com/openai/v1";
const NOT_CHAT = /whisper|orpheus|guard|safeguard|tts|embed|distil-whisper/i;

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  const key = Deno.env.get("GROQ_KEY");
  if (!key) return J({ error: "not_configured" }, 503);
  const auth = req.headers.get("authorization") ?? "";
  const u = await fetch(`${Deno.env.get("SUPABASE_URL")}/auth/v1/user`, { headers: { Authorization: auth, apikey: Deno.env.get("SUPABASE_ANON_KEY") ?? "" } });
  if (!u.ok) return J({ error: "unauthorized" }, 401);
  let b: { action?: string; model?: string; system_prompt?: string; temperature?: number; messages?: { role: string; content: string }[] };
  try { b = await req.json(); } catch { return J({ error: "bad_json" }, 400); }
  try {
    if (b.action === "models") {
      const r = await fetch(`${GROQ}/models`, { headers: { Authorization: `Bearer ${key}` } });
      if (!r.ok) return J({ error: "upstream_" + r.status }, 502);
      const ids: string[] = ((await r.json()).data ?? []).map((m: any) => m.id).filter((id: string) => !NOT_CHAT.test(id)).sort();
      return J({ models: ids });
    }
    const model = String(b.model ?? "");
    if (!model || NOT_CHAT.test(model)) return J({ error: "bad_model" }, 400);
    const msgs = (b.messages ?? []).filter((m) => (m.role === "user" || m.role === "assistant") && typeof m.content === "string").slice(-20)
      .map((m) => ({ role: m.role, content: m.content.slice(0, 4000) }));
    if (!msgs.length) return J({ error: "no_messages" }, 400);
    const defaultPrompt = "You are a helpful assistant inside the Mawj Al-Barq app. Answer in the user's language (Arabic by default). Be concise.";
    const systemPrompt = typeof b.system_prompt === "string" && b.system_prompt.trim() ? b.system_prompt.trim().slice(0, 2500) : defaultPrompt;
    const rawTemp = typeof b.temperature === "number" && !Number.isNaN(b.temperature) ? b.temperature : 0.6;
    const temperature = Math.min(1.0, Math.max(0.1, rawTemp));
    const r = await fetch(`${GROQ}/chat/completions`, {
      method: "POST", headers: { Authorization: `Bearer ${key}`, "Content-Type": "application/json" },
      body: JSON.stringify({ model, max_tokens: 1200, temperature, messages: [{ role: "system", content: systemPrompt }, ...msgs] }),
    });
    if (!r.ok) return J({ error: "upstream_" + r.status }, 502);
    const j = await r.json();
    return J({ reply: String(j.choices?.[0]?.message?.content ?? "").trim() });
  } catch (_) { return J({ error: "error" }, 500); }
});
