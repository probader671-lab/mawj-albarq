-- Pre-publication moderation: statuses, admin review queue, admin notifications, Quran flag. Idempotent. Applied live.
begin;

alter table public.videos add column if not exists is_quran boolean not null default false;
grant insert (is_quran) on public.videos to authenticated;   -- owner sets it at upload; select is table-wide already

alter table public.videos drop constraint if exists videos_status_check;
alter table public.videos add constraint videos_status_check
  check (status = any (array['processing','ready','failed','pending_review','rejected']));

-- Clients may insert 'ready' or 'pending_review' only (anything else becomes 'ready' as before).
-- NOTE: moderation runs on the phone, so a modified client could skip it; the server cannot run the models.
create or replace function public.guard_media_urls() returns trigger language plpgsql as $$
begin
  if current_user in ('anon','authenticated') then
    if tg_table_name = 'videos' then
      if new.video_url !~ '^https://res\.cloudinary\.com/wuuhfgal/' or (new.thumbnail_url is not null and new.thumbnail_url !~ '^https://res\.cloudinary\.com/wuuhfgal/') then
        raise exception 'invalid media url';
      end if;
      if new.status is distinct from 'pending_review' then new.status := 'ready'; end if;
    end if;
  end if;
  return new;
end $$;

-- Once a video is pending_review or rejected, only an admin can change its status (no self-publish by PATCH).
create or replace function public.guard_video_status() returns trigger language plpgsql as $$
begin
  if current_user in ('anon','authenticated') and not public.is_admin() then
    if old.status in ('pending_review','rejected') and new.status is distinct from old.status then
      new.status := old.status;
    end if;
    if new.status in ('pending_review','rejected') and old.status = 'ready' then
      new.status := old.status;   -- owners cannot move live videos into review states either
    end if;
  end if;
  return new;
end $$;
drop trigger if exists trg_guard_video_status on public.videos;
create trigger trg_guard_video_status before update on public.videos for each row execute function public.guard_video_status();

create table if not exists public.moderation_queue (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  video_id text references public.videos(id) on delete cascade,
  media_public_id text,
  kind text not null check (kind in ('blocked','review')),
  reasons text[] not null default '{}',
  music_share real, music_max real, nsfw_max real, faces int,
  status text not null default 'pending' check (status in ('pending','approved','rejected','blocked')),
  created_at timestamptz not null default now(),
  reviewed_by uuid, reviewed_at timestamptz
);
create index if not exists moderation_queue_status_idx on public.moderation_queue (status, created_at desc, id desc);
create index if not exists moderation_queue_user_idx on public.moderation_queue (user_id, created_at desc);
create index if not exists moderation_queue_video_idx on public.moderation_queue (video_id);
alter table public.moderation_queue enable row level security;
drop policy if exists mq_ins on public.moderation_queue; drop policy if exists mq_sel on public.moderation_queue; drop policy if exists mq_upd on public.moderation_queue;
create policy mq_ins on public.moderation_queue for insert to authenticated with check (user_id = auth.uid());
create policy mq_sel on public.moderation_queue for select to authenticated using (user_id = auth.uid() or public.is_admin());
create policy mq_upd on public.moderation_queue for update to authenticated using (public.is_admin()) with check (public.is_admin());
revoke all on public.moderation_queue from anon, authenticated;
grant select on public.moderation_queue to authenticated;
grant insert (video_id, media_public_id, kind, reasons, music_share, music_max, nsfw_max, faces) on public.moderation_queue to authenticated;
grant update (status, reviewed_by, reviewed_at) on public.moderation_queue to authenticated;

-- Clients cannot choose the queue status: blocked -> 'blocked', review -> 'pending'. Rate limit 40/day.
create or replace function public.mq_guard() returns trigger language plpgsql as $$
begin
  if current_user in ('anon','authenticated') then
    new.status := case when new.kind = 'blocked' then 'blocked' else 'pending' end;
    new.user_id := auth.uid();
    if (select count(*) from public.moderation_queue where user_id = new.user_id and created_at > now() - interval '1 day') >= 40 then
      raise exception 'rate limit exceeded' using errcode = '53400';
    end if;
  end if;
  return new;
end $$;
drop trigger if exists trg_mq_guard on public.moderation_queue;
create trigger trg_mq_guard before insert on public.moderation_queue for each row execute function public.mq_guard();

-- Admin alert on every queue insert
create or replace function public.mq_notify_admins() returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into public.notifications (user_id, actor_id, type, video_id)
  select p.id, new.user_id, case when new.kind = 'blocked' then 'moderation_blocked' else 'moderation' end, new.video_id
  from public.profiles p where p.is_admin = true;
  return new;
end $$;
revoke all on function public.mq_notify_admins() from public, anon, authenticated;
drop trigger if exists trg_mq_notify on public.moderation_queue;
create trigger trg_mq_notify after insert on public.moderation_queue for each row execute function public.mq_notify_admins();

-- Tunable thresholds (read by the app at upload time; admins edit via SQL or Table editor)
insert into public.backend_settings (key, value, description, is_public)
values ('moderation_config', '{"music_block_share":0.25,"music_window_p":0.5,"music_review_max":0.5,"music_review_share":0.10,"music_review_p":0.25,"nsfw_block":0.90,"nsfw_block_multi":0.80,"nsfw_review":0.40,"face_min_width":0.06,"quran_max_music":0.15}'::jsonb,
        'On-device moderation thresholds', true)
on conflict (key) do nothing;
commit;

-- Videos awaiting review or rejected are visible only to their owner and admins (not through the public API).
begin;
drop policy if exists "Videos are viewable by everyone" on public.videos;
create policy "Videos are viewable by everyone" on public.videos for select using (
  ((COALESCE(deleted, false) = false) AND (COALESCE(ban_status, 'none') <> ALL (ARRAY['deleted','permanent']))
    AND ((temp_ban_until IS NULL) OR (temp_ban_until <= now())) AND status NOT IN ('pending_review','rejected'))
  OR (auth.uid() = user_id) OR is_admin());
commit;

-- Admins approve/reject by PATCHing status (they are the 'authenticated' role too); guard_video_status stops non-admins
-- from moving pending_review/rejected videos or putting live ones into review states.
grant update (status) on public.videos to authenticated;
