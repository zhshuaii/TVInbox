# 轻收

轻量 Android TV APK 收件箱。版本 **0.1**，项目名 `TVInbox`。

[Releases](https://github.com/zhshuaii/TVInbox/releases) · [构建记录](https://github.com/zhshuaii/TVInbox/actions/workflows/android.yml)

**手机扫码上传，电视手动安装。上传只加入列表，不自动安装或弹窗。**

## 使用

在 Releases 下载 `TVInbox-v0.1.apk`，Source code 压缩包不是安装包。首次将本工具装到电视，使用 U 盘、ADB 或设备已有的安装方式。

电视打开轻收，手机连接可互通的同一局域网，扫码选择 APK 上传；电脑也能输入屏幕地址。手机显示上传完成后即可关闭网页。在电视列表选择“安装”，由系统原生安装器确认；首次可能需要允许轻收安装未知来源应用。

电视提供 **安装、删除、清空安装包**。删除 APK 不卸载已安装应用。

## 范围

默认端口 `56321`，占用时尝试 `56322`–`56325`。无令牌、PIN、账号、广告、统计或云服务。网页资源全部内置。只接收独立 APK，一次一个；不处理 APKS、XAPK、APKM，不做批量安装、自动安装、安装结果跟踪或已安装应用扫描。

只使用 Wi-Fi / 有线局域网的私有或链路本地 IPv4，不依赖外网，不通过 VPN 接收。主动离开应用后停服；本应用发起的授权/安装往返最多保留接收会话 10 分钟。最低 Android 6.0，具体设备兼容性需真机验收。

## 文件管理

最多 **10 个、512 MiB、保留 7 天**。APK 位于私有目录 `filesDir/apk-inbox/`，禁用云备份和设备迁移备份。应用本体、元数据、系统安装空间不包含在收件箱配额内。

上传按流写入，完整接收且通过基础检查后才确认成功。中断清理残片，下次启动补清；启动和上传前清理过期文件。仍超限就拒绝新上传，不悄悄删除未过期文件。正在供安装器读取的包不参与清理。安装后 APK 仍保留，由到期或手动删除回收。

## 网络边界

**仅用于可信局域网。无认证、HTTP 不加密，非常用端口不是访问控制，不要映射到公网。** 保留 Host / Origin 检查、连接数量、文件长度和超时保护。网页没有远程安装、文件浏览、下载或删除接口。

## 名称与签名

电视和网页名称均为 **轻收**，不拼接英文名称。发布包名固定为 `io.github.zhshuaii.tvinbox`，版本 `0.1`，不带额外后缀。

已生成独立的 RSA-4096 签名身份，公钥证书指纹保存在 `signing/release-cert.sha256`。私钥只通过仓库 Secret `TVINBOX_SIGNING_BUNDLE` 提供；不提交私钥、密码或签名备份包。

**切换状态：签名 Secret 配置成功并重新运行 Actions 后，才会发布固定签名 APK。在此之前，旧 Release 附件没有被替换。** 工作流不回退到临时签名，缺少 Secret 时仅完成编译检查，不签名或发布。详见 [签名发布](docs/RELEASING.md)。

旧包名 `io.github.zhshuaii.tvinbox.debug` 和新包名是两个应用，收件箱不共享。后续保持新包名及同一签名，并递增 versionCode 更新。

## 构建

仓库只保留 `main`，不自动生成依赖更新分支。工具链为 JDK 17、Gradle 8.11.1、AGP 8.9.2、Kotlin 2.1.21、SDK 35。

Actions 执行网页测试、签名脚本测试、JVM 测试、Lint 和裁剪后 Release 编译，再独立签名并核对固定证书，验证通过后发布 Releases。Gradle 编译阶段拿不到私钥。仅 main 的可信工作流可以签名和发布，PR 只检查。

本地可用 Gradle 8.11.1 生成官方 Wrapper：

```sh
gradle wrapper --gradle-version 8.11.1 --distribution-type bin
./gradlew testDebugUnitTest lintDebug lintRelease assembleRelease
```

Windows 使用 `gradlew.bat`。配置 `ANDROID_HOME` 或 `local.properties` 的 `sdk.dir`。本地未签名的编译产物不是可直接安装的发布包。

一个 Android 模块：Kotlin、原生 XML、内置 HTML/CSS/JavaScript、AndroidX Core、NanoHTTPD、ZXing。不引入大型前端框架或数据库。

## 验收与许可

自动检查不替代真机验收，见 [验收清单](docs/TESTING.md)。下载时核对 build-info.txt 和对应 Actions 结果。新增代码采用 MIT，依赖见 [第三方说明](THIRD_PARTY_NOTICES.md)。
