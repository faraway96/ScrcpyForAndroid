# Legacy Port: Android 6.0 (API 23) 支持补丁

本分支将 minSdk 从 26 (Android 8.0) 降到 23 (Android 6.0)。以下为全部改动与已知限制。

## 构建配置

| 文件 | 改动 |
|---|---|
| `app/build.gradle.kts` | `minSdk` 26→23；启用 core library desugaring（`java.time`/`java.nio.file` 在 API 26 以下由 desugar_jdk_libs 2.1.5 提供）；移除 `androidx.core:core-pip`（其 AAR 元数据要求 minSdk 24）；`versionName` 标记为 `0.6.0-api23` |
| `gradle/libs.versions.toml` | 移除 `core-pip` 相关条目 |
| `.github/workflows/*.yml` | CI 中对 miuix 子模块执行 `MIN_SDK 24→23` 补丁；无签名密钥 secrets 时自动生成临时 keystore，保证 fork 也能出可安装的签名 APK |

## 代码改动

| 文件 | 改动 |
|---|---|
| `StreamActivity.kt` | 用平台 API 复刻 `androidx.core.pip.BasicPictureInPicture`：`PictureInPictureParams.Builder` + `onUserLeaveHint` 手动进入（API 26-30）/ `setAutoEnterEnabled`（API 31+）；所有 PiP 入口加 `SDK_INT >= 26` 守卫；`dismissActivePictureInPicture` 加守卫（`isInPictureInPictureMode` 需 API 24+） |
| `ScrcpyAudioPlayer.kt` | `AudioTrack#setPerformanceMode` / `#getPerformanceMode`（API 26）加版本守卫，低版本自动回落普通延迟模式 |
| `res/mipmap-mdpi..xxxhdpi/` | 新增 legacy 启动图标（由 `ic_launcher-playstore.png` 缩放生成），原工程只有 `mipmap-anydpi-v26` 自适应图标，API 25 以下无图标可用 |

## 依赖 minSdk 审计结论（全部 ≤ 23，无卡点）

core-ktx/core 1.19=23 · lifecycle 2.11=23 · activity-compose 1.13=23 · compose ui/runtime/foundation 1.12=23 · material3 1.4=21 · navigation3 1.1.7=23 · biometric 1.4=23 · material 1.14=23 · security-crypto 1.1=21 · datastore 1.2.1=23 · conscrypt 2.7=21 · reorderable 3.1=21 · backdrop 2.0.1=21 · boringssl/libcxx prefab=21 · tinypinyin/bcpkix/kotlinx-serialization 无 AAR 约束

## Android 6.0 平板上的已知限制（如实告知）

1. **画中画不可用** —— 系统（API <26）本身不支持，相关 UI 不生效；串流全屏正常。
2. **音频低延迟模式回落** —— `PERFORMANCE_MODE_LOW_LATENCY` 需要 API 26，6.0 上使用普通缓冲。受控端音频转发本来就要 Android 11+。
3. **PiP 自定义关闭按钮**（API 33+ 的 `setCloseAction`）在 8.0-12 上不显示，用系统自带关闭按钮。
4. **miuix UI 库**官方 minSdk 24，本补丁强制降到 23；未在真机验证，若个别动效（如模糊）异常属于预期内。模糊相关（`miuix.blur`）在旧设备本就降级。
5. 上游作者明确表示没有低版本设备做过测试；Compose 1.12 声称支持 API 23 但同样未经上游验证。遇到崩溃请带 logcat 反馈。

## 受控设备注意

控制端跑在 Android 6.0 平板上；**被控设备**由 scrcpy-server 决定（视频镜像要求较低，音频转发要求 Android 11+）。老平板性能有限，建议受控端降低分辨率/帧率。
