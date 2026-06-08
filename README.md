<p align="center">
  <img src="docs/images/logo.png" width="450" alt="OurCat">
</p>

<p align="center">
  <a href="https://github.com/BlackCoder0/OurCat_backend/blob/main/LICENSE"><img src="https://img.shields.io/badge/license-MIT-green" alt="License"></a>
  <a href="#"><img src="https://img.shields.io/badge/Spring_Boot-2.7.18-brightgreen" alt="Spring Boot"></a>
  <a href="#"><img src="https://img.shields.io/badge/Java-11-orange" alt="Java"></a>
  <a href="#"><img src="https://img.shields.io/badge/MySQL-8.0-blue" alt="MySQL"></a>
  <a href="#"><img src="https://img.shields.io/badge/Flyway-7.15-red" alt="Flyway"></a>
</p>

# 🐱 OurCat Backend

> **让每一只校园猫咪都被看见。**  
> Spring Boot 2.7 + MySQL 8.0 + Flyway 构建的校园流浪猫救助平台后端，为 Android 客户端提供覆盖猫咪档案、救助调度、AI 以图搜猫、天气预警等核心业务模块的 REST API。

---

## ⚡ 30 秒快速启动

```bash
git clone https://github.com/BlackCoder0/OurCat_backend.git
cd OurCat_backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

```bash
# 验证（无需登录）
curl http://localhost:8080/api/auth/captcha
```

> 💡 开发模式使用 H2 内存数据库，**无需安装 MySQL**，无需配置任何密钥。完整教程见下方[快速上手](#-快速上手)。

---

## 📖 项目简介

**OurCat（校园猫谱）** 是一个 **Spring Boot + Android + MySQL** 的全栈校园流浪猫救助平台。

想象这个场景：你在校园里遇到一只没见过的猫，打开 App 拍照上传——后端收到照片后，调用 **AI 视觉模型（DINOv2）** 提取图像特征，和数据库中已有的猫咪档案逐一比对。如果匹配到相似度 70% 以上的记录，说明这只猫已经在册；如果没匹配到，则为你创建一份新档案。**这就是 "AI 以图搜猫 + 自动归档" 的核心闭环。**

而救助功能更进一步：任何人都可以发起一次救助活动（"图书馆门口有只猫腿受伤了"），组织成员能认领任务、更新进度、上传救助日志。后端自动串联**天气预警**——极端高温或强降水来临时，系统知道哪些区域有猫，提前推送提醒。

> 👉 这是 **后端 API 服务**。Android 客户端请见 [OurCat Android](https://github.com/BlackCoder0/OurCat_Android)

---

## 🎨 功能全景

```
                          ┌──────────────────┐
                          │    🔐 用户系统     │
                          │  注册/登录/JWT鉴权  │
                          │  组织创建/成员管理   │
                          └────────┬─────────┘
                                   │ 身份认证贯穿所有操作
          ┌────────────────────────┼────────────────────────┐
          │                        │                        │
          ▼                        ▼                        ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   🐈 猫咪档案    │    │   🚑 救助调度    │    │   💬 社群互动    │
│                 │    │                 │    │                 │
│ 📸 拍照上报     │    │ 📋 创建救助活动  │    │ 📝 论坛发帖评论  │
│ 🧠 AI 特征提取  │    │ 👥 分配救助任务  │    │ 👍 点赞投票     │
│ 🔍 以图搜猫匹配 │    │ 📊 进度追踪     │    │ 📢 广场广播      │
│ 📋 文字识别归档 │    │ 📝 救助日志记录  │    │ 💌 消息通知      │
│ 🗺️ 地图+热力图  │    │ ⚠️ 天气预警联动  │    │ 🏛️ 组织管理      │
└─────────────────┘    └─────────────────┘    └─────────────────┘
          │                        │                        │
          └────────────────────────┼────────────────────────┘
                                   │
                                   ▼
                    ┌─────────────────────────┐
                    │      ☁️ 外部服务          │
                    │ 阿里云OSS / 和风天气      │
                    │ 高德地图 / Replicate AI   │
                    │ HuggingFace / Open-Meteo │
                    └─────────────────────────┘
```

### 🔥 三大热点功能详解

| 热点 | 怎么用 | 后端做了什么 |
| :--- | :--- | :--- |
| 📸 **AI 以图搜猫 + 自动归档** | 用户拍照上传猫咪照片，填写毛色、性别等特征标签 | ① 图片存 OSS → ② DINOv2 模型提取 768 维 Embedding 特征 → ③ 与全库猫咪计算余弦相似度 → ④ Top-5 匹配返回（含相似度百分比）→ ⑤ 高于阈值则合并档案，低于则创建新猫 |
| 🚑 **救助活动全流程联动** | 用户发起救助 → 组织成员认领任务 → 执行中更新进度 → 完成归档 | ① 创建 RescueActivity + 子 RescueTask → ② 任务状态机流转（待认领→进行中→已完成）→ ③ 每次状态变更写入 RescueTaskLog → ④ 联动天气 API 推送极端天气预警 → ⑤ 救助完成自动更新猫咪健康状态 |
| 🌤️ **天气预警 × 猫咪守护** | 无需手动操作，后台定时巡检 | ① JWT 认证和风天气 + Open-Meteo CMA GRAPES 模型获取精准预报 → ② 检测日际温差≥8℃ 或 降水≥30mm → ③ 结合猫咪地理位置推送预警 → "明天降温 12℃，记得给图书馆的橘猫添个窝" |

---

## 🏗️ 系统架构

```
┌─────────────────────────┐
│    Android 客户端         │  ◄─── JWT Bearer Token
│    (OurCat Android)      │
└────────────┬────────────┘
             │
             │  HTTPS REST API
             ▼
┌─────────────────────────────────────────┐
│          Spring Boot 2.7.18              │
│          (OurCat Backend · 本仓库)        │
├─────────────────────────────────────────┤
│  🔐 Security Layer                      │
│  ├── SecurityConfig     BCrypt 密码哈希  │
│  └── JwtAuthFilter      JWT 无状态认证   │
├─────────────────────────────────────────┤
│  🌐 Controller Layer（认证·猫咪·救助·论坛·广场·消息·组织·天气·AI·文件）│
│  Auth / User / Cat / Rescue / Forum     │
│  Square / Message / Org / Weather / AI  │
├─────────────────────────────────────────┤
│  🧠 Service Layer（猫咪·救助·论坛·天气·消息·组织·广场·AI）│
│  CatService(含AI匹配) / RescueService   │
│  ForumService / WeatherService / ...    │
├─────────────────────────────────────────┤
│  🗄️ Repository Layer（Spring Data JPA → Hibernate）│
│  Spring Data JPA → Hibernate            │
└────────────┬────────────────────────────┘
             │
    ┌────────┼──────────┐
    ▼        ▼          ▼
┌──────┐ ┌──────┐ ┌──────────┐
│MySQL │ │ OSS  │ │ 外部 API  │
│8.0 + │ │阿里云│ │ 和风/高德 │
│Flyway│ │对象存储│ │ /AI/气象 │
└──────┘ └──────┘ └──────────┘
```

---

## 📁 目录结构

```text
backend/
├── pom.xml                          # Maven 配置（Spring Boot 2.7.18, Java 11）
├── .gitignore                       # 已排除 application-local.yml, .env, token运维.py
│
└── src/main/
    ├── java/com/ourcat/backend/
    │   ├── OurCatApplication.java   # 🚀 Spring Boot 启动入口
    │   ├── config/                  # ⚙️ Security, JWT, OSS, Captcha 配置
    │   ├── controllers/             # 🌐 REST 控制器（认证/猫咪/救助/论坛/广场/消息/组织/天气/AI/文件）
    │   │   ├── AuthController       # 登录/注册/验证码
    │   │   ├── UserController       # 用户信息/密码修改
    │   │   ├── CatController        # 猫咪上报/AI匹配/文字归档
    │   │   ├── RescueController     # 救助活动/任务分配/日志
    │   │   ├── ForumController      # 帖子/评论/点赞
    │   │   ├── SquareController     # 广场广播/评论
    │   │   ├── MessageController    # 消息/通知
    │   │   ├── OrganizationController # 组织管理
    │   │   ├── WeatherController    # 天气/预警/温差检测
    │   │   ├── OssController        # OSS 预签名上传
    │   │   └── AiController         # AI 特征提取/相似匹配
    │   ├── models/                  # 📦 16 个 JPA 实体
    │   ├── repositories/            # 🗄️ 15 个 Spring Data 仓储
    │   ├── services/                # 🧠 业务服务（猫咪/救助/论坛/天气/消息/组织/广场/AI）
    │   │   ├── CatService           # 猫咪业务 + AI 匹配逻辑
    │   │   ├── RescueService        # 救助任务状态机
    │   │   ├── WeatherService       # 天气获取 + 预警判断
    │   │   └── ...
    │   └── utils/                   # 🔧 JwtUtil, OssUtil, CaptchaStore
    │
    └── resources/
        ├── application.yml          # 主配置（全部 ${ENV_VAR:} 占位符，零硬编码）
        ├── application-dev.yml      # 开发环境（H2 内存库，开箱即用）
        ├── application-prod.yml     # 生产环境覆盖
        └── db/migration/            # 🦅 Flyway 迁移（V1 ~ V25，25 个版本）
```

---

## 🚀 快速上手

从克隆仓库到服务跑起来，提供两条路径：

| 路径 | 适合场景 | 需要安装 |
| :--- | :--- | :--- |
| **A. 开发模式** | 本地调试、接口测试、前端联调 | 仅 JDK 11 + Maven（数据库无需安装） |
| **B. 生产部署** | 上线到服务器 | JDK 11 + Maven + MySQL 8.0 + 全部 API 密钥 |

---

### 路径 A：开发模式（零依赖，一分钟跑起来）

开发模式使用 H2 内存数据库，**不需要安装 MySQL**，不需要配置任何密钥。克隆下来就能跑。

#### 1. 确保环境就绪

```bash
java -version   # 需要 JDK 11+
mvn -version    # 需要 Maven 3.6+
```

如果未安装：下载 [JDK 11](https://adoptium.net/) 和 [Maven](https://maven.apache.org/download.cgi)，解压后将 `bin/` 加入系统 PATH。

#### 2. 克隆并启动

```bash
git clone https://github.com/BlackCoder0/OurCat_backend.git
cd OurCat_backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

看到 `Started OurCatApplication` 就说明跑起来了。

#### 3. 快速验证

```bash
# 验证码接口（无需登录）
curl http://localhost:8080/api/auth/captcha

# H2 数据库控制台（浏览器打开）
# http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:mem:ourcat  用户名: sa  密码: (留空)
```

> 📌 开发模式下 Flyway 自动禁用，改为 JPA `ddl-auto: create` 自动建表——每次重启数据库会清空，仅用于开发调试。

---

### 路径 B：生产部署（MySQL + 全部密钥）

#### 1. 安装 MySQL 8.0

已有 MySQL 可跳过。首次安装：

**Windows：** 下载 [MySQL Installer](https://dev.mysql.com/downloads/installer/)，安装时记住 root 密码  
**Linux：**
```bash
sudo apt install mysql-server-8.0   # Ubuntu/Debian
# 或
sudo yum install mysql-server       # CentOS
sudo systemctl start mysql
```

#### 2. 创建数据库和用户

登录 MySQL 并执行：

```sql
-- 创建数据库（Flyway 会自动建表，但数据库本身需要手动创建）
CREATE DATABASE ourcat DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建专用用户（密码请换成你自己的）
CREATE USER 'ourcat'@'localhost' IDENTIFIED BY '你设置的密码';
GRANT ALL PRIVILEGES ON ourcat.* TO 'ourcat'@'localhost';
FLUSH PRIVILEGES;
```

> 如果应用和数据库不在同一台机器，把 `'localhost'` 换成 `'%'`（允许远程连接），并确保 MySQL 开启了远程访问（`bind-address = 0.0.0.0`）

#### 3. 设置环境变量

项目采用 **零硬编码** 策略——`application.yml` 中全是 `${VAR:}` 占位符，真实密钥全部通过环境变量注入。

| 变量组 | 开发模式 | 生产模式 | 说明 |
| :--- | :--- | :--- | :--- |
| DB | 不需要 | **必需** | dev 自动使用 H2 内存库 |
| JWT | 不需要 | **必需** | dev 使用默认密钥 |
| OSS | 不需要 | **视功能而定** | 仅图片上传需要 |
| AI | 不需要 | 可选 | 不影响基础业务 |
| Weather | 不需要 | 可选 | 不影响基础业务 |

**最小必填变量**（少了任何一个服务都无法正常启动）：

```bash
# ──── 数据库（对应上一步创建的库和用户）────
export DB_HOST=localhost          # MySQL 地址，远程服务器填 IP
export DB_USERNAME=ourcat         # 数据库用户名
export DB_PASSWORD=你设置的密码    # 数据库密码

# ──── JWT 签名密钥 ────
export JWT_SECRET=这里填一段至少32位的随机字符串

# ──── 阿里云 OSS（图片存储）────
export OURCAT_OSS_ENDPOINT=oss-cn-guangzhou.aliyuncs.com
export OURCAT_OSS_BUCKET=你的Bucket名称
export OURCAT_OSS_ACCESS_KEY_ID=你的RAM AccessKey ID
export OURCAT_OSS_ACCESS_KEY_SECRET=你的RAM AccessKey Secret

# ──── 和风天气（天气+预警）────
export OURCAT_QWEATHER_JWT=你的和风天气JWT令牌

# ──── 高德地图 ────
export AMAP_KEY=你的高德Web API Key
```

**可选变量**（AI 功能，不启用不影响基础业务）：

```bash
export AI_REPLICATE_API_TOKEN=你的Replicate Token    # 方案一：云端 AI（Replicate CLIP）
export AI_HF_TOKEN=你的HuggingFace Token             # 方案二：HuggingFace 推理 API（DINOv2）
export AI_EMBEDDING_API_URL=                        # 方案二备选：自托管 embedding 服务地址
```

> 💡 **永久保存环境变量**：Linux 写入 `/etc/environment` 或 `~/.bashrc`；Windows 在"系统属性 → 环境变量"中设置。
>
> 💡 **不想用环境变量？** 在 `src/main/resources/` 下创建 `application-local.yml`（已在 `.gitignore` 中，不会提交）。以下是一份完整模板，复制后填入真实值即可：

<details>
<summary>📄 application-local.yml 完整模板（点击展开）</summary>

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ourcat?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: ourcat
    password: 你设置的数据库密码

ourcat:
  jwt:
    secret: 至少32位的随机字符串，用于签署JWT令牌
    expiration-ms: 86400000

  oss:
    endpoint: oss-cn-guangzhou.aliyuncs.com
    bucket: 你的Bucket名称
    access-key-id: 你的RAM AccessKey ID
    access-key-secret: 你的RAM AccessKey Secret

  qweather:
    api-host: https://api.qweather.com
    jwt: 你的和风天气JWT令牌

  amap:
    key: 你的高德Web API Key

  ai:
    enabled: true
    replicate-enabled: false       # 使用 HuggingFace 自托管则留 false
    replicate-api-token: ""        # Replicate Token（方案一）
    hf-token: ""                   # HuggingFace Token（方案二）
    embedding-api-url: ""          # 自托管 embedding 服务地址
```

</details>

#### 4. 构建并启动

```bash
# 构建（跳过测试，加快打包）
mvn clean package -DskipTests

# 启动（prod 配置会启用 MySQL + Flyway）
java -jar target/backend-1.0.0-SNAPSHOT.jar --spring.profiles.active=prod
```

首次启动时 **Flyway 自动执行 25 版数据库迁移**，在 `flyway_schema_history` 表中记录版本。之后每次升级只需增加新的迁移脚本，Flyway 会自动检测并执行未应用的版本——**全程无需手动写 SQL 建表**。

#### 5. 验证部署

```bash
# 健康检查
curl http://localhost:8080/api/auth/captcha
# 应返回验证码图片数据
```

#### 常见问题排查

| 现象 | 可能原因 | 解决办法 |
| :--- | :--- | :--- |
| `Connection refused` | MySQL 未启动或端口不对 | `mysql -h $DB_HOST -u $DB_USERNAME -p` 测试连接 |
| `Access denied` | 用户名或密码错误 | 检查 `DB_USERNAME` / `DB_PASSWORD` 环境变量 |
| `Unknown database 'ourcat'` | 数据库未创建 | 执行 `CREATE DATABASE ourcat` |
| Flyway 报错 | 迁移版本冲突 | 检查 `flyway_schema_history` 表，清理失败记录 |
| 天气/AI 接口报错 | 对应 Key 未配置或已过期 | 检查对应环境变量是否设置正确 |

---

## 🔌 API 速查

> **Base URL:** `http://localhost:8080`  
> **鉴权方式:** `Authorization: Bearer <token>`（仅 `/api/auth/register`、`/api/auth/login`、`/api/auth/captcha` 免登录）  
> 移动端主要依赖以下接口分组进行联调。

| 路径前缀 | 模块 | 关键操作 |
| :--- | :--- | :--- |
| `/api/auth/**` | 🔐 认证 | 登录、注册、图形验证码 |
| `/api/user/**` | 👤 用户 | 个人信息、修改密码、用户搜索 |
| `/api/cat` | 🐈 猫咪 | 上报、查询、**AI 匹配**、档案归档 |
| `/api/rescue/**` | 🚑 救助 | 活动、**任务分配**、日志、进度 |
| `/api/forum/**` | 💬 论坛 | 帖子、评论、点赞 |
| `/api/square/**` | 📢 广场 | 广播、组织活动 |
| `/api/message` | ✉️ 消息 | 通知、私信、**天气预警推送** |
| `/api/org/**` | 🏛️ 组织 | 创建、加入、成员管理 |
| `/api/weather/**` | 🌤️ 天气 | 实时、预报、**温差/降水预警** |
| `/api/ai/**` | 🧠 AI | **图像特征提取、相似度匹配** |
| `/api/oss/**` | 📁 文件 | 获取上传预签名 URL |

> 📝 除认证接口外，所有请求需携带 Header：`Authorization: Bearer <token>`

---

## 🗄️ 数据关系

```
User ──┬── Cat (上报者)              ← 谁上报了这只猫
       ├── Post / PostVote (论坛)    ← 谁发了帖子
       ├── RescueTask (执行者)       ← 谁在执行救助
       ├── SquarePost (广场)         ← 谁发了广播
       ├── Message (收发)           ← 消息发给谁
       └── OrganizationMember        ← 属于哪个组织

Cat ──┬── CatReport (上报记录)      ← 这只猫的所有目击记录
      └── RescueTask (救助目标)     ← 这只猫关联的救助

Organization ──┬── OrganizationMember  ← 组织有哪些成员
                ├── RescueActivity     ← 组织发起了哪些救助
                └── SquarePost         ← 组织发布了哪些广播
```

### 核心表结构速览

| 表名 | 关键字段 | 说明 |
| :--- | :--- | :--- |
| `users` | `id, username, password(BCrypt), role, nickname, avatar_url` | 用户表，角色分普通用户/组织管理员/超级管理员 |
| `cats` | `id, name, color, gender, area, latitude, longitude, ai_embedding(768维)` | 猫咪档案表，`ai_embedding` 存储 DINOv2 特征向量用于以图搜猫 |
| `cat_reports` | `id, cat_id, reporter_id, images, description, location` | 猫咪上报记录，一次上报可能关联到已有猫咪或创建新猫 |
| `posts` | `id, author_id, title, content, images, vote_count` | 论坛帖子，支持 Markdown 图文 |
| `comments` | `id, post_id, author_id, parent_id, content` | 多级评论，`parent_id` 实现嵌套回复 |
| `square_posts` | `id, author_id, org_id, content, images, rescue_activity_id` | 广场广播，可关联救助活动 |
| `rescue_activities` | `id, org_id, title, description, status, cat_id` | 救助活动，状态流转：募集中→进行中→已完成 |
| `rescue_tasks` | `id, activity_id, assignee_id, title, status, deadline` | 救助子任务，可分配给组织成员 |
| `rescue_task_logs` | `id, task_id, operator_id, action, content, images` | 救助日志，记录每一步操作和现场照片 |
| `organizations` | `id, name, description, avatar_url, creator_id` | 救助组织 |
| `organization_members` | `id, org_id, user_id, role` | 组织成员关系 |
| `messages` | `id, sender_id, receiver_id, type, content, is_read` | 消息/通知/天气预警 |

> 📌 完整建表语句见 `src/main/resources/db/migration/` 下的 25 个 Flyway 迁移脚本。

---

## 🔒 安全设计

* **零硬编码凭据**：全部密钥通过环境变量注入，源码中不含任何真实凭证，可放心开源。
* **密码 BCrypt 哈希**：用户密码使用 Spring Security BCryptPasswordEncoder 加盐存储。
* **JWT 无状态认证**：Token 过期时间可配置（默认 24h），支持无缝水平扩展。
* **OSS 预签名上传**：客户端通过 `GET /api/oss/upload-url` 获取临时预签名 URL 直传，服务端不暴露 OSS AccessKey。
* **Flyway 版本迁移**：数据库变更可追溯、可回滚，避免手动 SQL 事故。

---

## 🛠️ 技术栈

| 层面 | 选型 |
| :--- | :--- |
| 框架 | Spring Boot 2.7.18 (Java 11) |
| 安全 | Spring Security + JJWT 0.11.5 |
| ORM | Spring Data JPA (Hibernate) |
| 数据库 | MySQL 8.0 + Flyway 7.15 |
| 对象存储 | 阿里云 OSS 3.17.4 |
| 天气 | 和风天气 JWT + Open-Meteo CMA GRAPES |
| AI | 默认关闭。开启后可选：① HuggingFace DINOv2 自托管（768维，需 GPU）② Replicate CLIP 云端 API（按量计费，不占本机资源）。不同模型的 embedding 不可混用比对。 |
| 地图 | 高德地图 Web API |
| 构建 | Maven |

---

## 🤝 贡献与鸣谢

如果你觉得这个项目对校园流浪猫救助有意义，欢迎点一个 **⭐ Star**！

如有问题或建议，欢迎提交 Issue。

---

## 📌 当前状态

- **当前客户端**：仅 Android（无 iOS / Web 版本）
- **API 文档**：暂未集成 Swagger / OpenAPI，接口列表见上方 [API 速查](#-api-速查)
- **开发环境**：默认 H2 内存数据库，开箱即用
- **生产环境**：MySQL 8.0 + Flyway 自动迁移
- **已验证环境**：JDK 11 + Maven 3.8 + MySQL 8.0
- **Last reviewed**：2026-06-08

---

## 📄 开源协议

MIT License

---

## 🌐 English

**OurCat** is a full-stack **Spring Boot + Android + MySQL** campus stray cat rescue platform. It provides REST APIs covering cat archiving with **AI-powered image recognition** (DINOv2 / CLIP embedding + cosine similarity matching), rescue task scheduling with status workflow, forum & community square, Amap-based location visualization, QWeather + Open-Meteo extreme weather alerts, and Alibaba Cloud OSS presigned upload.

> 👉 **Backend repo** (this one) · [Android client](https://github.com/BlackCoder0/OurCat_Android)

---

OurCat — 让每一只校园猫都被看见 ❤️
  <sub>OurCat — 让每一只校园猫咪都被看见 ❤️</sub>
</p>
