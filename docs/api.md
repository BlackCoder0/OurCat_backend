# API 速查

> **Base URL:** `http://localhost:8080`  
> **鉴权方式:** `Authorization: Bearer <token>`（认证接口免登录；demo 图片 GET 公开，PUT 使用一次性票据）  
> 移动端主要依赖以下接口分组进行联调。

| 路径前缀 | 模块 | 关键操作 |
| :--- | :--- | :--- |
| `/api/auth/**` | 认证 | 登录、注册、图形验证码 |
| `/api/user/**` | 用户 | 个人信息、修改密码、用户搜索 |
| `/api/cat` | 猫咪 | 上报、查询、**AI 匹配**、档案归档 |
| `/api/rescue/**` | 救助 | 活动、**任务分配**、日志、进度 |
| `/api/forum/**` | 论坛 | 帖子、评论、点赞 |
| `/api/square/**` | 广场 | 广播、组织活动 |
| `/api/message` | 消息 | 系统通知、天气预警记录 |
| `/api/org/**` | 组织 | 默认组织信息、加入申请、成员管理与申请审核 |
| `/api/weather/**` | 天气 | 实时、预报、**温差/降水预警** |
| `/api/ai/**` | AI | **图像特征提取、相似度匹配** |
| `/api/oss/**` | 文件 | 获取上传预签名 URL |

> 业务接口需要登录，demo 图片 GET/PUT 例外，详见文末。业务请求携带 Header：`Authorization: Bearer <token>`

### 核心接口最小示例

以下示例只覆盖从注册到基础联调所需的最小路径。先获取验证码，再把登录或注册返回的 Token 用于后续请求。

```powershell
# 1. 获取验证码
$captcha = curl.exe -s http://localhost:8080/api/auth/captcha | ConvertFrom-Json
$captcha.key
$captcha.image

# 2. 注册（把验证码图片中的文字填入 captcha）
curl.exe -X POST http://localhost:8080/api/auth/register `
  -H "Content-Type: application/json" `
  -d '{"username":"demo","password":"change-me","nickname":"Demo","captchaKey":"验证码 key","captcha":"验证码文字"}'

# 3. 登录后保存返回的 token，再访问需要鉴权的接口
curl.exe -X GET "http://localhost:8080/api/cat/locations" `
  -H "Authorization: Bearer 你的 token"
```

AI 接口是否可用取决于后端配置；demo 默认关闭图片 AI，文本匹配仍可用于研究。

关键接口行为：

| 方法 | 路径 | 鉴权 | 说明 |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/auth/captcha` | 否 | 返回验证码 `key` 和 Base64 图片 |
| `POST` | `/api/auth/register` | 否 | 注册需要 `username`、`password`、`captchaKey`、`captcha` |
| `POST` | `/api/auth/login` | 否 | 登录需要用户名、密码和验证码，返回 JWT |
| `POST` | `/api/cat/report` | 是 | 保存上报并执行当前匹配流程，响应返回报告 ID、坐标和时间 |
| `GET` | `/api/cat/report/{reportId}` | 是 | 查询上报详情、匹配信息和附近猫咪 |
| `POST` | `/api/ai/detect` | 是 | 根据图片 URL 提取特征并返回候选猫咪 |
| `GET` | `/api/oss/upload-url?filename=cat.jpg&contentType=image/jpeg` | 是 | 获取上传地址；生产/云端返回 OSS 预签名地址，`demo` 返回本地地址 |
| `GET` | `/api/weather/warning` | 是 | 按 `location=纬度,经度` 查询天气预警并写入消息 |

图片上传步骤（demo 和 OSS 共用返回字段）：先调用上面的接口，再使用返回的 `uploadUrl` 发起 `PUT`，并携带返回的 `contentType` 请求头；上传成功后，把返回的 `publicUrl` 作为图片地址提交给猫咪、论坛或救助接口。

---

本地 demo 的 `GET /api/oss/local/{fileName}` 不要求登录；`PUT /api/oss/local/{fileName}?token=...` 使用一次性票据，不携带业务 JWT。这两个接口在其他 Profile 不注册。示例组织入口为 `/api/org`。
