本地体验优先阅读 [本地运行教程](local-experience.md)。本页供需要独立配置开发或生产环境的人使用。

# 配置与部署

不要一开始就注册所有第三方平台。按下面的顺序配置：

| 阶段 | 需要什么 | 缺少时的影响 |
| :--- | :--- | :--- |
| 先启动 Backend | JDK 11、Maven | 无法构建或启动 |
| 本地基础业务 | dev 使用内存 H2；demo 使用文件 H2 | dev 重启清空；demo 保留 |
| 生产数据库 | MySQL、数据库账号、生产 JWT 密钥 | 生产模式无法正常使用 |
| 图片能力 | `demo` 本地目录；生产/云端阿里云 OSS | `dev` 未配置 OSS 时不可用；`demo` 不需要 OSS |
| AI 识猫 | Replicate Token 和开关 | AI 特征提取、相似匹配不可用 |
| 基础天气 | Open-Meteo，默认无需 Key | 基础天气和计算型提醒不可用 |
| 官方灾害预警 | 和风天气 JWT | 官方灾害预警不可用，基础天气不受影响 |
| Android 地图 | Android 端高德 Key | Android 地图和定位不可用，不影响 Backend 启动 |

Backend 能启动，不等于所有外部功能都已配置完成。

---

## 配置文件

敏感配置不要写入公开的 `application.yml`。使用环境变量，或使用未提交的外部 `local/application-local.yml`。

### 这些配置文件分别负责什么？

| 文件 | 作用 | 是否应该写个人密钥 |
| :--- | :--- | :--- |
| `src/main/resources/application.yml` | 全项目的公开基础模板，提供默认值和环境变量占位符 | **不应该** |
| `src/main/resources/application-dev.yml` | `dev` Profile 的开发覆盖配置，使用 H2 内存数据库并关闭 AI | 仅本地调试，不用于生产 |
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
| JWT 签名密钥 | `JWT_SECRET` | 生产必填；仅 `dev` Profile 可暂用默认值 | 主配置会回退到 `change-me-in-local-profile`，不会自动阻止生产启动；生产必须显式替换 |
| JWT 有效期 | `JWT_EXPIRATION_MS` | 否，默认 24 小时 | 使用默认有效期 |
| 阿里云 OSS | `OURCAT_OSS_ENDPOINT`、`OURCAT_OSS_BUCKET`、`OURCAT_OSS_ACCESS_KEY_ID`、`OURCAT_OSS_ACCESS_KEY_SECRET` | 云端图片功能必填；demo 使用本地目录 | 图片、头像、帖子图片无法正常上传 |
| AI 总开关 | `AI_ENABLED` | 否 | 设置为 `false` 时关闭全部 AI |
| Replicate AI | `AI_REPLICATE_ENABLED`、`AI_REPLICATE_API_TOKEN` | AI 识猫必填 | AI 特征提取和相似匹配不可用 |
| 和风天气 | `OURCAT_QWEATHER_API_HOST`、`OURCAT_QWEATHER_JWT` 或 `OURCAT_QWEATHER_JWT_FILE` | 官方灾害预警必填 | 基础天气仍可用，官方灾害预警不可用 |
| Open-Meteo | `OURCAT_OPEN_METEO_*` | 否，默认无需 Key | 使用默认 Open-Meteo 配置 |
| 服务端口 | `SERVER_PORT` | 否，默认 `8080` | 使用默认端口 |

`AMAP_KEY` 是 Backend 配置中的占位项，不会自动传给 Android；Android 地图 Key 单独通过 `OURCAT_AMAP_KEY_DEBUG` 或 `OURCAT_AMAP_KEY_RELEASE` 注入。它不是 Backend 启动条件，Android 端的配置请看 [Android README](https://github.com/BlackCoder0/OurCat_Android)。

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

### 第三方服务配置入口

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

### 开发模式

开发模式使用 H2 内存数据库，**不需要安装 MySQL**。AI 默认关闭；开发 Profile 使用的 JWT 密钥仅用于本机调试，不能用于生产环境。H2 使用内存存储，服务每次重启都会清空数据。

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

PowerShell：

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

macOS/Linux：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
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

### 基础生产启动

基础生产启动可放在你自己的本地服务器，也可放在云服务器。两者的 Backend 配置相同；区别主要是网络、防火墙、域名和 HTTPS。

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

按需配置 OSS、Replicate 和天气。它们不是生产 Backend 的统一启动条件，但对应功能要正常使用就必须配置。具体变量名和作用见上方[配置文件](#配置文件)。

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

首次启动时 **Flyway 自动执行 25 版数据库迁移**，并在 `flyway_schema_history` 表中记录版本。之后增加新的迁移脚本即可由 Flyway 执行，通常不需要手动写 SQL 建表。

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
| Flyway 报错 | 迁移执行失败或数据库状态与迁移不一致 | 先检查报错 SQL 和数据库状态；修复迁移后按 Flyway 文档处理。不要直接删除迁移记录或重置非测试数据库 |
| AI 接口报错 | 未配置 Replicate 或 Token 无效 | 检查 `AI_ENABLED`、`AI_REPLICATE_ENABLED` 和 `AI_REPLICATE_API_TOKEN` |
| 图片上传失败 | OSS 未配置或 RAM 权限不足 | 检查 OSS Endpoint、Bucket 和 AccessKey |
| 官方天气预警失败 | 和风 JWT 或个人 API Host 不正确 | 检查 `OURCAT_QWEATHER_JWT` 和 `OURCAT_QWEATHER_API_HOST` |

---
