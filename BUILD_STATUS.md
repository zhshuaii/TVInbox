# 构建状态说明

以每个提交对应的 GitHub Actions 运行结果为准：

- Android build and preview release：网页测试、JVM 测试、Android Lint、Debug/Release 编译、测试 APK 签名验证；main 成功后自动发布到 Releases。
- Signed release：标签与固定签名校验、测试、Lint、正式 APK 编译与发布。

仓库中的源码和工作流存在，不等于构建已经成功。下载 Release 时核对 build-info.txt 中的源码提交及对应 Actions 结果。

测试 APK 使用 debug 签名；正式版需要仓库所有者的固定签名 Secrets。未经真机验收不能宣称全品牌电视兼容。
