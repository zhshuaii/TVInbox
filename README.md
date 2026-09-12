# 轻收 · TVInbox

轻量 Android TV APK 收件箱。当前版本 **0.1**。

[下载 APK](https://github.com/zhshuaii/TVInbox/releases/latest) · [构建记录](https://github.com/zhshuaii/TVInbox/actions/workflows/android.yml)

**手机扫码上传，电视手动安装。上传只加入列表，不自动安装或弹窗。**

## 下载与使用

在 Releases 下载 `TVInbox-v0.1.apk`。Source code 压缩包不是安装包。

首次将轻收安装到 Android 电视或盒子，需要 U 盘、ADB 或设备已有的安装方式。打开轻收，手机与电视连接可互通的同一局域网，扫描二维码后选择单个 APK 上传；电脑也可以输入屏幕地址。

手机显示上传完成后可以关闭网页。使用电视遥控器选择安装包，点击“安装”，由系统原生安装器确认。首次可能需要允许轻收安装未知来源应用。

电视提供 **安装、删除、清空安装包**。删除 APK 不卸载已安装应用。

## 功能边界

- 默认端口 `56321`，占用时依次尝试 `56322`–`56325`。
- 无令牌、PIN、账号、广告、统计 SDK、云服务或外部网页资源。
- 一次接收一个独立 `.apk`，不支持 APKS、XAPK、APKM 或批量安装。
- Wi-Fi / 有线网络使用私有或链路本地 IPv4，不依赖外网，不通过 VPN 接收。
- 主动退出或离开应用后停服；本工具发起的系统授权/安装往返最多保留接收会话 10 分钟，返回主页面恢复接收。
- 不跟踪安装结果、不静默安装、不扫描已安装应用。
- 最低 Android 6.0（API 23），具体电视兼容性需真机验收。

## 文件管理

最多 **10 个、合计 512 MiB、保留 7 天**。应用本体、元数据和系统安装所需空间不计入收件箱配额。

APK 位于应用私有目录 `filesDir/apk-inbox/`。上传按流写入，完整接收并通过基础格式检查后才返回成功。中断上传清理残片，下次启动补清；启动和新上传前清理过期文件。仍超限时拒绝新上传，不悄悄删除未过期文件。

安装后 APK 仍保留，按保留期限或手动操作删除。正在供安装器读取的文件不会被清空或过期清理删除。禁用云备份和设备迁移备份。

## 网络边界

**仅适用于可信局域网。无认证，HTTP 不加密；非常用端口不是访问控制。不要开放到公网或公共 Wi-Fi。**

保留 Host / Origin 检查、连接数量、文件长度和超时保护。不提供网页远程安装、目录浏览、APK 下载或删除接口。

## 构建与发布

仓库只保留 `main`，移除 Dependabot 自动版本更新配置，不为依赖升级自动创建分支。

`main` 提交执行网页测试、JVM 测试、Android Lint、Debug/Release 编译和 APK 签名验证。全部通过后，按应用版本发布普通 Release：`v0.1`、`TVInbox-v0.1.apk`，附带 `SHA256SUMS` 和 `build-info.txt`。同一版本不重复创建、也不覆盖已发布附件；新提交的构建产物仍可从 Actions Artifacts 获取。发布下一版本时递增 `versionCode` 并修改 `versionName`。

应用名称统一为“轻收 · TVInbox”，版本名称无额外后缀。当前自动构建附件沿用包名 `io.github.zhshuaii.tvinbox.debug` 与 CI 临时签名；名称统一不代表签名机制改变。跨构建覆盖安装可能因签名不同而失败；卸载原应用会清除其收件箱。

固定签名构建使用包名 `io.github.zhshuaii.tvinbox`，需要仓库所有者配置固定密钥，详见 [签名发布](docs/RELEASING.md)。

## 本地构建

工具链：JDK 17、Gradle 8.11.1、AGP 8.9.2、Kotlin 2.1.21、Android SDK 35。

GitHub Actions 使用官方 Gradle 生成 Wrapper，`gradle-wrapper` artifact 提供生成文件。本地已有 Gradle 8.11.1 时：

```sh
gradle wrapper --gradle-version 8.11.1 --distribution-type bin
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Windows 使用 `gradlew.bat`，配置 `ANDROID_HOME` 或 `local.properties` 中的 `sdk.dir`。

工程只有一个 Android 模块：Kotlin、原生 XML、内置 HTML/CSS/JavaScript、AndroidX Core、NanoHTTPD、ZXing。没有 React/Vue、Compose、Room、WorkManager 或依赖注入框架。

## 验收与许可

自动检查不替代真机验收，参见 [验收清单](docs/TESTING.md)。每个 APK 的编译结果以 `build-info.txt` 对应提交的 Actions 记录为准。

新增代码采用 MIT，依赖采用各自许可证，见 [第三方说明](THIRD_PARTY_NOTICES.md)。
