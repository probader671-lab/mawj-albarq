-- Device ban (Nov 27 2026). Idempotent. Device id = SHA-256 of (app salt + ANDROID_ID), computed on the phone.
begin;
create table if not exists public.device_accounts (
  device_hash text not null check (device_hash ~ '^[0-9a-f]{64}$'),
  user_id uuid not null,
  model text, os text,
  first_seen timestamptz not null default now(), last_seen timestamptz not null default now(),
  primary key (device_hash, user_id));
create index if not exists device_accounts_user_idx on public.device_accounts(user_id);
alter table public.device_accounts enable row level security;
revoke all on public.device_accounts from anon, authenticated;

-- Called by the app after every sign-in / app start. Records the device for this account.
-- If the device is banned, the account is banned too. Returns true when blocked.
create or replace function public.register_device(p_hash text, p_model text, p_os text) returns boolean
language plpgsql security definer set search_path = public, pg_temp as $$
declare uid uuid := auth.uid(); banned boolean;
begin
  if uid is null or p_hash !~ '^[0-9a-f]{64}$' then return false; end if;
  insert into public.device_accounts(device_hash, user_id, model, os)
  values (p_hash, uid, left(p_model,60), left(p_os,30))
  on conflict (device_hash, user_id) do update set last_seen = now(), model = excluded.model, os = excluded.os;
  banned := public.is_device_banned(p_hash, null);
  if banned then
    update public.profiles set is_banned = true, banned = true, ban_expires_at = null, ban_reason = coalesce(ban_reason,'banned device') where id = uid;
  end if;
  return banned or coalesce(public.is_banned_now(uid), false);
end $$;
revoke all on function public.register_device(text,text,text) from public, anon;
grant execute on function public.register_device(text,text,text) to authenticated;

-- Admin: ban every device an account has used, and every account seen on those devices.
create or replace function public.admin_ban_user_devices(p_target uuid, p_reason text, p_days int default 0) returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare h text; n int := 0; exp timestamptz;
begin
  if not public.is_admin() then return jsonb_build_object('success', false, 'message', 'not allowed'); end if;
  if p_days > 0 then exp := now() + (p_days || ' days')::interval; end if;
  for h in select distinct device_hash from public.device_accounts where user_id = p_target loop
    insert into public.banned_devices(fingerprint, reason, banned_by, expires_at) values (h, p_reason, auth.uid(), exp)
    on conflict (fingerprint) do update set reason = excluded.reason, expires_at = excluded.expires_at, banned_by = auth.uid();
    n := n + 1;
  end loop;
  update public.profiles set is_banned = true, banned = true, ban_expires_at = exp, ban_reason = p_reason, banned_by = auth.uid()
   where id = p_target or id in (select user_id from public.device_accounts where device_hash in (select device_hash from public.device_accounts where user_id = p_target));
  insert into public.admin_actions(admin_id, target_user_id, action, reason, meta) values (auth.uid(), p_target, 'ban_device', p_reason, jsonb_build_object('devices', n, 'days', p_days));
  return jsonb_build_object('success', true, 'devices', n);
end $$;
revoke all on function public.admin_ban_user_devices(uuid,text,int) from public, anon;
grant execute on function public.admin_ban_user_devices(uuid,text,int) to authenticated;

-- Banned accounts cannot create content (server side, not just in the app).
create or replace function public.block_banned() returns trigger language plpgsql security definer set search_path = public, pg_temp as $$
begin
  if current_user in ('anon','authenticated') and coalesce(public.is_banned_now(auth.uid()), false) then
    raise exception 'account banned' using errcode = '42501';
  end if;
  return new;
end $$;
do $$ declare t text; begin
  foreach t in array array['videos','comments','social_posts','stories','post_comments','suggestions','reports'] loop
    execute format('drop trigger if exists trg_block_banned on public.%I', t);
    execute format('create trigger trg_block_banned before insert on public.%I for each row execute function public.block_banned()', t);
  end loop; end $$;

-- Edge-function limiter also refuses banned accounts and banned devices.
create or replace function public.rl_hit(p_fn text, p_max int, p_window_sec int) returns boolean
language plpgsql security definer set search_path = public as $$
declare uid uuid := auth.uid(); n int;
begin
  if uid is null then return false; end if;
  if coalesce(public.is_banned_now(uid), false) then return false; end if;
  if exists (select 1 from public.device_accounts d join public.banned_devices b on b.fingerprint = d.device_hash and (b.expires_at is null or b.expires_at > now()) where d.user_id = uid) then return false; end if;
  select count(*) into n from public.api_rate_limit where user_id = uid and fn = p_fn and at > now() - make_interval(secs => p_window_sec);
  if n >= p_max then return false; end if;
  insert into public.api_rate_limit(user_id, fn) values (uid, p_fn);
  if random() < 0.02 then delete from public.api_rate_limit where at < now() - interval '2 days'; end if;
  return true;
end $$;
commit;
