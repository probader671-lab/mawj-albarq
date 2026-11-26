-- unique immutable public ID for every account
alter table public.profiles add column if not exists public_id text;
create or replace function public.gen_public_id() returns text language plpgsql as $$
declare chars text := 'ABCDEFGHJKMNPQRSTUVWXYZ23456789'; r text; i int; tries int := 0;
begin
  loop
    r := 'MB-'; for i in 1..8 loop r := r || substr(chars, 1 + floor(random()*length(chars))::int, 1); end loop;
    exit when not exists (select 1 from public.profiles where public_id = r);
    tries := tries + 1; if tries > 20 then raise exception 'public_id generation failed'; end if;
  end loop;
  return r;
end $$;
update public.profiles set public_id = public.gen_public_id() where public_id is null;
create unique index if not exists profiles_public_id_key on public.profiles(public_id);
alter table public.profiles alter column public_id set not null;
create or replace function public.profiles_public_id_guard() returns trigger language plpgsql as $$
begin
  if tg_op = 'INSERT' then
    new.public_id := public.gen_public_id();  -- always server-generated, client value ignored
  elsif new.public_id is distinct from old.public_id then
    raise exception 'public_id is immutable';
  end if;
  return new;
end $$;
drop trigger if exists trg_profiles_public_id on public.profiles;
create trigger trg_profiles_public_id before insert or update on public.profiles for each row execute function public.profiles_public_id_guard();
