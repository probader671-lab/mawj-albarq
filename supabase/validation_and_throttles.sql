-- Server-side validation + throttles (Nov 27 2026). Idempotent. NOT VALID = applies to new/updated rows only.
begin;

alter table public.videos drop constraint if exists videos_input_chk;
alter table public.videos add constraint videos_input_chk check (
  char_length(btrim(title)) between 1 and 150
  and (description is null or char_length(description) <= 2000)
  and (duration is null or duration between 0 and 36000)
  and (size_bytes is null or size_bytes between 0 and 524288000)
  and (width is null or width between 0 and 10000) and (height is null or height between 0 and 10000)
) not valid;

alter table public.comments drop constraint if exists comments_input_chk;
alter table public.comments add constraint comments_input_chk check (char_length(btrim(content)) between 1 and 1000) not valid;
alter table public.social_posts drop constraint if exists social_posts_input_chk;
alter table public.social_posts add constraint social_posts_input_chk check (char_length(content) <= 3000) not valid;
alter table public.stories drop constraint if exists stories_input_chk;
alter table public.stories add constraint stories_input_chk check (caption is null or char_length(caption) <= 300) not valid;
alter table public.profiles drop constraint if exists profiles_input_chk;
alter table public.profiles add constraint profiles_input_chk check (
  (full_name is null or char_length(btrim(full_name)) between 1 and 60)
  and (bio is null or char_length(bio) <= 300)
  and (website is null or (char_length(website) <= 200 and website ~* '^https?://'))
) not valid;

-- Only our Cloudinary account / Supabase storage may be referenced as media by clients
create or replace function public.guard_media_urls() returns trigger language plpgsql as $$
begin
  if current_user in ('anon','authenticated') then
    if tg_table_name = 'videos' then
      if new.video_url !~ '^https://res\.cloudinary\.com/wuuhfgal/' or (new.thumbnail_url is not null and new.thumbnail_url !~ '^https://res\.cloudinary\.com/wuuhfgal/') then
        raise exception 'invalid media url';
      end if;
      new.status := 'ready'; -- clients cannot choose anything else
    end if;
  end if;
  return new;
end $$;
drop trigger if exists trg_guard_media_urls on public.videos;
create trigger trg_guard_media_urls before insert on public.videos for each row execute function public.guard_media_urls();

-- Per-user insert rate limits
create or replace function public.throttle_inserts() returns trigger language plpgsql as $$
declare lim int := tg_argv[0]::int; win interval := tg_argv[1]::interval; n int;
begin
  if current_user in ('anon','authenticated') then
    execute format('select count(*) from public.%I where user_id = $1 and created_at > now() - $2', tg_table_name)
      into n using new.user_id, win;
    if n >= lim then raise exception 'rate limit exceeded' using errcode = '53400'; end if;
  end if;
  return new;
end $$;
drop trigger if exists trg_throttle on public.videos;       create trigger trg_throttle before insert on public.videos       for each row execute function public.throttle_inserts(30, '1 day');
drop trigger if exists trg_throttle on public.comments;     create trigger trg_throttle before insert on public.comments     for each row execute function public.throttle_inserts(20, '1 minute');
drop trigger if exists trg_throttle on public.social_posts; create trigger trg_throttle before insert on public.social_posts for each row execute function public.throttle_inserts(20, '1 hour');
drop trigger if exists trg_throttle on public.stories;      create trigger trg_throttle before insert on public.stories      for each row execute function public.throttle_inserts(30, '1 day');

-- Pro-code guessing: 5 attempts per 15 minutes per user
create table if not exists public.pro_code_attempts (id bigserial primary key, user_id uuid not null, at timestamptz not null default now());
alter table public.pro_code_attempts enable row level security;
revoke all on public.pro_code_attempts from anon, authenticated;
do $fix$
declare d text;
begin
  d := pg_get_functiondef('public.activate_pro_code(uuid,text)'::regprocedure);
  if position('pro_code_attempts' in d) = 0 then
    d := replace(d, E'    SELECT * INTO v_code FROM public.subscription_codes',
      E'    IF (SELECT count(*) FROM public.pro_code_attempts WHERE user_id = p_user_id AND at > now() - interval ''15 minutes'') >= 5 THEN\n        RETURN jsonb_build_object(''success'', false, ''message'', ''محاولات كثيرة، حاول لاحقاً'');\n    END IF;\n    INSERT INTO public.pro_code_attempts (user_id) VALUES (p_user_id);\n    SELECT * INTO v_code FROM public.subscription_codes');
    execute d;
  end if;
end $fix$;

commit;

-- Banner choice is stored in profiles.banner_url: empty, "preset:<id>", or an image on our Cloudinary account.
alter table public.profiles drop constraint if exists profiles_banner_chk;
alter table public.profiles add constraint profiles_banner_chk check (
  banner_url is null or banner_url = '' or banner_url ~ '^preset:[a-z_]{1,30}$' or banner_url ~ '^https://res\.cloudinary\.com/wuuhfgal/'
) not valid;
