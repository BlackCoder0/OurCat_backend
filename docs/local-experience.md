# 在自己的电脑体验 OurCat

这是独立的本地实例，账号、内容和图片不会同步到作者的服务器。电脑必须保持运行，手机才能访问。

## Windows 体验包

适用 Windows 10/11 x64。包内包含 Java 17 运行时、Backend、Demo APK；不需要安装 JDK、Maven 或 Android Studio。目前属于预发布验证包，实体手机和陌生电脑尚未验收。

1. 把整个 ZIP 解压到可写目录，不要在压缩包里直接运行，也不要覆盖已有体验包。
2. 双击 `start.cmd`。看到 `Started OurCatApplication` 表示启动完成；窗口需要保持打开。
3. 把 `OurCat-demo.apk` 传到手机安装。它的应用名为“OurCat 本地体验”，包名 `com.ourcat.app.demo`，与原 App 可以共存。
4. 手机和电脑接入同一可信局域网，在登录页点“设置后端地址”。填写启动窗口显示的电脑 Wi-Fi 地址，例如 `http://192.168.1.20:8080/`。模拟器使用 `http://10.0.2.2:8080/`。
5. 在 `data/login-info.txt` 找到本机生成的管理员账号密码，输入图片上的验证码登录。也可以注册普通账号。

启动窗口可能列出 VPN、虚拟网卡等多个地址，请选电脑 Wi-Fi/以太网地址。手机自己的 `localhost` 不是电脑。若连不上，先在电脑浏览器访问 `http://localhost:8080/api/auth/captcha`；再检查地址、防火墙和路由器的设备隔离。只在可信网络允许此程序通信，无需关闭整个防火墙或做公网端口转发。

端口占用时，关闭原体验窗口，或编辑 `data/port.txt` 改为其他未占用端口，重新启动并在手机同步修改地址。切换后端需要先退出登录；新地址使用自己的账号和验证码。

## 可以完成的操作

浏览两只示例猫咪、帖子和救助活动；注册、修改资料、上传头像、发布带图帖子、上报猫咪。管理员可审核加入组织的申请、管理志愿者、分配任务；成员可申领任务、记录进度并提交完成汇报。

Demo APK 不内置高德 Key，不提供真实地图和定位。上报页明确使用示例坐标（上海 31.23, 121.47），用于学习业务流程。AI 默认关闭；基础天气仍需要网络。当前 Android 救助日志界面提供文字记录，后端可接收图片地址，不宣称有已完成的日志选图界面。

本地图片支持 JPEG、PNG、GIF，单张最多 10 MiB、2000 万像素，存放于 `data/images`。获取上传地址需要登录；PUT 使用 10 分钟有效的一次性票据。图片读取不要求登录，知道图片 URL 的同网设备可以读取，因此体验实例只用于自己的可信网络。

## 停止、备份和升级

在启动窗口按 `Ctrl+C` 停止服务，然后关闭窗口。停止后整个 `data` 目录就是备份对象：包括数据库、图片、本机 JWT、管理员初始化配置和账号信息。请私人保存，不上传仓库或发给其他体验者。

重启使用原 `data`，不重置账号密码。更改密码后，以 App 里的新密码为准，初始化文件不会替你重置密码。换版本前先停止并备份。此版只验证同版本重启保留，不承诺跨版本数据库自动升级。

日志位于 `logs/current.log`。反馈时给出版本、错误文字和操作步骤，先遮掉账号、Token 和个人信息。

## 从源码运行

Backend 需要 JDK 11 或更高版本、Maven（本机验证环境为 JDK 17）。在仓库根目录执行：

```powershell
$env:OURCAT_DEMO_JWT_SECRET = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
$env:OURCAT_DEMO_ADMIN_USERNAME = 'local-admin'
$env:OURCAT_DEMO_ADMIN_PASSWORD = Read-Host '设置本地管理员密码（至少 12 位）'
mvn spring-boot:run '-Dspring-boot.run.profiles=demo'
```

源码运行时数据默认位于 `data/demo`，图片在它的 `images` 子目录；可用 `OURCAT_DEMO_DATA_DIR` 改成其他目录。JWT 更换会使已登录的 Token 失效，账号和业务数据仍保留。不要在含生产凭据的终端运行 demo；启动保护会拒绝 MySQL、混用 Profile、开启 AI 或带 OSS/和风凭据的配置。

Android 构建见对应仓库。想启用真实地图、OSS 或 AI 时，从源码配置自己的服务，不修改本地体验包来接管作者资源。

## 开发者打包

先运行 Backend 的 `mvn package` 和 Android 的 `gradlew.bat assembleDemo`，再执行：

```powershell
.\packaging\build-demo.ps1 -RuntimeJdk '你的 JDK 17 目录' -DemoApk 'Demo APK 的绝对路径'
```

脚本只复制指定 JAR/APK、启动脚本、文档和用 jlink 生成的运行时，输出到 `target`；不会打入本地数据库或私有配置。默认目标目录已存在时拒绝覆盖。`runtime/legal` 保留运行时许可证；Java 运行时按其自身条款提供，OurCat 代码使用 MIT。发行包附带 `build-info.json` 和文件 SHA-256，公开上传前检查实际 ZIP 内容。
