-- 救助任务：指派或申领，完成时可填汇报。见主计划「三、3.3 任务指派与主动申领」
CREATE TABLE IF NOT EXISTS rescue_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rescue_activity_id BIGINT NOT NULL,
    assignee_user_id BIGINT NULL,
    assigner_user_id BIGINT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'assigned',
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    completion_note TEXT,
    completion_images VARCHAR(2000),
    FOREIGN KEY (rescue_activity_id) REFERENCES rescue_activities(id) ON DELETE CASCADE,
    FOREIGN KEY (assignee_user_id) REFERENCES users(id) ON DELETE SET NULL,
    FOREIGN KEY (assigner_user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_rescue_tasks_activity ON rescue_tasks(rescue_activity_id);
CREATE INDEX idx_rescue_tasks_assignee ON rescue_tasks(assignee_user_id);
