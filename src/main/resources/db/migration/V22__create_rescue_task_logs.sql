CREATE TABLE IF NOT EXISTS rescue_task_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rescue_task_id BIGINT NOT NULL,
    user_id BIGINT NULL,
    log_type VARCHAR(32) NOT NULL DEFAULT 'progress',
    content TEXT NOT NULL,
    images VARCHAR(2000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (rescue_task_id) REFERENCES rescue_tasks(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_rescue_task_logs_task ON rescue_task_logs(rescue_task_id);
CREATE INDEX idx_rescue_task_logs_created ON rescue_task_logs(created_at);
