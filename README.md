# HourlyChime 整点报时

一个极简的 Android 整点报时 / 定时提醒应用（`com.len.hourlychime`）。无任何依赖库、无布局 XML，纯程序化 UI，单 APK 约 13KB。为替代已停止维护的 com.caynax.hourlychime 而自研。

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

## 铃声音频（不入库）

出于版权考虑，本仓库不包含 `assets/` 下的铃声音频文件（原为旧应用提取及 Freesound 素材）。自行放置以下任一/全部文件后即可构建出带内置铃声的包：

```
assets/sing.mp3   assets/bell.mp3   assets/wind.ogg   assets/chimes.ogg
assets/d1.mp3     assets/d2.mp3     assets/d3.mp3     assets/d4.mp3
```

缺失对应文件时该铃声选项播放会失败，但 App 其余功能（系统音效 / 自选文件铃声）不受影响。

## 许可

源码按 MIT 许可发布（见 LICENSE）。铃声音频文件不在许可范围内。
