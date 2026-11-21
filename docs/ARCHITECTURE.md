[English](ARCHITECTURE.en.md) | [简体中文](ARCHITECTURE.md)

# Android Credential Switcher 架构设计文档

本文档深入阐述 Android Credential Switcher 的核心架构、`Settings.Secure` 三键模型、Android 15–17 限读镜像算法以及 HyperOS 4 通行密钥选择器的底层行为分析。

## 1. 核心架构与运行模型

本项目针对 <b>Android 14–17</b>（API 34–37，兼容低版本编译门槛至 `minSdkVersion 29`）打造，重点解决 <b>小米 HyperOS 3/4</b>、<b>ColorOS</b>、<b>OriginOS</b> 等国行定制系统在图形设置中隐藏或缺失「其他服务」开关的问题。应用完全采用标准 Java + 原生 Android Framework 编写，零第三方库、零 Gradle 构建依赖、零 `INTERNET` 网络权限。

### 1.1 模块化工程目录树

```
android-credential-switcher/
├── .github/
│   └── workflows/
│       └── build.yml                  # GitHub Actions 零 Gradle 自动构建工作流
├── assets/
│   ├── logo.svg                       # 矢量品牌图标
│   └── preview-zh.png                 # 脱敏真机实拍预览图
├── docs/
│   ├── ARCHITECTURE.en.md             # 英文架构设计文档
│   └── ARCHITECTURE.md                # 中文架构设计文档
├── res/
│   ├── drawable/                      # 矢量前景/背景与旧版兼容图标
│   ├── drawable-anydpi-v26/           # Android 8+ 自适应图标定义
│   ├── values/                        # 默认英文应用名称
│   └── values-zh/                     # 中文应用名称
├── src/io/github/toruolenyeva/credswitcher/
│   ├── CredentialStore.java           # 服务枚举、Settings.Secure 读写、镜像状态与指针清理
│   ├── MainActivity.java              # 单页主控交互与折叠排查卡片
│   ├── Strings.java                   # 运行时中英双语字典与首启语言自适应
│   └── UiKit.java                     # 日夜配色、水波纹触控反馈与状态栏着墨安全封装
├── AndroidManifest.xml                # 权限契约与 Intent Queries 声明
├── build.ps1                          # Windows 离线 CLI 构建脚本（自动探测 SDK/JDK）
├── build.sh                           # macOS / Linux 离线 CLI 构建脚本
├── CHANGELOG.md                       # 版本变更记录
├── LICENSE                            # MIT 开源许可协议
├── README.en.md                       # 英文项目说明文档
└── README.md                          # 中文项目说明文档
```

```
+-------------------------------------------------------------------+
|                   Android / HyperOS 系统框架层                     |
|  +------------------------+         +--------------------------+  |
|  |   PackageManager       |         |   Settings.Secure        |  |
|  |   (Intent 服务发现)     |         |   (三键配置存储与广播)     |  |
|  +-----------+------------+         +------------+-------------+  |
+--------------|-----------------------------------|----------------+
               | queryIntentServices               | WRITE_SECURE_SETTINGS
               v                                   v
+-------------------------------------------------------------------+
|               Android Credential Switcher (无网络离线进程)         |
|  +-------------------------------------------------------------+  |
|  | CredentialStore.java : 三键原子协同写 + 限读镜像 + 失效清理     |  |
|  +-------------------------------------------------------------+  |
|  | Strings.java / UiKit.java / MainActivity.java : 双语自适应 UI  |  |
|  +-------------------------------------------------------------+  |
+----------------------------------+--------------------------------+
                                   |
                                   v
                SharedPreferences ("cred_switcher_state")
```

## 2. `Settings.Secure` 三键模型与限读镜像算法

Android Credential Manager（Android 14+）与 Autofill Framework 通过 `Settings.Secure` 中的三个核心键值协调全局密码管理器与通行密钥行为：

| 设置键名 | 权限/读取限制 | 数据格式 | 职责边界与联动逻辑 |
| :--- | :--- | :--- | :--- |
| `credential_service_primary` | 写入需 `WRITE_SECURE_SETTINGS`；Android 15+ 直读需系统签名权限 | 单个 `ComponentName` 字符串或 `null` | 指定默认首选通行密钥管理器；在面板中选中时自动同步加入多选列表并联动同包名 Autofill |
| `credential_service` | 同上（受 `LIST_ENABLED_CREDENTIAL_PROVIDERS` 保护） | 冒号 `:` 分隔的多个 `ComponentName` | 控制「其他服务」开启列表；支持多个管理器同时参与站点通行密钥检索 |
| `autofill_service` | 普通应用保持 `Settings.Secure.getString` 直读 | 单个 `ComponentName` 字符串或 `null` | 控制输入框自动填充（账号密码）服务；支持在底部卡片独立切换或关闭 |

### 2.1 Android 15–17 列表直读受限兼容策略

在 Android 15 / 16 / 17 上，非 `signature|privileged` 应用调用 `Settings.Secure.getString()` 读取 `credential_service` 与 `credential_service_primary` 时会静默返回 `null`，但调用 `Settings.Secure.putString()`（持有 `WRITE_SECURE_SETTINGS`）依然拥有完整的系统写入权限。为此 `CredentialStore.loadSnapshot()` 采用三级状态解析算法：

1. <b>系统直读优先</b>：若当前 ROM 未拦截读取（非 `null`），直接使用实时系统返回值并校验组件有效性。
2. <b>已初始化镜像回落</b>：若返回 `null` 且本地 `SharedPreferences` 存在 `initialized=true` 标记，读取上次写入的 `mirrored_primary` 与 `mirrored_creds`。
3. <b>首次运行智能引导</b>：首次安装尚未执行任何写入时，利用仍可直接读取的 `autofill_service` 反查同包名的 `CredentialProviderService` 组件，将界面初始状态无缝对齐用户当前的默认配置。

### 2.2 幽灵服务指针清理

当 `Settings.Secure` 中残留已卸载软件或包名变更后的失效组件字符串时，系统底层遍历易出现超时或异常回退。`detectGhostPointers()` 逐一通过 `PackageManager.getServiceInfo(cn, 0)` 探测存活状态，发现失效项即展示告警卡片并提供一键清理。

## 3. HyperOS 4 选择器行为分析与防重置机制

### 3.1 `MiuiCredentialManager` 锁定态回退原因

通过分析 HyperOS 4 系统内置组件 `MiuiCredentialManager`（基于 Compose 与 `miuix.appcompat.app.AlertDialog` 改写）：

- 原生 AOSP Jetpack 凭据选择器会在密码管理器锁定且支持延迟鉴权时展示 `AuthenticationEntryInfo`（即「点击解锁软件」）卡片；
- `MiuiCredentialManager` 的 `GetMiCredentialScreen` 仅处理包含真实条目的 `PRIMARY_SELECTION` 状态，遇到锁定态或空条目状态直接路由至 `onIllegalUiState()` 并以 `cancelCode = 3` 结束选择器；
- 浏览器（Edge / Chrome）收到取消错误后触发内部兜底链路，转而拉起 Google Password Manager 提示未找到通行密钥；
- 仅当开启的服务中有至少 1 个处于已解锁且保存有当前站点密钥时，小米选择器才会呈现单条确认（总条目数等于 1）或 `CheckedItemAdapter` 多选列表（总条目数大于等于 2）。

### 3.2 系统后台重置对策

部分定制系统的安全中心会在 OTA 更新或后台自检阶段调用内部接口覆写默认自动填充与凭据服务。本应用无需开启后台常驻服务耗电，而是通过「一键重刷配置到系统」按钮，在需要时将已确认的首选、多选列表与自动填充三键一次性重新落盘。

## 4. 零 Gradle 构建链路与权限边界契约

### 4.1 离线 CLI 构建流水线

`build.ps1` 与 `build.sh` 直接编排 Android SDK 标准工具链，完全绕过 Gradle Daemon 与远程 Maven 依赖：

1. `aapt2 compile`：编译 `res/` 目录下双语字符串与 `adaptive-icon` 资源；
2. `aapt2 link`：生成 `R.java` 与未压缩 `resources.arsc` 的基础 APK；
3. `javac --release 11`：编译 `src/` 与 `R.java` 为 Java 11 字节码；
4. `d8 --min-api 29`：脱糖并打包为单个 `classes.dex`；
5. `zip` + `zipalign -p -f 4`：将 `classes.dex` 注入 APK 并按 4 字节对齐；
6. `apksigner sign` + `verify`：使用本地忽略的 keystore 完成 V2/V3 签名与证书核验。

### 4.2 安全与存储契约

- <b>最小权限集</b>：仅申请 `android.permission.WRITE_SECURE_SETTINGS`（ADB 一次性授予，卸载即失效）与 `android.permission.QUERY_ALL_PACKAGES`（配合 `<queries>` 枚举本机密码管理器），绝不申请联网、存储或通讯录等无关权限。
- <b>`SharedPreferences` 存储键（`cred_switcher_state`）</b>：仅存储 `initialized`（boolean）、`mirrored_primary`（String）、`mirrored_creds`（String）与 `ui_lang`（`"auto"` / `"zh"` / `"en"`），并在 `AndroidManifest.xml` 中显式设置 `android:allowBackup="false"`。
