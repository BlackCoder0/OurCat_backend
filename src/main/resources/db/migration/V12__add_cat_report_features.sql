-- V12: 给 cat_reports 表添加特征字段，用于匹配算法

ALTER TABLE cat_reports ADD COLUMN color VARCHAR(100) DEFAULT NULL;
ALTER TABLE cat_reports ADD COLUMN feature VARCHAR(200) DEFAULT NULL;
ALTER TABLE cat_reports ADD COLUMN personality VARCHAR(200) DEFAULT NULL;

-- 为地理位置查询添加索引优化
CREATE INDEX idx_cat_reports_location ON cat_reports(lat, lng);
