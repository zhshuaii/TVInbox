# 轻收 · TVInbox

轻量 Android TV APK 收件箱：手机扫码上传，电视手动安装。

[构建记录](https://github.com/zhshuaii/TVInbox/actions/workflows/android.yml) · [下载 APK](https://github.com/zhshuaii/TVInbox/releases)

**上传只进入电视列表，不自动安装。手机不需要配套 App，也不查询安装进度。**

## 下载与使用

进入 Releases，在标有 Preview 的测试版本附件中下载 `TVInbox-v0.1.0-preview.apk`；GitHub 自动提供的 Source code 压缩包不是安装包。

将本工具安装到 Android 电视或盒子，打开“轻收”。首次安装本工具仍需 U 盘、ADB 或设备已有的安装方式。手机与电视连接同一可互通的局域网，扫描电视二维码；电脑也可以输入屏幕地址。选择单个独立 APK，等待手机显示上传完成，再用电视遥控器点击列表中的“安装”。首次可能需要允许轻收安装未知来源应用，最终仍由系统原生安装器确认。

电视提供 **安装、删除、清空安装包**。删除 APK 不卸载应用。

## 范围与文件管理

- 默认端口 `56321`，占用时依次尝试 `56322`–`56325`。
- 没有令牌、PIN、账号、广告、统计 SDK、云服务或外部网页资源。
- 只支持单个独立 `.apk`，一次接收一个；不支持 APKS、XAPK、APKM 或批量安装。
- 只选择 Wi-Fi / 有线网络的私有或链路本地 IPv4，不依赖外网，不通过 VPN 接收。
- 主动退出或离开应用后停服；本工具发起的系统授权/安装往返最多保留接收会话 10 分钟，返回主页面恢复接收。
- 不跟踪安装成功与否，不静默安装，不扫描已安装应用。
- 最低 Android 6.0（API 23）；各品牌电视兼容性仍需真机验证。

安装包最多 **10 个、合计 512 MiB、保留 7 天**。应用本体、少量元数据及系统安装所需空间不包含在收件箱配额内。

私有目录 `filesDir/apk-inbox/` 的 `incoming/` 保存接收残片，`ready/` 保存完整 APK 和元数据。内部文件名由应用生成，客户端文件名仅显示；禁止云备份和设备迁移备份。

上传使用 64 KiB 缓冲区直接写盘，完整接收且通过基础 APK 检查后才返回成功。断流、超时或校验失败清理残片，启动时补清。启动和新上传前清理过期包；仍超数量或容量时拒绝新上传，不悄悄删除未过期的包。正在供系统安装器读取的文件不会被清空或过期清理删除。安装后文件仍保留，按统一期限或手动操作删除。

## 网络边界

**无认证，只适用于可信局域网。非常用端口不是访问控制。HTTP 不加密，不要用于公共 Wi-Fi 或公网端口映射。**

保留 Host / Origin 检查、浏览器跨站请求限制、连接数量、文件长度与超时保护。HTTP 不提供远程安装、目录浏览、APK 下载或删除接口。

## GitHub 自动构建与发布

`main` 提交自动运行网页测试、JVM 测试、Android Lint、Debug 和裁剪后 Release 编译。全部通过后，将可安装的测试 APK、`SHA256SUMS` 和 `build-info.txt` 自动发布到 **GitHub Releases 的 Pre-release**，标签格式 `v0.1.0-preview.<构建序号>`。PR 只检查和生成 Artifacts，不公开发布。

测试包名 `io.github.zhshuaii.tvinbox.debug`，使用 CI debug 签名。不同独立 runner 构建不保证能覆盖更新；签名不同时需先卸载测试版，卸载会清除其收件箱。不要把测试签名视为正式更新身份。

正式包名 `io.github.zhshuaii.tvinbox`。配置固定签名 Secrets 后，推送与 versionName 对应的 `vX.Y.Z` 标签，Signed release 工作流验证并发布正式签名 APK。不把 unsigned APK 当作可安装正式包。详见 [签名发布](docs/RELEASING.md)。

## 本地构建

固定工具链：JDK 17、Gradle 8.11.1、AGP 8.9.2、Kotlin 2.1.21、Android SDK 35。

GitHub Actions 安装 Gradle 并生成官方 Wrapper，`gradle-wrapper` artifact 提供 wrapper 文件。本地已有 Gradle 8.11.1 时运行：

```sh
gradle wrapper --gradle-version 8.11.1 --distribution-type bin
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Windows 使用 `gradlew.bat`。需要配置 `ANDROID_HOME` 或 `local.properties` 的 `sdk.dir`。

工程只有一个 Android 模块，使用 Kotlin、原生 XML、内置 HTML/CSS/JavaScript、AndroidX Core、NanoHTTPD 和 ZXing；没有 React/Vue、Compose、Room、WorkManager 或依赖注入框架。

## 验证与许可

自动检查不能代替真机测试。参见 [验收清单](docs/TESTING.md)，每个 APK 的实际编译结果以其对应的 Actions 记录为准。

新增代码使用 MIT；依赖采用各自许可证，见 [第三方说明](THIRD_PARTY_NOTICES.md)。
