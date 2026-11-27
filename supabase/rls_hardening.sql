-- RLS hardening audit, Nov 27 2026. Idempotent.
begin;

-- 1) subscription_codes: any signed-in user could read every active code. Redeeming goes through activate_pro_code (security definer).
drop policy if exists "Authenticated users can check active codes" on public.subscription_codes;
revoke all on public.subscription_codes from anon;

-- 2) Remove dangerous table privileges everywhere (RLS does not cover TRUNCATE etc.)
revoke truncate, references, trigger on all tables in schema public from anon, authenticated;
-- anon may only INSERT where a policy deliberately allows anonymous inserts
revoke insert, update, delete on all tables in schema public from anon;
grant insert on public.reports, public.shares, public.story_views, public.video_views, public.suggestions to anon;

-- 3) profiles: users could set is_admin / is_pro / verified / banned / counters on themselves, and ban/warning details were public.
revoke all on public.profiles from anon, authenticated;
grant select (id, full_name, avatar_url, banner_url, bio, website, location, theme_accent_argb, theme_secondary_argb,
              theme_preset, followers_count, following_count, verified, created_at, updated_at, public_id)
  on public.profiles to anon, authenticated;
grant insert (id, full_name, avatar_url, banner_url, bio, website, location) on public.profiles to authenticated;
grant update (full_name, avatar_url, banner_url, bio, website, location, theme_accent_argb, theme_secondary_argb,
              theme_preset, email_notifications, dark_mode) on public.profiles to authenticated;

-- 4) videos: owners could edit views/likes counters, ban_status and temp_ban_until (self-unban) via PATCH.
revoke all on public.videos from anon, authenticated;
grant select on public.videos to anon, authenticated;
grant insert (user_id, title, description, video_url, thumbnail_url, storage_type, media_public_id, mime_type, size_bytes,
              duration, width, height, is_vertical, is_short, status) on public.videos to authenticated;
grant update (title, description, deleted) on public.videos to authenticated;
grant delete on public.videos to authenticated;

-- 5) other owner-editable tables: allow only content columns
revoke update on public.comments, public.social_posts, public.stories from authenticated;
grant update (content) on public.comments to authenticated;
grant update (content) on public.social_posts to authenticated;
grant update (caption) on public.stories to authenticated;
revoke insert, update, delete on public.daily_upload_counts from authenticated; -- written by check_upload_limit (security definer)

-- 6) stop identity spoofing on anonymous-insert tables
drop policy if exists "Anyone can submit a report" on public.reports;
create policy "Anyone can submit a report" on public.reports for insert
  with check (reporter_user_id is null or reporter_user_id = auth.uid());
drop policy if exists "Anyone can submit a suggestion" on public.suggestions;
create policy "Anyone can submit a suggestion" on public.suggestions for insert
  with check (user_id is null or user_id = auth.uid());

-- 7) functions: callable only by who needs them
revoke execute on all functions in schema public from public, anon, authenticated;
grant execute on function public.is_admin() to anon, authenticated;
grant execute on function public.gen_public_id() to anon, authenticated;
grant execute on function public.record_video_view(text, uuid, text, text, text, integer, integer) to anon, authenticated;
grant execute on function public.register_views_batch(jsonb) to anon, authenticated;
grant execute on function public.increment_views(text) to anon, authenticated;
grant execute on function
  public.activate_pro_code(uuid,text), public.get_user_status(uuid), public.check_upload_limit(uuid,text,bigint),
  public.get_storage_usage(), public.get_user_comment_count(uuid), public.is_banned_now(uuid),
  public.is_device_banned(text,text), public.record_login_event(uuid,text,text,text,text,text,boolean,text),
  public.record_device_identity(text,text,text,text,text,text,text,text,uuid),
  public.reserve_upload_session(uuid,bigint,text,text), public.finalize_upload_session(uuid,text,text,bigint),
  public.admin_ban_user(uuid,text,integer), public.admin_toggle_verify(uuid,boolean), public.admin_warn_user(uuid,text),
  public.create_pro_code(text,integer,integer), public.deactivate_pro_code(uuid)
to authenticated;  -- admin_* and *_pro_code check is_admin() inside

-- activate_pro_code took any user id: bind it to the caller. get_user_status leaked any user's status.
do $fix$
declare d text;
begin
  d := pg_get_functiondef('public.activate_pro_code(uuid,text)'::regprocedure);
  if position('auth.uid() IS DISTINCT FROM p_user_id' in d) = 0 then
    d := regexp_replace(d, E'\\mBEGIN\n', E'BEGIN\n    IF auth.uid() IS DISTINCT FROM p_user_id THEN RETURN jsonb_build_object(''success'', false, ''message'', ''غير مصرح''); END IF;\n');
    execute d;
  end if;
  d := pg_get_functiondef('public.get_user_status(uuid)'::regprocedure);
  if position('auth.uid() IS DISTINCT FROM p_user_id' in d) = 0 then
    d := regexp_replace(d, E'\\mBEGIN\n', E'BEGIN\n    IF auth.uid() IS DISTINCT FROM p_user_id AND NOT public.is_admin() THEN RETURN jsonb_build_object(''found'', false); END IF;\n');
    execute d;
  end if;
end $fix$;

commit;
