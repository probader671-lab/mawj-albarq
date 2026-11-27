-- Admin tools (Nov 27 2026). Idempotent. All functions refuse non-admins.
begin;

-- Ban an account for real: profile flag + auth-level ban (login and token refresh stop working) + hide its videos.
create or replace function public.admin_ban_user(p_target uuid, p_reason text, p_days integer default 0) returns jsonb
language plpgsql security definer set search_path = public, auth, pg_temp as $$
declare v_expiry timestamptz;
begin
  if not public.is_admin() then return jsonb_build_object('success', false, 'message', 'not allowed'); end if;
  if p_target = auth.uid() or exists (select 1 from public.profiles where id = p_target and coalesce(is_admin,false)) then
    return jsonb_build_object('success', false, 'message', 'cannot ban an admin');
  end if;
  if p_days > 0 then v_expiry := now() + (p_days || ' days')::interval; end if;
  update public.profiles set is_banned = true, banned = true, ban_expires_at = v_expiry, ban_reason = p_reason, banned_by = auth.uid() where id = p_target;
  update auth.users set banned_until = coalesce(v_expiry, now() + interval '100 years') where id = p_target;
  delete from auth.refresh_tokens where user_id = p_target::text;
  update public.videos set ban_status = 'permanent', ban_reason = 'owner banned', status = 'rejected' where user_id = p_target and status = 'ready';
  insert into public.admin_actions (admin_id, target_user_id, action, reason, duration_minutes, meta)
  values (auth.uid(), p_target, case when p_days = 0 then 'ban_permanent' else 'ban_temp' end, p_reason, case when p_days > 0 then p_days*24*60 end, jsonb_build_object('expires_at', v_expiry, 'days', p_days));
  return jsonb_build_object('success', true, 'expires_at', v_expiry);
end $$;

create or replace function public.admin_unban_user(p_target uuid) returns jsonb
language plpgsql security definer set search_path = public, auth, pg_temp as $$
begin
  if not public.is_admin() then return jsonb_build_object('success', false, 'message', 'not allowed'); end if;
  update public.profiles set is_banned = false, banned = false, ban_expires_at = null, ban_reason = null where id = p_target;
  update auth.users set banned_until = null where id = p_target;
  update public.videos set ban_status = 'none', ban_reason = null, status = 'ready' where user_id = p_target and ban_reason = 'owner banned';
  insert into public.admin_actions (admin_id, target_user_id, action) values (auth.uid(), p_target, 'unban');
  return jsonb_build_object('success', true);
end $$;

-- Ban the account and every device it used (and accounts seen on those devices).
create or replace function public.admin_ban_user_devices(p_target uuid, p_reason text, p_days int default 0) returns jsonb
language plpgsql security definer set search_path = public, auth, pg_temp as $$
declare h text; n int := 0; exp timestamptz; u uuid;
begin
  if not public.is_admin() then return jsonb_build_object('success', false, 'message', 'not allowed'); end if;
  if p_days > 0 then exp := now() + (p_days || ' days')::interval; end if;
  for h in select distinct device_hash from public.device_accounts where user_id = p_target loop
    insert into public.banned_devices(fingerprint, reason, banned_by, expires_at) values (h, p_reason, auth.uid(), exp)
    on conflict (fingerprint) do update set reason = excluded.reason, expires_at = excluded.expires_at, banned_by = auth.uid();
    n := n + 1;
  end loop;
  for u in select distinct user_id from public.device_accounts where device_hash in (select device_hash from public.device_accounts where user_id = p_target) union select p_target loop
    perform public.admin_ban_user(u, p_reason, p_days);
  end loop;
  return jsonb_build_object('success', true, 'devices', n);
end $$;

-- Remove a video from every feed (reversible: row is kept).
create or replace function public.admin_remove_video(p_video_id text, p_reason text) returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
begin
  if not public.is_admin() then return jsonb_build_object('success', false, 'message', 'not allowed'); end if;
  update public.videos set ban_status = 'permanent', ban_reason = left(p_reason, 200), status = 'rejected', deleted = true where id = p_video_id;
  if not found then return jsonb_build_object('success', false, 'message', 'not found'); end if;
  insert into public.admin_actions (admin_id, target_video_id, action, reason) values (auth.uid(), p_video_id, 'ban_video_permanent', p_reason);
  return jsonb_build_object('success', true);
end $$;

create or replace function public.admin_delete_comment(p_comment_id uuid) returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
begin
  if not public.is_admin() then return jsonb_build_object('success', false, 'message', 'not allowed'); end if;
  update public.comments set is_deleted = true where id = p_comment_id;
  insert into public.admin_actions (admin_id, action, reason, meta) values (auth.uid(), 'delete_comment', null, jsonb_build_object('comment_id', p_comment_id));
  return jsonb_build_object('success', true);
end $$;

-- Suggestions: status shown to everyone, set by admins only. open = not received yet, reviewed = in progress, resolved = done.
create or replace function public.admin_set_suggestion(p_id uuid, p_status text) returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
begin
  if not public.is_admin() then return jsonb_build_object('success', false, 'message', 'not allowed'); end if;
  if p_status not in ('open','reviewed','resolved') then return jsonb_build_object('success', false, 'message', 'bad status'); end if;
  update public.suggestions set status = p_status where id = p_id;
  return jsonb_build_object('success', true);
end $$;

do $$ declare f text; begin
  foreach f in array array['admin_ban_user(uuid,text,integer)','admin_unban_user(uuid)','admin_ban_user_devices(uuid,text,integer)','admin_remove_video(text,text)','admin_delete_comment(uuid)','admin_set_suggestion(uuid,text)'] loop
    execute format('revoke all on function public.%s from public, anon', f);
    execute format('grant execute on function public.%s to authenticated', f);
  end loop; end $$;

-- Record the (hashed) IP seen by the server on each device registration. Raw IP is never stored.
alter table public.device_accounts add column if not exists ip_hash text;
create or replace function public.register_device(p_hash text, p_model text, p_os text) returns boolean
language plpgsql security definer set search_path = public, pg_temp as $$
declare uid uuid := auth.uid(); banned boolean; ip text;
begin
  if uid is null or p_hash !~ '^[0-9a-f]{64}$' then return false; end if;
  begin ip := split_part(coalesce(current_setting('request.headers', true)::json->>'x-forwarded-for', ''), ',', 1); exception when others then ip := ''; end;
  insert into public.device_accounts(device_hash, user_id, model, os, ip_hash)
  values (p_hash, uid, left(p_model,60), left(p_os,30), case when ip <> '' then encode(digest(ip || 'mawj-ip-v1', 'sha256'), 'hex') end)
  on conflict (device_hash, user_id) do update set last_seen = now(), model = excluded.model, os = excluded.os, ip_hash = coalesce(excluded.ip_hash, public.device_accounts.ip_hash);
  banned := public.is_device_banned(p_hash, null);
  if banned then
    update public.profiles set is_banned = true, banned = true, ban_expires_at = null, ban_reason = coalesce(ban_reason,'banned device') where id = uid;
  end if;
  return banned or coalesce(public.is_banned_now(uid), false);
end $$;
commit;
