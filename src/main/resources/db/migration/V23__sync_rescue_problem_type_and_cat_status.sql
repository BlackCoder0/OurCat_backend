ALTER TABLE rescue_activities ADD COLUMN problem_type VARCHAR(32) DEFAULT NULL COMMENT '问题类型: 受伤/疾病/困境/其他';

UPDATE cats SET status = '疾病' WHERE status = '生病';

ALTER TABLE cats MODIFY COLUMN status VARCHAR(32) DEFAULT '活跃' COMMENT '状态: 活跃/失踪/收养/受伤/疾病/困境/其他/已故';
