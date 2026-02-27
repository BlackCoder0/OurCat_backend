-- Allow long image URL JSON (e.g. many OSS URLs) to avoid 500 on create post
ALTER TABLE posts MODIFY COLUMN images TEXT;
