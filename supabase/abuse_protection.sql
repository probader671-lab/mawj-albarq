-- Abuse protection (Nov 27 2026). Idempotent.
begin;
create table if not exists public.api_rate_limit (id bigserial primary key, user_id uuid not null, fn text not null, at timestamptz not null default now());
create index if not exists api_rate_limit_idx on public.api_rate_limit (user_id, fn, at desc);
alter table public.api_rate_limit enable row level security;
revoke all on public.api_rate_limit from anon, authenticated;

-- Returns true when the caller (auth.uid()) is still under the limit, and records the hit.
create or replace function public.rl_hit(p_fn text, p_max int, p_window_sec int) returns boolean
language plpgsql security definer set search_path = public as $$
declare uid uuid := auth.uid(); n int;
begin
  if uid is null then return false; end if;
  select count(*) into n from public.api_rate_limit where user_id = uid and fn = p_fn and at > now() - make_interval(secs => p_window_sec);
  if n >= p_max then return false; end if;
  insert into public.api_rate_limit(user_id, fn) values (uid, p_fn);
  if random() < 0.02 then delete from public.api_rate_limit where at < now() - interval '2 days'; end if;
  return true;
end $$;
revoke all on function public.rl_hit(text,int,int) from public, anon;
grant execute on function public.rl_hit(text,int,int) to authenticated;
commit;

-- Global signup circuit breaker: at most 100 new accounts per hour (stops fake-account floods).
create or replace function public.signup_breaker() returns trigger language plpgsql security definer set search_path=public,auth as $$
begin
  if (select count(*) from auth.users where created_at > now() - interval '1 hour') >= 100 then
    raise exception 'signup rate limit' using errcode='53400';
  end if;
  return new;
end $$;
drop trigger if exists trg_signup_breaker on auth.users;
create trigger trg_signup_breaker before insert on auth.users for each row execute function public.signup_breaker();
