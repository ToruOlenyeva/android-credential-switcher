# Changelog

## [v1.0.0] - 2026-11-22

### Added
- 离线单页控制面板，免 Root、免 Shizuku、零网络权限独立读写 Android 14–17 `Settings.Secure` 凭证与自动填充核心配置（`credential_service_primary`、`credential_service`、`autofill_service`）。
- 完整支持被 HyperOS 3/4、ColorOS、OriginOS 等定制系统隐藏或缺失的「其他服务」通行密钥多选开关，支持同时启用 Bitwarden、Keyguard、Google 密码管理工具等多个服务。
- 运行时中英双语切换（顶部 `EN` / `中文` 胶囊按钮）与系统语言自动跟随，配合系统级浅色 / 暗色主题与原生水波纹反馈。
- Android 15–17 列表读取受限镜像同步机制（基于 `SharedPreferences` 与可读的 `autofill_service` 首启引导）、无效/卸载残留服务指针探测与清理、以及「一键重刷配置到系统（修复 OTA 重置）」功能。
- 可折叠 HyperOS / 国产系统常见问题排查卡片（锁定态回退谷歌原因、单选/多选触发条件及权限持久化说明）。
- 零 Gradle 跨平台离线构建脚本（`build.ps1` / `build.sh`）与 GitHub Actions CI 自动构建工作流。
