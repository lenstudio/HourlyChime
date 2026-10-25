# HourlyChime 整点报时

一个极简的 Android 整点报时 / 定时提醒应用（`com.len.hourlychime`）。无任何依赖库、无布局 XML，纯程序化 UI；代码本体不到 13KB，含 8 个内置铃声后单 APK 约 1.3MB。为替代已停止维护的 com.caynax.hourlychime 而自研。

## 功能

- **多方案调度**：方案 = 名称 + 适用星期 + 规则集 + 铃声 + 音量，可自由组合
- **规则语法**：`H:M[-H:M][/步长]`，逗号分隔多条，如 `9:00-18:00/60, 12:30`
- **铃声三来源**：App 内置 / 系统音效（RingtoneManager 枚举）/ 自选文件（SAF 文件选择器，持久授权）
- **音量独立控制**：临时调整 STREAM_NOTIFICATION 播放，播完自动恢复原音量
- **法定工作日 / 休息日同步**：内置 `assets/holidays.json`，规则可区分工作日/周末/节假日
- **可靠触发**：AlarmManager 精确闹钟 + `goAsync()` 保证播完才结束；开机 / 时间变更 / 时区变更自动重排；回到主界面自愈重排
- 支持振动、沉浸式状态栏

## 源码结构

| 文件 | 说明 |
|---|---|
| `Scheduler.java` | 数据与调度核心：方案(JSON)解析、规则展开、下一次触发时间计算 |
| `ChimeReceiver.java` | 广播接收：播放铃声 + 排下一次闹钟，sys:/doc:/asset 三分支播放 |
| `MainActivity.java` | 主界面：方案列表、总开关、振动开关 |
| `ProfileActivity.java` | 方案编辑：规则、星期、铃声选择（三来源）、音量、试听 |
| `Ui.java` | 程序化 UI 工具（沉浸式状态栏等） |

## 构建

需要 Android SDK（build-tools 34 / platform android-33）与 JDK 17：

```sh
./build.sh            # 产出 HourlyChime.apk（需自备 debug.keystore）
```

或手动流程见 `build.sh` 内注释。关键点：

- `aapt2 link` 必须带 `--min-sdk-version 30 --target-sdk-version 33`（否则部分设备安装报 -26）
- 无布局 XML，资源仅一个启动器图标；`assets/` 随包打入

## 内置铃声

`assets/` 下随包打入 8 个内置铃声，文件名与应用内编号一一对应（`Ringtone N` → `ringN.*`，格式保持原始 mp3/ogg）：

| 文件 | 应用内名称 | 预置用途 |
|---|---|---|
| `ring1.mp3` | Ringtone 1 (上下班提醒铃) | 特铃-节点 |
| `ring2.mp3` | Ringtone 2 (工作日正点报时) | 工作日-整点 |
| `ring3.ogg` | Ringtone 3 (工作日半点报时) | 工作日-半点 |
| `ring4.ogg` | Ringtone 4 (休息日整点报时) | 特铃-周末 |
| `ring5.mp3` | Ringtone 5 (短叮) | — |
| `ring6.mp3` | Ringtone 6 (双音叮咚) | — |
| `ring7.mp3` | Ringtone 7 (门铃叮咚) | — |
| `ring8.mp3` | Ringtone 8 (家电叮) | — |

代码中铃声 key（`sing` / `bell` / `wind` / `chimes` / `d1`~`d4`，存于用户偏好）与文件名解耦，映射见 `ChimeReceiver.java`。

## 许可

源码按 MIT 许可发布（见 LICENSE）。铃声音频文件不在许可范围内。
