-- Strongest music score (0..1) found by the on-device check; used to demote borderline-music videos in ranking. Idempotent. Applied live.
alter table public.videos add column if not exists music_score real not null default 0;
alter table public.videos drop constraint if exists videos_music_score_chk;
alter table public.videos add constraint videos_music_score_chk check (music_score between 0 and 1);
