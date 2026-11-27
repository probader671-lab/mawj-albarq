// Proxy to Face++ Detect. Holds FACEPP_KEY / FACEPP_SECRET as function secrets.
// Input: { frames: [base64 jpeg, ...] } (max 6). Requires a logged-in user JWT.
// Output: { available: bool, frames: [{faces:[{gender,quality,blur,yaw}]}] }
const CORS = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, content-type, apikey", "Content-Type": "application/json" };
const J = (o: unknown, s = 200) => new Response(JSON.stringify(o), { status: s, headers: CORS });

// Per-user rate limit (DB function rl_hit). Fails open if the check itself errors.
async function allowed(auth: string): Promise<boolean> {
  try {
    const r = await fetch(`${Deno.env.get("SUPABASE_URL")}/rest/v1/rpc/rl_hit`, { method: "POST", headers: { Authorization: auth, apikey: Deno.env.get("SUPABASE_ANON_KEY") ?? "", "Content-Type": "application/json" }, body: JSON.stringify({ p_fn: "face", p_max: 60, p_window_sec: 3600 }) });
    if (!r.ok) return true;
    return (await r.json()) === true;
  } catch (_) { return true; }
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  const key = Deno.env.get("FACEPP_KEY"), secret = Deno.env.get("FACEPP_SECRET");
  if (!key || !secret) return J({ available: false, reason: "not_configured" });
  // Require a real signed-in user (not just the anon key)
  const auth = req.headers.get("authorization") ?? "";
  const ures = await fetch(`${Deno.env.get("SUPABASE_URL")}/auth/v1/user`, { headers: { Authorization: auth, apikey: Deno.env.get("SUPABASE_ANON_KEY") ?? "" } });
  if (!ures.ok) return J({ error: "unauthorized" }, 401);
  if (!(await allowed(auth))) return J({ error: "rate_limited" }, 429);
  let body: { frames?: string[] };
  try { body = await req.json(); } catch { return J({ error: "bad_json" }, 400); }
  const frames = (body.frames ?? []).slice(0, 6).filter((f) => typeof f === "string" && f.length < 1_500_000);
  if (!frames.length) return J({ error: "no_frames" }, 400);
  // Face++ free keys allow 1 concurrent call: run frames one after another and retry on a concurrency/QPS refusal.
  const out: any[] = [];
  for (const b64 of frames) {
    let res: any = { error: "unknown" };
    for (let attempt = 0; attempt < 4; attempt++) {
      const fd = new FormData();
      fd.set("api_key", key); fd.set("api_secret", secret);
      fd.set("image_base64", b64);
      fd.set("return_attributes", "gender,facequality,blur,headpose,mouthstatus");
      try {
        const r = await fetch("https://api-us.faceplusplus.com/facepp/v3/detect", { method: "POST", body: fd });
        if (r.status === 403 || r.status === 429) { res = { error: r.status }; await new Promise((ok) => setTimeout(ok, 700 * (attempt + 1))); continue; }
        if (!r.ok) { res = { error: r.status }; break; }
        const j = await r.json();
        res = {
          faces: (j.faces ?? []).map((f: any) => ({
            gender: f.attributes?.gender?.value ?? null,
            quality: f.attributes?.facequality?.value ?? null,
            blur: f.attributes?.blur?.blurness?.value ?? null,
            yaw: f.attributes?.headpose?.yaw_angle ?? null,
            w: f.face_rectangle?.width ?? null,
            mouth_occ: Math.max(f.attributes?.mouthstatus?.other_occlusion ?? 0, f.attributes?.mouthstatus?.surgical_mask_or_respirator ?? 0),
          })),
        };
        break;
      } catch (_) { res = { error: "net" }; }
    }
    out.push(res);
  }
  const okCount = out.filter((o: any) => !o.error).length;
  console.log("face-gender frames=" + out.length + " ok=" + okCount + " errors=" + JSON.stringify(out.filter((o: any) => o.error).map((o: any) => o.error)));
  // available when at least one frame was judged; failed frames are reported per frame
  return J({ available: okCount > 0, frames: out });
});
