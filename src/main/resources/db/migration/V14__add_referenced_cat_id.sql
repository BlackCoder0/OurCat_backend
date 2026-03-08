-- V14: 为帖子和广场添加关联猫咪字段
ALTER TABLE posts ADD COLUMN referenced_cat_id BIGINT DEFAULT NULL COMMENT '关联的猫咪档案ID';
ALTER TABLE square_posts ADD COLUMN referenced_cat_id BIGINT DEFAULT NULL COMMENT '关联的猫咪档案ID';
