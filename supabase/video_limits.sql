-- Server-side video limits: 5 uploads per rolling 24 h per user (admins exempt), title <= 120, description <= 300. Idempotent. Applied live.
create or replace function public.video_limits() returns trigger language plpgsql security definer set search_path = public as $$
declare n int; adm boolean;
begin
  if current_user in ('anon','authenticated') then
    if tg_op = 'INSERT' then
      select coalesce(is_admin, false) into adm from public.profiles where id = new.user_id;
      if not coalesce(adm, false) then
        select count(*) into n from public.videos where user_id = new.user_id and created_at > now() - interval '1 day';
        if n >= 5 then raise exception 'daily video limit reached (5)' using errcode = '53400'; end if;
      end if;
    end if;
    if tg_op = 'INSERT' or new.title is distinct from old.title then
      if char_length(btrim(new.title)) > 120 then raise exception 'title too long (max 120)' using errcode = '22001'; end if;
    end if;
    if tg_op = 'INSERT' or new.description is distinct from old.description then
      if new.description is not null and char_length(new.description) > 300 then raise exception 'description too long (max 300)' using errcode = '22001'; end if;
    end if;
  end if;
  return new;
end $$;
drop trigger if exists trg_video_limits on public.videos;
create trigger trg_video_limits before insert or update of title, description on public.videos for each row execute function public.video_limits();
