-- 添加救助活动ID字段到广场帖子表
ALTER TABLE square_posts ADD COLUMN rescue_activity_id BIGINT;
CREATE INDEX idx_square_posts_rescue_activity ON square_posts(rescue_activity_id);
