-- 救助活动。见主计划「三、3.2 救助活动」；起点必须为某只猫或广场帖（cat_id/square_post_id）
CREATE TABLE IF NOT EXISTS rescue_activities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    cat_id BIGINT NULL,
    square_post_id BIGINT NULL,
    organization_id BIGINT NULL,
    urgency VARCHAR(32) NOT NULL DEFAULT 'normal',
    status VARCHAR(32) NOT NULL DEFAULT 'created',
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    FOREIGN KEY (cat_id) REFERENCES cats(id) ON DELETE SET NULL,
    FOREIGN KEY (square_post_id) REFERENCES square_posts(id) ON DELETE SET NULL,
    FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE SET NULL,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_rescue_activities_cat ON rescue_activities(cat_id);
CREATE INDEX idx_rescue_activities_status ON rescue_activities(status);
CREATE INDEX idx_rescue_activities_created_at ON rescue_activities(created_at DESC);
