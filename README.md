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

> **让每一只校园猫都被看见。**
>
> Spring Boot 2.7 + MySQL 8.0 + Flyway 构建的校园流浪猫救助平台后端，为 Android 客户端提供覆盖猫咪档案、救助调度、AI 以图搜猫、天气预警等核心业务模块的 REST API。

> **默认运行方式：**本地开发使用 H2 内存数据库；仓库不提供公开云端 Backend 地址。想让 Android 连接云端，需要先自行部署 Backend，再修改客户端的 Base URL。

---

## ⚡ 30 秒快速启动

```powershell
git clone https://github.com/BlackCoder0/OurCat_backend.git
cd OurCat_backend
$env:AI_ENABLED = "false"
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

另开一个终端验证：

```powershell
curl.exe http://localhost:8080/api/auth/captcha
```

返回内容应包含 `key` 和 `image`。

> 💡 这条路径只验证本地基础服务。开发模式使用 H2 内存数据库，**无需安装 MySQL**；OSS、天气和 AI 都可以后续按功能启用。完整教程见下方[快速上手](#-快速上手)。

---

## 📖 项目简介

**OurCat（校园猫谱）** 是一个 **Spring Boot + Android + MySQL** 的全栈校园流浪猫救助平台。

想象这个场景：你在校园里遇到一只没见过的猫，打开 App 拍照上传——后端收到照片后，调用 **Replicate 视觉模型** 提取图像特征，和数据库中已有的猫咪档案逐一比对。如果匹配到足够相似的记录，说明这只猫已经在册；如果没匹配到，则为你创建一份新档案。**这就是 "AI 以图搜猫 + 自动归档" 的核心闭环。**

而救助功能更进一步：任何人都可以发起一次救助活动（"图书馆门口有只猫腿受伤了"），组织成员能认领任务、更新进度、上传救助日志。系统提供**天气查询和预警接口**，App 在启动或用户触发查询时获取天气；当前版本没有后台定时巡检任务。

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
│ 📋 文本特征归档 │    │ 📝 救助日志记录  │    │ 💌 消息通知      │
│ 🗺️ 地图+热力图  │    │ ⚠️ 天气预警联动  │    │ 🏛️ 组织管理      │
└─────────────────┘    └─────────────────┘    └─────────────────┘
          │                        │                        │
          └────────────────────────┼────────────────────────┘
                                   │
                                   ▼
                    ┌─────────────────────────┐
                    │      ☁️ 外部服务          │
                    │ 阿里云 OSS / Replicate │
                    │ Open-Meteo / 和风天气  │
                    └─────────────────────────┘
```

### 🔥 三大热点功能详解

| 热点 | 怎么用 | 后端做了什么 |
| :--- | :--- | :--- |
| 📸 **AI 以图搜猫 + 自动归档** | 用户拍照上传猫咪照片，填写毛色、性别等特征标签 | ① 图片存 OSS → ② Replicate 模型提取 Embedding 特征 → ③ 上报接口先返回报告 ID → ④ 后台异步执行地理、文本和图片匹配 → ⑤ 通过报告详情接口查看最终关联结果；独立 `/api/ai/detect` 接口才返回 Top-5 候选 |
| 🚑 **救助活动全流程联动** | 用户发起救助 → 组织成员认领任务 → 执行中更新进度 → 完成归档 | ① 创建 RescueActivity + 子 RescueTask → ② 任务状态机流转（待认领→进行中→已完成）→ ③ 每次状态变更写入 RescueTaskLog → ④ 救助完成自动更新猫咪健康状态；天气预警由独立天气接口查询并写入消息 |
| 🌤️ **天气预警 × 猫咪守护** | App 启动或用户触发天气查询 | ① 调用和风天气和 Open-Meteo CMA GRAPES 获取预报 → ② 检测日际温差≥8℃ 或降水≥30mm → ③ 有预警时写入当前用户消息；当前版本不包含后台定时巡检 |

---

## 🏗️ 系统架构

```
┌─────────────────────────┐
│    Android 客户端         │  ◄─── JWT Bearer Token
│    (OurCat Android)      │
└────────────┬────────────┘
             │
             │  本地开发 HTTP / 生产 HTTPS REST API
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
│8.0 + │ │阿里云│ │ 和风/AI  │
│Flyway│ │对象存储│ │ /气象   │
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
    │   │   ├── CatController        # 猫咪上报/AI匹配/文本特征归档
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
        ├── application.yml          # 主配置（敏感值使用 ${ENV_VAR:} 占位符）
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
| **B. 生产部署** | 上线到本地或云服务器 | JDK 11 + Maven + MySQL 8.0 + 生产 JWT 密钥 |

---

## 🧭 先判断你需要什么

不要一开始就注册所有第三方平台。按下面的顺序配置：

| 阶段 | 需要什么 | 缺少时的影响 |
| :--- | :--- | :--- |
| 先启动 Backend | JDK 11、Maven | 无法构建或启动 |
| 本地基础业务 | 开发模式自动使用 H2 | 重启会清空数据，但不影响启动 |
| 生产数据库 | MySQL、数据库账号、生产 JWT 密钥 | 生产模式无法正常使用 |
| 图片能力 | 阿里云 OSS | 猫咪图片、头像、帖子图片上传不可用 |
| AI 识猫 | Replicate Token 和开关 | AI 特征提取、相似匹配不可用 |
| 基础天气 | Open-Meteo，默认无需 Key | 基础天气和计算型提醒不可用 |
| 官方灾害预警 | 和风天气 JWT | 官方灾害预警不可用，基础天气不受影响 |
| Android 地图 | Android 端高德 Key | Android 地图和定位不可用，不影响 Backend 启动 |

Backend 能启动，不等于所有外部功能都已配置完成。

---

## ⚙️ 配置文件先讲清楚

如果你是第一次接触 Spring Boot，先记住一句话：**不要把个人密钥直接写进公开的 `application.yml`。**

### 这些配置文件分别负责什么？

| 文件 | 作用 | 是否应该写个人密钥 |
| :--- | :--- | :--- |
| `src/main/resources/application.yml` | 全项目的公开基础模板，提供默认值和环境变量占位符 | **不应该** |
| `src/main/resources/application-dev.yml` | `dev` Profile 的开发覆盖配置，使用 H2 内存数据库 | 不需要 |
| `src/main/resources/application-prod.yml` | `prod` Profile 的生产覆盖配置，使用 MySQL | 不要把密钥写死在文件里 |
| 外部 `local/application-local.yml` | 你自己的本地私有覆盖配置，适合放数据库账号和第三方密钥 | **可以，但不能提交** |

启动时使用哪个 Profile，决定加载哪一层配置：

```text
application.yml
        ↓
application-dev.yml   （启动 dev 时加载）
或 application-prod.yml（启动 prod 时加载）
或 application-local.yml（启动 local 时加载）
```

`application.yml` 中的环境变量占位符通常写在真正的配置值内部，例如：

```yaml
url: jdbc:mysql://${DB_HOST:localhost}:3306/ourcat
```

含义是：如果系统里设置了 `DB_HOST`，就使用它；没有设置时使用 `localhost`。冒号后面的内容是默认值，不是要求你立刻填写的个人配置。

### `application.yml` 哪些能改，哪些不要改？

通常**不要直接改** `application.yml` 来填密钥。优先使用环境变量，或者使用未提交的私有 `application-local.yml`。

| 配置内容 | 对应环境变量 | 是否必填 | 缺少时的影响 |
| :--- | :--- | :--- | :--- |
| MySQL 地址、账号、密码 | `DB_HOST`、`DB_USERNAME`、`DB_PASSWORD` | 仅 `prod` 或 MySQL 本地运行必填 | Backend 无法连接 MySQL |
| JWT 签名密钥 | `JWT_SECRET` | 生产必填；仅 `dev` Profile 有开发专用值 | 生产缺少时应启动失败；不要使用开发值 |
| JWT 有效期 | `JWT_EXPIRATION_MS` | 否，默认 24 小时 | 使用默认有效期 |
| 阿里云 OSS | `OURCAT_OSS_ENDPOINT`、`OURCAT_OSS_BUCKET`、`OURCAT_OSS_ACCESS_KEY_ID`、`OURCAT_OSS_ACCESS_KEY_SECRET` | 图片功能必填 | 图片、头像、帖子图片无法正常上传 |
| AI 总开关 | `AI_ENABLED` | 否 | 设置为 `false` 时关闭全部 AI |
| Replicate AI | `AI_REPLICATE_ENABLED`、`AI_REPLICATE_API_TOKEN`、可选 `AI_REPLICATE_MODEL` | AI 识猫必填 | AI 特征提取和相似匹配不可用 |
| 和风天气 | `OURCAT_QWEATHER_API_HOST`、`OURCAT_QWEATHER_JWT` 或 `OURCAT_QWEATHER_JWT_FILE` | 官方灾害预警必填 | 基础天气仍可用，官方灾害预警不可用 |
| Open-Meteo | `OURCAT_OPEN_METEO_*` | 否，默认无需 Key | 使用默认 Open-Meteo 配置 |
| 服务端口 | `SERVER_PORT` | 否，默认 `8080` | 使用默认端口 |

`AMAP_KEY` 虽然仍保留在 `application.yml` 的占位配置中，但当前主要影响 Android 端地图和定位。它不是 Backend 启动条件，Android 端的配置请看 [Android README](https://github.com/BlackCoder0/OurCat_Android)。

### 推荐方式：环境变量

这是最适合云服务器和生产环境的方式。密钥不写进源码，也不需要复制或改名任何 YAML 文件。

PowerShell 示例：

```powershell
$env:DB_HOST = "localhost"
$env:DB_USERNAME = "ourcat"
$env:DB_PASSWORD = "替换成你自己的数据库密码"
$env:JWT_SECRET = "替换成至少32位的随机字符串"

# 图片上传需要；不使用图片功能可以先不设置
$env:OURCAT_OSS_ENDPOINT = "oss-cn-你的地域.aliyuncs.com"
$env:OURCAT_OSS_BUCKET = "你的 Bucket 名称"
$env:OURCAT_OSS_ACCESS_KEY_ID = "你的 RAM AccessKey ID"
$env:OURCAT_OSS_ACCESS_KEY_SECRET = "你的 RAM AccessKey Secret"

# 使用 Replicate 时才设置
$env:AI_ENABLED = "true"
$env:AI_REPLICATE_ENABLED = "true"
$env:AI_REPLICATE_API_TOKEN = "你的 Replicate Token"
```

macOS/Linux 示例：

```bash
export DB_HOST=localhost
export DB_USERNAME=ourcat
export DB_PASSWORD='替换成你自己的数据库密码'
export JWT_SECRET='替换成至少32位的随机字符串'

export OURCAT_OSS_ENDPOINT='oss-cn-你的地域.aliyuncs.com'
export OURCAT_OSS_BUCKET='你的 Bucket 名称'
export OURCAT_OSS_ACCESS_KEY_ID='你的 RAM AccessKey ID'
export OURCAT_OSS_ACCESS_KEY_SECRET='你的 RAM AccessKey Secret'

export AI_ENABLED=true
export AI_REPLICATE_ENABLED=true
export AI_REPLICATE_API_TOKEN='你的 Replicate Token'
```

注意：上面的环境变量只对当前终端或当前进程有效。长期运行时，请使用操作系统服务、云服务器控制台或密钥管理工具注入，不要把它们写进 README、源码或部署脚本。

### 第三方服务官方入口与最短配置方法

只配置你确实要用的服务。不要为了让 Backend 启动而注册全部平台。

| 服务 | 官方入口 | 最短配置动作 | 对应变量 |
| :--- | :--- | :--- | :--- |
| 阿里云 OSS | [OSS 控制台](https://oss.console.aliyun.com/) | 创建 Bucket 和 RAM AccessKey，记录地域对应的 Endpoint、Bucket 名称、AccessKey ID 和 Secret | `OURCAT_OSS_*` |
| Replicate | [API Tokens](https://replicate.com/account/api-tokens) | 注册并创建 API Token；再打开 `AI_ENABLED` 和 `AI_REPLICATE_ENABLED` | `AI_REPLICATE_API_TOKEN`、`AI_REPLICATE_ENABLED` |
| 和风天气 | [控制台项目设置](https://console.qweather.com/setting) | 创建项目并按控制台说明生成 JWT，同时记录该项目的 API Host | `OURCAT_QWEATHER_JWT`、`OURCAT_QWEATHER_API_HOST` |
| Open-Meteo | [官方 API 文档](https://open-meteo.com/en/docs) | 不需要申请 Key；保持默认地址即可 | `OURCAT_OPEN_METEO_*`（通常不填） |

OSS 还有一个容易被忽略的限制：当前后端把上传结果保存为 `https://<bucket>.<endpoint>/<object-key>`，Android 会直接读取这个 `publicUrl`。因此 Bucket 必须允许客户端读取对象，或你需要自行改造后端为下载接口、签名 GET URL 或 CDN；只配置“可上传”而禁止读取，会出现上传成功但图片无法显示。

### 私有方式：外部 `local/application-local.yml`

你原来的目录结构可以保留，但要理解它的加载方式：

```text
OurCat/
├── local/
│   └── application-local.yml       # 私有文件，不提交
└── backend/
    └── src/main/resources/
        ├── application.yml
        ├── application-dev.yml
        └── application-prod.yml
```

**外部 `local/application-local.yml` 不会自动生效。** 必须从 `backend/` 目录启动，并同时激活 `local` Profile、指定外部配置目录：

```powershell
cd backend
mvn spring-boot:run `
  "-Dspring-boot.run.profiles=local" `
  "-Dspring-boot.run.arguments=--spring.config.additional-location=optional:file:../local/"
```

macOS/Linux：

```bash
cd backend
mvn spring-boot:run \
  -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.arguments="--spring.config.additional-location=optional:file:../local/"
```

如果你已经打包成 JAR，命令写法如下：

```powershell
java -jar target/backend-1.0.0-SNAPSHOT.jar `
  --spring.profiles.active=local `
  --spring.config.additional-location=optional:file:../local/
```

文件名必须是 `application-local.yml`，目录必须是从 `backend/` 看过去的 `../local/`。只创建文件、不激活 Profile，等于没配置，Spring Boot 不会替你猜。

可以复制下面的**无密钥模板**，再在本机填写真实值。这个文件只能留在你自己的电脑或服务器上：

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ourcat?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: ourcat
    password: 替换成数据库密码
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
    locations: classpath:db/migration

ourcat:
  jwt:
    secret: 替换成至少32位的随机字符串
    expiration-ms: 86400000
  oss:
    endpoint: oss-cn-你的地域.aliyuncs.com
    bucket: 你的 Bucket 名称
    access-key-id: 你的 RAM AccessKey ID
    access-key-secret: 你的 RAM AccessKey Secret
  ai:
    enabled: false
    replicate-enabled: false
    # 启用 Replicate 时再改为 true 并填写 Token
    replicate-api-token: ""
  qweather:
    # 在和风天气控制台的项目设置中查看个人 API Host
    api-host: https://api.qweather.com
    jwt: ""
```

不想使用 OSS、Replicate 或和风天气时，不要随便填一个假值。留空或关闭对应功能，并按本文的功能分级逐项启用。

### 你原来的 `更新后端.py` 到底做了什么？

这个脚本是**私有部署脚本**，不是项目运行所必需的配置工具。它会在打包前：

1. 读取根目录 `local/application-local.yml`；
2. 临时覆盖 `backend/src/main/resources/application.yml`；
3. 执行 Maven 打包并上传 JAR 到远程服务器；
4. 在远程服务器停止服务、处理 Flyway 失败记录并重启；
5. 最后尝试恢复原来的配置文件。

它包含 SSH 登录、远程服务器、数据库和密钥相关信息。**公开仓库不需要它，新用户也不应该照着它配置。** 更重要的是，把真实配置覆盖进 `application.yml` 再打包，会产生两个风险：

- 真实密钥可能进入 Git diff、备份文件或构建产物；
- 生成的 JAR 可能把密钥打进 `BOOT-INF/classes/application.yml`，泄露后不能靠“删掉当前文件”补救。

公开项目的推荐替代方案是：生产环境使用环境变量；个人本地测试使用外部 `local/application-local.yml`。不要复制、改名、上传 `更新后端.py`，也不要把真实 `application-local.yml` 发给别人。

---

### 路径 A：开发模式（先跑基础功能）

开发模式使用 H2 内存数据库，**不需要安装 MySQL**。没有准备 Replicate 时，请按下面的方式关闭 AI；否则 Backend 仍可能启动，但调用 AI 时会返回配置错误。开发 Profile 使用的 JWT 密钥仅用于本机调试。

#### 1. 确保环境就绪

```bash
java -version   # 需要 JDK 11+
mvn -version    # 需要 Maven 3.6+
```

如果未安装：下载 [JDK 11](https://adoptium.net/) 和 [Maven](https://maven.apache.org/download.cgi)，解压后将 `bin/` 加入系统 PATH。

#### 2. 克隆并进入项目

```bash
git clone https://github.com/BlackCoder0/OurCat_backend.git
cd OurCat_backend
```

PowerShell（暂时关闭 AI）：

```powershell
$env:AI_ENABLED = "false"
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

macOS/Linux（暂时关闭 AI）：

```bash
AI_ENABLED=false mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

看到 `Started OurCatApplication` 就说明跑起来了。

#### 3. 启动并验证

```powershell
curl.exe http://localhost:8080/api/auth/captcha
```

> 📌 开发模式下 Flyway 自动禁用，改为 JPA `ddl-auto: create` 自动建表——每次重启数据库会清空，仅用于开发调试。验证码接口能返回，只能说明服务可访问，不能证明 OSS、AI、天气和生产数据库都正常。

H2 控制台：

当前 Spring Security 不开放 `/h2-console/**`，因此不要把 H2 Console 当作可用调试入口。开发模式请通过 API 或 Android 客户端验证；如果以后需要 H2 Console，应单独增加仅限本地开发的安全配置。

---

### 路径 B：生产部署（MySQL + 生产配置）

生产部署可放在你自己的本地服务器，也可放在云服务器。两者的 Backend 配置相同；区别主要是网络、防火墙、域名和 HTTPS。

本节不写真实服务器地址、账号或密钥。

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

> 如果应用和数据库不在同一台机器，需要按实际网络环境配置 MySQL 远程访问。不要为了省事把数据库端口直接暴露到公网。

#### 3. 设置生产环境基础变量

项目对敏感配置采用**不写入源码**策略——`application.yml` 中使用 `${VAR:}` 占位符，真实密钥通过环境变量或未提交的私有配置注入。

```powershell
# 数据库（对应上一步创建的库和用户）
$env:DB_HOST = "localhost"        # MySQL 地址，远程服务器填 IP 或内网地址
$env:DB_USERNAME = "ourcat"       # 数据库用户名
$env:DB_PASSWORD = "你设置的密码"  # 数据库密码

# JWT 签名密钥
$env:JWT_SECRET = "至少32位的随机字符串"
```

Linux/macOS：

```bash
export DB_HOST=localhost
export DB_USERNAME=ourcat
export DB_PASSWORD='你设置的密码'
export JWT_SECRET='至少32位的随机字符串'
```

按需配置 OSS、Replicate 和天气。它们不是生产 Backend 的统一启动条件，但对应功能要正常使用就必须配置。具体变量名和作用见上方[配置文件先讲清楚](#-配置文件先讲清楚)。

> 💡 环境变量只对当前终端窗口有效。需要长期运行时，请使用系统服务或云服务器的安全环境变量管理方式，不要把密钥提交到 Git。
>
> 💡 不要在这里把完整密钥粘贴进 README。需要文件方式时，请使用上方说明的外部 `local/application-local.yml`，并显式激活 `local` Profile。

#### 4. 构建并启动

```bash
# 构建
mvn clean package

# 启动（prod 配置会启用 MySQL + Flyway）
java -jar target/backend-1.0.0-SNAPSHOT.jar --spring.profiles.active=prod
```

首次启动时 **Flyway 自动执行 25 版数据库迁移**，在 `flyway_schema_history` 表中记录版本。之后每次升级只需增加新的迁移脚本，Flyway 会自动检测并执行未应用的版本——**全程无需手动写 SQL 建表**。

#### 5. 验证部署

```bash
# 公开认证接口冒烟测试
curl http://localhost:8080/api/auth/captcha
```

返回包含 `key` 和 `image` 的 JSON，只能说明服务可访问；它不是完整健康检查。

#### 常见问题排查

| 现象 | 可能原因 | 解决办法 |
| :--- | :--- | :--- |
| `Connection refused` | MySQL 未启动或端口不对 | `mysql -h $DB_HOST -u $DB_USERNAME -p` 测试连接 |
| `Access denied` | 用户名或密码错误 | 检查 `DB_USERNAME` / `DB_PASSWORD` 环境变量 |
| `Unknown database 'ourcat'` | 数据库未创建 | 执行 `CREATE DATABASE ourcat` |
| Flyway 报错 | 迁移版本冲突 | 检查 `flyway_schema_history` 表，清理失败记录 |
| AI 接口报错 | 未配置 Replicate 或 Token 无效 | 检查 `AI_ENABLED`、`AI_REPLICATE_ENABLED` 和 `AI_REPLICATE_API_TOKEN` |
| 图片上传失败 | OSS 未配置或 RAM 权限不足 | 检查 OSS Endpoint、Bucket 和 AccessKey |
| 官方天气预警失败 | 和风 JWT 或个人 API Host 不正确 | 检查 `OURCAT_QWEATHER_JWT` 和 `OURCAT_QWEATHER_API_HOST` |

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
| `/api/message` | ✉️ 消息 | 系统通知、天气预警记录 |
| `/api/org/**` | 🏛️ 组织 | 创建、加入、成员管理 |
| `/api/weather/**` | 🌤️ 天气 | 实时、预报、**温差/降水预警** |
| `/api/ai/**` | 🧠 AI | **图像特征提取、相似度匹配** |
| `/api/oss/**` | 📁 文件 | 获取上传预签名 URL |

> 📝 除认证接口外，所有请求需携带 Header：`Authorization: Bearer <token>`

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

关键接口行为：

| 方法 | 路径 | 鉴权 | 说明 |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/auth/captcha` | 否 | 返回验证码 `key` 和 Base64 图片 |
| `POST` | `/api/auth/register` | 否 | 注册需要 `username`、`password`、`captchaKey`、`captcha` |
| `POST` | `/api/auth/login` | 否 | 登录需要用户名、密码和验证码，返回 JWT |
| `POST` | `/api/cat/report` | 是 | 保存上报后异步匹配，响应先返回报告 ID |
| `GET` | `/api/cat/report/{reportId}` | 是 | 查询异步匹配后的报告详情 |
| `POST` | `/api/ai/detect` | 是 | 根据图片 URL 提取特征并返回候选猫咪 |
| `GET` | `/api/oss/upload-url?filename=cat.jpg&contentType=image/jpeg` | 是 | 获取 OSS 预签名上传地址；OSS 未配置时图片功能不可用 |
| `GET` | `/api/weather/warning` | 是 | 按 `location=纬度,经度` 查询天气预警并写入消息 |

OSS 上传步骤：先调用上面的接口，再使用返回的 `uploadUrl` 发起 `PUT`，并携带返回的 `contentType` 请求头；上传成功后，把返回的 `publicUrl` 作为图片地址提交给猫咪、论坛或救助接口。

---

## 🗄️ 数据关系

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

## 🔒 安全设计

* **凭据不入源码**：生产密钥通过环境变量或未提交的私有配置注入，公开源码不包含真实凭证。
* **密码 BCrypt 哈希**：用户密码使用 Spring Security BCryptPasswordEncoder 加盐存储。
* **JWT 无状态认证**：Token 过期时间可配置（默认 24h）；生产环境必须注入足够长度的 `JWT_SECRET`。
* **OSS 预签名上传**：客户端通过 `GET /api/oss/upload-url` 获取临时预签名 URL 直传，服务端不暴露 OSS AccessKey。
* **Flyway 版本迁移**：数据库变更可追踪并按版本执行；回滚需要单独编写逆向迁移或恢复备份。

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
| AI | Replicate 云端推理；需要配置 Token 和开关。未准备好时可在开发启动时关闭 AI。 |
| 地图 | Android 端使用高德地图 SDK |
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
- **已验证环境**：JDK 11 + Maven 3.8，H2 开发模式可编译测试
- **未在本仓库复核**：MySQL 生产启动、第三方服务调用
- **Last reviewed**：2026-08-29

---

## 📄 开源协议

MIT License

---

## 🌐 English

**OurCat** is a full-stack **Spring Boot + Android + MySQL** campus stray cat rescue platform. It provides REST APIs covering cat archiving with **Replicate-powered image recognition** (embedding + cosine similarity matching), rescue task scheduling with status workflow, forum & community square, weather queries and warnings, and Alibaba Cloud OSS presigned upload.

> 👉 **Backend repo** (this one) · [Android client](https://github.com/BlackCoder0/OurCat_Android)

---

OurCat — 让每一只校园猫都被看见 ❤️
  <sub>OurCat — 让每一只校园猫都被看见 ❤️</sub>
</p>
