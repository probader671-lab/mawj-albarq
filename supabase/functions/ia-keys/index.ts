// Returns the archive.org upload keys to a signed-in user (for videos > 20 min or > 500 MB).
// Keys live only as function secrets IA_ACCESS / IA_SECRET, so they can be rotated without an app update.
const CORS = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, content-type, apikey", "Content-Type": "application/json" };
const J = (o: unknown, s = 200) => new Response(JSON.stringify(o), { status: s, headers: CORS });

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  const a = Deno.env.get("IA_ACCESS"), s = Deno.env.get("IA_SECRET");
  if (!a || !s) return J({ error: "not_configured" }, 503);
  const auth = req.headers.get("authorization") ?? "";
  const u = await fetch(`${Deno.env.get("SUPABASE_URL")}/auth/v1/user`, { headers: { Authorization: auth, apikey: Deno.env.get("SUPABASE_ANON_KEY") ?? "" } });
  if (!u.ok) return J({ error: "unauthorized" }, 401);
  return J({ access: a, secret: s });
});
