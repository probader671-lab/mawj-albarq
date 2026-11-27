-- Followers get a 'new_video' notification when a followed creator's video becomes ready (published). Idempotent.
create or replace function public.notify_new_video() returns trigger
language plpgsql security definer set search_path to 'public' as $$
begin
  if new.status = 'ready' and (tg_op = 'INSERT' or old.status is distinct from 'ready') then
    insert into public.notifications(user_id, actor_id, type, video_id)
      select f.follower_id, new.user_id, 'new_video', new.id from public.follows f where f.following_id = new.user_id and f.follower_id <> new.user_id;
  end if;
  return new;
end $$;
drop trigger if exists trg_notify_new_video on public.videos;
create trigger trg_notify_new_video after insert or update of status on public.videos for each row execute function public.notify_new_video();
