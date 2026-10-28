# HourlyChime 项目约定（长期）

- 公司端开发目录 E:/android/HourlyChime ↔ github.com/lenstudio/HourlyChime（main），家中 Mac m8 共享 \192.168.1.8\len\android
- **任何代码/资源更新构建后，都要 adb install -r 到手机**（设备 d2df3756）：`/e/android/android-sdk/platform-tools/adb.exe install -r HourlyChime.apk`；装机后确认 Success
- 构建命令：`SDK='E:\android\android-sdk' sh build.sh`（需 JAVA_HOME=openjdk17；release.keystore 在项目目录自动签名）
- Git push：优先直连（`git -c http.proxy= -c https.proxy= push`），失败换默认代理 9080；两条路都可能抖，交替重试
- 手机为 Android 16：adb backup 失效；备份配置只能「同签名+debuggable 临时包 + run-as 读 prefs」
- 签名统一 release.keystore（CN=HourlyChime），手机已迁移到该签名，可直接 install -r
- holidays.json 需每年国办发布后更新（约 11 月）；2027 待补
- **MIUI 漏响根因（2026-10-28 排查定案）**：`setExactAndAllowWhileIdle` 在 MIUI 上退化为普通 setExact —— dumpsys 显示 `exactAllowReason=policy_permission`（非 `allow-listed`），uid 10494 不在 deviceidle 白名单；叠加 `delay_nonwakeup_alarms_while_screen_off=true` 与 Doze，派发被大幅延迟（统计 Max delay 7m57s、Max non-interactive 5h31m）→ ChimeReceiver 在“当前分钟”找不到命中方案就静默跳过 = 漏响。MIUI 无 root 拿不到白名单，唯一可靠方案是 ForegroundService + PARTIAL_WAKE_LOCK 自持时间线（配合 MIUI 手动开：自启动/后台弹窗/省电=无限制）
