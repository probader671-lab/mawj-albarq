// Proxy to Face++ Detect. Holds FACEPP_KEY / FACEPP_SECRET as function secrets.
// Input: { frames: [base64 jpeg, ...] } (max 3). Requires a logged-in user JWT.
// Output: { available: bool, frames: [{faces:[{gender,quality,blur,yaw}]}] }
const CORS = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, content-type, apikey", "Content-Type": "application/json" };
const J = (o: unknown, s = 200) => new Response(JSON.stringify(o), { status: s, headers: CORS });

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  const key = Deno.env.get("FACEPP_KEY"), secret = Deno.env.get("FACEPP_SECRET");
  if (!key || !secret) return J({ available: false, reason: "not_configured" });
  // Require a real signed-in user (not just the anon key)
  const auth = req.headers.get("authorization") ?? "";
  const ures = await fetch(`${Deno.env.get("SUPABASE_URL")}/auth/v1/user`, { headers: { Authorization: auth, apikey: Deno.env.get("SUPABASE_ANON_KEY") ?? "" } });
  if (!ures.ok) return J({ error: "unauthorized" }, 401);
  let body: { frames?: string[] };
  try { body = await req.json(); } catch { return J({ error: "bad_json" }, 400); }
  const frames = (body.frames ?? []).slice(0, 3).filter((f) => typeof f === "string" && f.length < 1_500_000);
  if (!frames.length) return J({ error: "no_frames" }, 400);
  const out = await Promise.all(frames.map(async (b64) => {
    const fd = new FormData();
    fd.set("api_key", key); fd.set("api_secret", secret);
    fd.set("image_base64", b64);
    fd.set("return_attributes", "gender,facequality,blur,headpose");
    try {
      const r = await fetch("https://api-us.faceplusplus.com/facepp/v3/detect", { method: "POST", body: fd });
      if (!r.ok) return { error: r.status };
      const j = await r.json();
      return {
        faces: (j.faces ?? []).map((f: any) => ({
          gender: f.attributes?.gender?.value ?? null,
          quality: f.attributes?.facequality?.value ?? null,
          blur: f.attributes?.blur?.blurness?.value ?? null,
          yaw: f.attributes?.headpose?.yaw_angle ?? null,
          w: f.face_rectangle?.width ?? null,
        })),
      };
    } catch (_) { return { error: "net" }; }
  }));
  const failed = out.some((o: any) => o.error);
  return J({ available: !failed, frames: out });
});
