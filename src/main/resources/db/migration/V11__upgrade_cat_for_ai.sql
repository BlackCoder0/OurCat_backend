-- V11: 升级猫咪表结构，支持 AI 识别和档案管理

-- 升级 cats 表
ALTER TABLE cats ADD COLUMN name VARCHAR(50) DEFAULT NULL COMMENT '猫咪昵称';
ALTER TABLE cats ADD COLUMN status VARCHAR(20) DEFAULT 'active' COMMENT '状态: active/missing/adopted';
ALTER TABLE cats ADD COLUMN primary_image_url VARCHAR(500) DEFAULT NULL COMMENT '主图URL';
ALTER TABLE cats ADD COLUMN report_count INT DEFAULT 0 COMMENT '关联上报数';
ALTER TABLE cats ADD COLUMN created_at DATETIME DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE cats ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- 升级 cat_reports 表
ALTER TABLE cat_reports ADD COLUMN match_confidence FLOAT DEFAULT NULL COMMENT 'AI匹配置信度';
ALTER TABLE cat_reports ADD COLUMN confirmed TINYINT(1) DEFAULT 0 COMMENT '用户已确认归属';
ALTER TABLE cat_reports ADD COLUMN ai_suggested_cat_id BIGINT DEFAULT NULL COMMENT 'AI建议的猫咪ID';

-- 为已有数据设置默认的 created_at（使用 report_time 的最早时间）
UPDATE cats c SET c.created_at = (
    SELECT MIN(cr.report_time) FROM cat_reports cr WHERE cr.cat_id = c.id
) WHERE c.created_at IS NULL;

-- 更新 report_count
UPDATE cats c SET c.report_count = (
    SELECT COUNT(*) FROM cat_reports cr WHERE cr.cat_id = c.id
);

-- 为有图片的猫咪设置主图（取最新上报的图片）
UPDATE cats c SET c.primary_image_url = (
    SELECT cr.image_url FROM cat_reports cr 
    WHERE cr.cat_id = c.id AND cr.image_url IS NOT NULL 
    ORDER BY cr.report_time DESC LIMIT 1
) WHERE c.primary_image_url IS NULL;
