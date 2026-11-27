-- Recommendations v1: per-user signals + taste. Own rows only. Idempotent. Applied live.
begin;
create table if not exists public.user_video_signals (
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  video_id text not null references public.videos(id) on delete cascade,
  watch_ms integer not null default 0 check (watch_ms >= 0 and watch_ms <= 36000000),
  completed boolean not null default false,
  skipped boolean not null default false,
  not_interested boolean not null default false,
  views_count integer not null default 1 check (views_count between 0 and 10000),
  updated_at timestamptz not null default now(),
  primary key (user_id, video_id)
);
create index if not exists uvs_user_updated_idx on public.user_video_signals (user_id, updated_at desc, video_id desc);

create table if not exists public.user_taste (
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  key text not null check (char_length(key) between 4 and 80),
  score real not null check (score between -10 and 10),
  updated_at timestamptz not null default now(),
  primary key (user_id, key)
);

alter table public.user_video_signals enable row level security;
alter table public.user_taste enable row level security;
do $$ declare t text; begin
  foreach t in array array['user_video_signals','user_taste'] loop
    execute format('drop policy if exists %I on public.%I', t||'_own_sel', t);
    execute format('drop policy if exists %I on public.%I', t||'_own_ins', t);
    execute format('drop policy if exists %I on public.%I', t||'_own_upd', t);
    execute format('create policy %I on public.%I for select to authenticated using (user_id = auth.uid())', t||'_own_sel', t);
    execute format('create policy %I on public.%I for insert to authenticated with check (user_id = auth.uid())', t||'_own_ins', t);
    execute format('create policy %I on public.%I for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid())', t||'_own_upd', t);
    execute format('revoke all on public.%I from anon, authenticated', t);
    execute format('grant select on public.%I to authenticated', t);
  end loop;
end $$;
grant insert (user_id, video_id, watch_ms, completed, skipped, not_interested, views_count, updated_at) on public.user_video_signals to authenticated;
grant update (watch_ms, completed, skipped, not_interested, views_count, updated_at) on public.user_video_signals to authenticated;
grant insert (user_id, key, score, updated_at) on public.user_taste to authenticated;
grant update (score, updated_at) on public.user_taste to authenticated;

-- Config: curated creators for interested / not-interested cards (empty = any creator with few videos)
insert into public.backend_settings (key, value, description, is_public)
values ('reco_config', '{"curated_user_ids":[],"new_creator_max_videos":2,"explore_share":0.15,"max_same_creator_streak":2}'::jsonb, 'Recommendation config', true)
on conflict (key) do nothing;
commit;

-- merge-duplicates upserts SET every supplied column, including the key; RLS (user_id = auth.uid()) still pins rows to the caller.
grant update (user_id, video_id) on public.user_video_signals to authenticated;
grant update (user_id, key) on public.user_taste to authenticated;
