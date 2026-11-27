-- Real view counting: every watch counts, rewatches count again.
-- Called by signed-in users only; capped per user+video per hour so it can't be spammed.
create or replace function public.count_view(p_video_id text, p_count int default 1, p_watched_ms int default 0)
returns int language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_uid uuid := auth.uid();
  v_n int := least(greatest(coalesce(p_count,1),1), 20);
  v_recent int;
begin
  if v_uid is null then return 0; end if;
  select coalesce(sum(greatest(watched_duration,1)),0) into v_recent from public.video_views
    where video_id = p_video_id and user_id = v_uid and created_at > now() - interval '1 hour';
  -- watched_duration stores the number of views counted in that call
  if v_recent >= 60 then return 0; end if;
  insert into public.video_views(video_id, user_id, watched_duration, total_duration, watch_percent)
    values (p_video_id, v_uid, v_n, greatest(p_watched_ms,0)/1000, 0);
  update public.videos set views = greatest(coalesce(views,0) + v_n, 0), real_views = greatest(coalesce(real_views,0) + v_n, 0)
    where id = p_video_id and deleted = false;
  return v_n;
end $$;
revoke all on function public.count_view(text,int,int) from public, anon;
grant execute on function public.count_view(text,int,int) to authenticated;

-- Remove the seeded demo landscape videos (owned by the demo user)
update public.videos set deleted = true where user_id = '00000000-0000-4000-8000-000000000001' and width > height;
