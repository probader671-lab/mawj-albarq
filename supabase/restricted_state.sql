-- Post-publish restriction: a published video that fails a later check is hidden (status 'restricted') and the uploader is notified. Idempotent. Applied live.
alter table public.videos drop constraint if exists videos_status_check;
alter table public.videos add constraint videos_status_check check (status = any (array['processing','ready','failed','pending_review','rejected','restricted']));
alter table public.videos add column if not exists restricted_reason text;
alter table public.notifications add column if not exists reason text;

drop function if exists public.restrict_video(text, text);
create or replace function public.restrict_video(p_video text, p_reason text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v record;
begin
  select id, user_id into v from public.videos where id::text = p_video;
  if v.id is null then return jsonb_build_object('success', false); end if;
  if v.user_id <> auth.uid() and not coalesce((select is_admin from public.profiles where id = auth.uid()), false) then return jsonb_build_object('success', false); end if;
  update public.videos set status = 'restricted', restricted_reason = left(p_reason, 300) where id::text = p_video and status in ('ready','pending_review');
  if not found then return jsonb_build_object('success', false); end if;
  insert into public.notifications (user_id, actor_id, type, video_id, reason) values (v.user_id, null, 'restricted', p_video, left(p_reason, 300));
  return jsonb_build_object('success', true);
end $$;
revoke all on function public.restrict_video(text, text) from public, anon;
grant execute on function public.restrict_video(text, text) to authenticated;
