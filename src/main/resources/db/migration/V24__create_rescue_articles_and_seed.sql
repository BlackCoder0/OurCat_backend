CREATE TABLE IF NOT EXISTS rescue_articles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    category VARCHAR(50),
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO rescue_articles (title, content, category, sort_order) VALUES
('TNR（抓捕-绝育-放归）新手流程', '这是一份面向校园/社区流浪猫的 TNR（Trap-Neuter-Return）新手流程概览：\n\n1) 准备与沟通\n- 先确定猫群活动范围与投喂时间，提前与物业/保安沟通，避免误会。\n- 准备捕猫笼、一次性手套、旧毛巾/遮挡布、标记贴纸、转运车。\n\n2) 捕捉（Trap）\n- 在固定点位放置捕猫笼，使用气味较强的食物诱导。\n- 捕获后第一时间用布遮挡，降低应激。\n\n3) 绝育与免疫（Neuter/Spay）\n- 送至有 TNR 经验的医院/机构进行绝育与基础免疫。\n- 可同时进行体表寄生虫处理与健康检查。\n\n4) 术后暂养（Recovery）\n- 保持安静、干燥、通风，观察精神状态、进食、排泄与伤口。\n- 若出现持续出血、明显肿胀、精神萎靡等情况，尽快复诊。\n\n5) 放归（Return）\n- 在原捕获地放归，避免异地放归导致迷失与冲突。\n\n参考链接：\n- Alley Cat Allies：TNR 步骤指南 https://www.alleycat.org/resources/how-to-help-community-cats-a-step-by-step-guide-to-trap-neuter-return/\n- Alley Cat Allies：TNR 概念与依据 https://www.alleycat.org/our-work/trap-neuter-return/', '权威流程', 1),
('TNR 术后护理要点（观察清单）', '术后护理的目标是降低感染与应激，确保猫在恢复后安全放归。\n\n建议准备：保暖垫/毯子、干净垫料、一次性手套、消毒用品、观察记录表。\n\n观察清单（每天至少 2 次）：\n- 精神状态：是否持续萎靡、呼吸是否异常\n- 进食饮水：是否完全拒食\n- 伤口情况：是否明显红肿、渗液、有异味\n- 排泄情况：是否长时间无排尿/无排便\n\n放归建议：\n- 一般建议至少过夜观察；具体以医院建议为准。\n- 仍然应在原捕获点放归。\n\n参考链接（PDF）：\n- ASPCA：TNR 术后护理 https://www.aspca.org/sites/default/files/en_-_caring_for_tnr_cats_after_surgery_2.2024.pdf', '护理', 2),
('社区猫基础科普：流浪猫、散养猫与“社区猫”', '在实际救助中，区分“亲人/可领养的流浪猫”和“更适合 TNR 的社区猫”很重要。\n\n判断参考：\n- 是否亲人：可触摸、会主动靠近人，通常更适合尝试领养\n- 是否长期定居：长期出现在同一片区域、与其他猫稳定共存，通常属于社区猫\n- 是否已有照护：定点投喂、精神状态良好，优先考虑 TNR 与持续照护\n\n参考链接：\n- ASPCA：Community Cats 科普 https://www.aspca.org/helping-shelters-people-pets/closer-look-community-cats', '科普', 3);
