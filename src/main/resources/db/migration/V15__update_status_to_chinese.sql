-- V15: 将猫咪状态字段从英文 active 改为中文 活跃

-- 1. 将现有的 "active" 状态全部更新为 "活跃"
UPDATE cats SET status = '活跃' WHERE status = 'active';

-- 2. 修改表结构，将默认值设为 "活跃"
ALTER TABLE cats MODIFY COLUMN status VARCHAR(20) DEFAULT '活跃' COMMENT '状态: 活跃/失踪/收养/生病/已故';