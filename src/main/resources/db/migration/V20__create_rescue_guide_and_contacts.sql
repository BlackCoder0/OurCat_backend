-- 救助指南与救助电话。见主计划「三、3.5 救助内容扩充」
CREATE TABLE IF NOT EXISTS rescue_guide (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rescue_contacts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(50) NOT NULL,
    description VARCHAR(200),
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO rescue_guide (title, content, sort_order) VALUES
('发现伤病猫时怎么办', '1. 不要贸然靠近，避免惊吓。\n2. 观察情况，记录位置与外貌。\n3. 在本平台发起救助活动或到广场发布救助需求。\n4. 联系校园救助组织或下方救助电话。', 1),
('联系谁', '可拨打下方「救助电话」中的校园救助组织电话，或在本平台申请加入组织后参与救助任务。', 2),
('注意事项', '• 注意自身安全，避免被咬伤抓伤。\n• 如需送医，可在地图页查看附近宠物医院。\n• 完成救助后请在任务中填写完成汇报，便于记录。', 3);

INSERT INTO rescue_contacts (name, phone, description, sort_order) VALUES
('校园流浪猫救助组织', '400-xxx-xxxx', '平台默认救助组织热线（示例，请替换为实际电话）', 1),
('紧急联系', '110', '遇紧急情况可报警', 2);
