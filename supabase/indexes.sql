-- Mawj Al-Barq: indexes for the app's hot paths. Idempotent (IF NOT EXISTS). Applied live.
-- Existing indexes already cover: comments(video_id,created_at), likes(video_id), saved_videos(user_id,created_at),
-- profiles.public_id (unique), videos(user_id,created_at), notifications(user_id,created_at).

-- Home feed: WHERE status='ready' AND deleted=false ORDER BY created_at DESC, id DESC (keyset). The older partial
-- index uses COALESCE(deleted,false), which the planner cannot match against deleted=false, so add an exact one.
CREATE INDEX IF NOT EXISTS videos_feed_keyset_idx ON public.videos (created_at DESC, id DESC) WHERE status = 'ready' AND deleted = false;
-- Profile video list
CREATE INDEX IF NOT EXISTS videos_user_keyset_idx ON public.videos (user_id, created_at DESC, id DESC) WHERE status = 'ready' AND deleted = false;
-- Comments, keyset pages
CREATE INDEX IF NOT EXISTS comments_video_keyset_idx ON public.comments (video_id, created_at DESC, id DESC) WHERE is_deleted = false;
CREATE INDEX IF NOT EXISTS post_comments_keyset_idx ON public.post_comments (post_id, created_at DESC, id DESC) WHERE is_deleted = false;
-- Posts feed
CREATE INDEX IF NOT EXISTS social_posts_keyset_idx ON public.social_posts (pinned DESC, created_at DESC, id DESC) WHERE is_deleted = false;
-- Notifications: keyset page and unread count
CREATE INDEX IF NOT EXISTS notif_user_keyset_idx ON public.notifications (user_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS notif_user_unread_idx ON public.notifications (user_id) WHERE is_read = false;
-- Reverse lookups (counts / followers; primary keys lead with the other column)
CREATE INDEX IF NOT EXISTS follows_following_idx ON public.follows (following_id, follower_id);
CREATE INDEX IF NOT EXISTS post_likes_post_idx ON public.post_likes (post_id);
CREATE INDEX IF NOT EXISTS comment_likes_comment_idx ON public.comment_likes (comment_id);
CREATE INDEX IF NOT EXISTS story_likes_story_idx ON public.story_likes (story_id);
CREATE INDEX IF NOT EXISTS saved_posts_post_idx ON public.saved_posts (post_id);
CREATE INDEX IF NOT EXISTS saved_videos_video_idx ON public.saved_videos (video_id);
ANALYZE public.videos; ANALYZE public.comments; ANALYZE public.notifications; ANALYZE public.follows; ANALYZE public.social_posts;
