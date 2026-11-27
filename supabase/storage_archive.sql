-- Allow videos stored on archive.org (large uploads). Idempotent.
ALTER TABLE videos DROP CONSTRAINT IF EXISTS videos_storage_type_check;
ALTER TABLE videos ADD CONSTRAINT videos_storage_type_check CHECK (storage_type IN ('cloudinary', 'supabase', 'demo', 'archive'));
