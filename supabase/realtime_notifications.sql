-- Realtime for instant push: own notification rows are streamed to the app's foreground service (RLS n_sel limits rows to the owner).
alter publication supabase_realtime add table public.notifications;
