// Signs Cloudinary uploads for signed-in users only (replaces the public unsigned preset).
// Secrets: CLOUDINARY_API_KEY, CLOUDINARY_API_SECRET. Input: { kind: "video" | "image" }
// Output: { cloud, api_key, timestamp, signature, folder, upload_preset, resource_type }
const CORS = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, content-type, apikey", "Content-Type": "application/json" };
const J = (o: unknown, s = 200) => new Response(JSON.stringify(o), { status: s, headers: CORS });
const CLOUD = "wuuhfgal", PRESET = "mawj_signed";
// per user per day: videos 30, images 100
const LIMITS: Record<string, [number, number]> = { video: [30, 86400], image: [100, 86400] };

async function sha1(s: string) {
  const d = await crypto.subtle.digest("SHA-1", new TextEncoder().encode(s));
  return Array.from(new Uint8Array(d)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  const key = Deno.env.get("CLOUDINARY_API_KEY"), secret = Deno.env.get("CLOUDINARY_API_SECRET");
  if (!key || !secret) return J({ error: "not_configured" }, 503);
  const auth = req.headers.get("authorization") ?? "";
  const u = await fetch(`${Deno.env.get("SUPABASE_URL")}/auth/v1/user`, { headers: { Authorization: auth, apikey: Deno.env.get("SUPABASE_ANON_KEY") ?? "" } });
  if (!u.ok) return J({ error: "unauthorized" }, 401);
  let b: { kind?: string } = {};
  try { b = await req.json(); } catch { /* default */ }
  const kind = b.kind === "image" ? "image" : "video";
  const [max, win] = LIMITS[kind];
  try {
    const r = await fetch(`${Deno.env.get("SUPABASE_URL")}/rest/v1/rpc/rl_hit`, { method: "POST", headers: { Authorization: auth, apikey: Deno.env.get("SUPABASE_ANON_KEY") ?? "", "Content-Type": "application/json" }, body: JSON.stringify({ p_fn: "sign_" + kind, p_max: max, p_window_sec: win }) });
    if (r.ok && (await r.json()) !== true) return J({ error: "rate_limited" }, 429);
  } catch (_) { /* fail open */ }
  const timestamp = Math.floor(Date.now() / 1000);
  const folder = kind === "video" ? "mawj/videos" : "mawj/images";
  // Signed params, alphabetical: folder, timestamp, upload_preset
  const signature = await sha1(`folder=${folder}&timestamp=${timestamp}&upload_preset=${PRESET}${secret}`);
  return J({ cloud: CLOUD, api_key: key, timestamp, signature, folder, upload_preset: PRESET, resource_type: kind });
});
