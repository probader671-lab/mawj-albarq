// Returns the archive.org upload keys to a signed-in user (for videos > 20 min or > 500 MB).
// Keys live only as function secrets IA_ACCESS / IA_SECRET, so they can be rotated without an app update.
const CORS = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, content-type, apikey", "Content-Type": "application/json" };
const J = (o: unknown, s = 200) => new Response(JSON.stringify(o), { status: s, headers: CORS });

// Per-user rate limit (DB function rl_hit). Fails open if the check itself errors.
async function allowed(auth: string): Promise<boolean> {
  try {
    const r = await fetch(`${Deno.env.get("SUPABASE_URL")}/rest/v1/rpc/rl_hit`, { method: "POST", headers: { Authorization: auth, apikey: Deno.env.get("SUPABASE_ANON_KEY") ?? "", "Content-Type": "application/json" }, body: JSON.stringify({ p_fn: "ia", p_max: 5, p_window_sec: 86400 }) });
    if (!r.ok) return true;
    return (await r.json()) === true;
  } catch (_) { return true; }
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  const a = Deno.env.get("IA_ACCESS"), s = Deno.env.get("IA_SECRET");
  if (!a || !s) return J({ error: "not_configured" }, 503);
  const auth = req.headers.get("authorization") ?? "";
  const u = await fetch(`${Deno.env.get("SUPABASE_URL")}/auth/v1/user`, { headers: { Authorization: auth, apikey: Deno.env.get("SUPABASE_ANON_KEY") ?? "" } });
  if (!u.ok) return J({ error: "unauthorized" }, 401);
  if (!(await allowed(auth))) return J({ error: "rate_limited" }, 429);
  return J({ access: a, secret: s });
});
