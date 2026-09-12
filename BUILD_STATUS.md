# 构建状态说明

以各提交对应的 GitHub Actions 结果为准：

- Android build and release：网页/JVM 测试、Android Lint、Debug/Release 编译、APK 签名验证；main 成功后按版本发布普通 Release。
- Signed release：版本标签与固定密钥校验、检查、编译和发布。

当前版本命名统一为 0.1。下载时核对 build-info.txt 中的源码提交和对应 Actions 结果；源码或工作流文件存在不等于构建成功。

当前自动构建 APK 沿用 CI 临时签名；固定签名需仓库所有者配置密钥。命名简化不改变这一技术区别。未经真机验收，不宣称全品牌电视兼容。
