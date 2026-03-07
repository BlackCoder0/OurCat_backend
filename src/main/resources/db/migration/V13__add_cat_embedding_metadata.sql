ALTER TABLE cats ADD COLUMN ai_embedding_model VARCHAR(100) DEFAULT NULL COMMENT 'embedding模型名';
ALTER TABLE cats ADD COLUMN ai_embedding_dim INT DEFAULT NULL COMMENT 'embedding维度';
ALTER TABLE cats ADD COLUMN ai_embedding_updated_at DATETIME DEFAULT NULL COMMENT 'embedding更新时间';
