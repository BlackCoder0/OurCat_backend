# 从哪里读源码

先沿一次上报操作阅读：Android 的 `ReportCatDialogFragment → CatApi`，对应后端的 `CatController → CatService → CatReport / Cat → Repository`。

- [认证控制器](../src/main/java/com/ourcat/backend/controllers/AuthController.java)：验证码、注册、登录与 BCrypt。
- [猫咪服务](../src/main/java/com/ourcat/backend/services/CatService.java)：上报、匹配、确认档案。
- [救助服务](../src/main/java/com/ourcat/backend/services/RescueService.java)：任务分配、申领、日志和权限判断。
- [本地图片](../src/main/java/com/ourcat/backend/utils/LocalImageStorage.java)：上传票据、内容校验、持久化。
- [demo 初始化](../src/main/java/com/ourcat/backend/services/DemoDataInitializer.java)和[启动保护](../src/main/java/com/ourcat/backend/config/DemoProfileGuard.java)：本地入口怎样与生产配置隔离。

上报先保存记录，再执行匹配；响应不会直接给出所有匹配结果，需要查询上报详情。`performAsyncMatching()` 当前由同一个 Service 调用，不能因为有 `@Async` 就视为真正后台异步任务。综合阈值为 0.68，同时使用图片时还要求图片相似度至少 0.62；缺少图片向量时走文本匹配。文本匹配不等同图片 AI 识别。

生产数据库使用 MySQL 8 与 Flyway，迁移 SQL 位于 `src/main/resources/db/migration`。dev/demo 使用 H2 和 JPA 建表，不代表已复核完整 MySQL 迁移。升级前备份，不能靠删除迁移记录或清库修复问题。

## 数据关系

```
User ──┬── CatReport (上报者)        ← 谁上报了这只猫
       ├── Post / PostVote (论坛)    ← 谁发了帖子
       ├── RescueTask (执行者)       ← 谁在执行救助
       ├── SquarePost (广场)         ← 谁发了广播
       ├── Message (收发)           ← 消息发给谁
       └── OrganizationMember        ← 属于哪个组织

Cat ──┬── CatReport (上报记录)      ← 这只猫的所有目击记录
      └── RescueActivity (救助目标) ← 这只猫关联的救助活动

Organization ──┬── OrganizationMember  ← 组织有哪些成员
                └── RescueActivity     ← 组织发起了哪些救助

SquarePost ─────── RescueActivity     ← 广场救助帖关联的救助活动
```

### 核心表结构速览

| 表名 | 关键字段 | 说明 |
| :--- | :--- | :--- |
| `users` | `id, username, password(BCrypt), role, nickname, avatar_url` | 用户表，角色分普通用户/志愿者/管理员（`role` 为 1/2/3） |
| `cats` | `id, name, color, feature, personality, status, primary_image_url, ai_embedding` | 猫咪档案表，`ai_embedding` 存储图片特征向量用于以图搜猫 |
| `cat_reports` | `id, lat, lng, report_time, image_url, description, cat_id, user_id, match_confidence, confirmed, ai_suggested_cat_id` | 猫咪上报记录，一次上报可能关联到已有猫咪或创建新猫 |
| `posts` | `id, title, content, images, likes, dislikes, user_id, pinned, referenced_cat_id` | 论坛帖子，图片 URL 以 JSON 字符串保存 |
| `comments` | `id, content, post_id, user_id, created_at` | 论坛评论；当前表结构没有嵌套评论字段 |
| `square_posts` | `id, text, images, location, type, status, likes, user_id, referenced_cat_id, rescue_activity_id` | 广场广播，可关联猫咪和救助活动 |
| `rescue_activities` | `id, title, description, cat_id, square_post_id, organization_id, problem_type, urgency, status, created_by` | 救助活动，状态值为 `created`、`in_progress`、`completed` |
| `rescue_tasks` | `id, rescue_activity_id, assignee_user_id, assigner_user_id, status, assigned_at, completed_at, completion_note, completion_images` | 救助子任务，可指派或由组织成员申领 |
| `rescue_task_logs` | `id, rescue_task_id, user_id, log_type, content, images, created_at` | 救助日志，记录进度和现场照片 |
| `organizations` | `id, name, description, created_at` | 救助组织 |
| `organization_members` | `id, organization_id, user_id, role, joined_at` | 组织成员关系 |
| `messages` | `id, type, content, target_type, target_id, user_id, is_read, created_at` | 用户消息、通知和天气预警记录 |

> 📌 完整建表语句见 `src/main/resources/db/migration/` 下的 25 个 Flyway 迁移脚本。

---
