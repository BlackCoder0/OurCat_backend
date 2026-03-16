INSERT INTO organizations (id, name, description)
SELECT 1, '校园流浪猫救助组织', '校园流浪猫综合救助平台默认组织，加入后成为志愿者（2级用户），可参与救助任务指派与申领。'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM organizations);
