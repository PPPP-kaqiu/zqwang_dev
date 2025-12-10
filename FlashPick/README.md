# FlashPick 安卓应用 SOP

> 版本：2025-11-30  
> 维护人：FlashPick 核心团队

本文档用于指导在物理设备上安装、配置、使用与排障 FlashPick 安卓应用的标准流程。

---

## 1. 环境要求
- Android 8.0（API 26）及以上设备（已在 Android 14 上验证）。
- 设备已开启 USB 调试。
- 开发机可使用 `adb`。
- 应用包名：`com.flashpick.app.debug`（当前调试包位于 `app/build/outputs/apk/debug/app-debug.apk`）。

---

## 2. 安装 / 升级步骤
1. **强制停止旧进程（升级前必做）**
   ```bash
   adb shell am force-stop com.flashpick.app.debug
   ```
2. **安装最新调试包**
   ```bash
   adb install -r /Users/moonshot/Desktop/zqwang/ReadingMyself/FlashPick/flashpick-android/app/build/outputs/apk/debug/app-debug.apk
   ```
3. 在设备上手动启动 FlashPick。

---

## 3. 首次权限配置
主界面（Compose）会显示各权限状态卡片，请按下列顺序全部点亮为“已开启”：

1. **录音权限**：点击“申请录音权限”，在系统弹窗选择允许。
2. **悬浮窗权限**：点击“开启悬浮窗权限”，跳转系统设置后开启。
3. **无障碍服务**：点击“打开无障碍设置”，启用 `FlashPick App Monitor`。若系统提示“此服务出现故障”，请在设置中关闭再开启，或返回主界面点击检查。
4. **屏幕录制权限**：点击“开启屏幕录制权限”，在 MediaProjection 提示中选择 FlashPick，授权后按钮会变灰表示有效。

> 提示：若重启设备或被系统回收，下一次双击桌宠会自动弹出 MediaProjection 授权。

---

## 4. 白名单管理
1. 主界面点击 **“管理白名单应用”**。
2. 等待应用列表加载（依赖 `<queries>` + 协程）。
3. 通过搜索框按名称或包名过滤。
4. 点击条目即可选中/取消，结果持久化到 `SharedPreferences`。
5. 点击“完成”保存配置。

仅白名单内的应用会触发录制与桌宠显示。

---

## 5. 运行机制
### 5.1 哨兵服务（AccessibilityService）
- 监听 `TYPE_WINDOW_STATE_CHANGED`。
- 忽略自身包名事件，避免进入/退出循环。
- 500 ms 去抖，降低同 App 内跳转的重启频率。
- 行为：
  - **进入白名单 App**：启动 `OverlayService` 与 `ScreenRecorderService`（开始向环形缓冲写入）。
  - **离开白名单 App**：停止桌宠并暂停录制；若正在保存片段，会立即冲洗。

### 5.2 桌宠悬浮窗服务
- 前台服务类型 `dataSync`。
- 紫色可拖拽圆球，手势：
  - **单击**：展开/收起菜单（含 `2s`、`10s`、`录音`）。
  - **双击**：设置回放窗口为前 5 秒 + 后 5 秒并立即保存。
  - **长按“录音”**：开始/结束语音笔记（需已有最新视频）。
- 菜单默认 3 秒无操作自动隐藏；录音中保持展开。

### 5.3 录屏服务
- 权限授予后常驻前台。
- 组成：
  - `MediaProjection` + `VirtualDisplay`
  - `MediaCodec`（H.264）编码 → 内存 `CircularBuffer`
  - 系统音频（playback）采集；麦克风仅在语音笔记时启用。
- 默认窗口：前 5 秒 + 后 5 秒，可通过菜单按钮调整。
- 视频保存路径：`Context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)/FlashPick`。
- `VideoGalleryActivity` 会列出目录下全部 MP4 与 `_voice.m4a` 音频，可直接调起系统播放器播放。

---

## 6. 使用流程
1. 确认所有权限为“已开启”，白名单已选定目标 App。
2. 退出主界面，打开白名单 App（如小红书）。  
   - 期望：紫色桌宠滑入，通知栏提示悬浮服务运行。
3. 等待环形缓冲填充数据。
4. **保存视频片段**：
   - 双击桌宠或点击菜单 `2s/10s`。  
   - 会弹出 `Triggered` Toast，并提示回放窗口。  
   - 视频保存后可在“查看录制内容”中播放。  
   - 若在后 5 秒内退出白名单，会立即结束并落盘。
5. **录制语音笔记**：
   - 前提：当前会话至少成功保存过一次视频。  
   - 单击桌宠展开菜单 → 长按“录音”。  
   - 接受时会震动并提示“正在录音… 松开即可保存”。  
   - 松手即停止并提示“录音已保存”，文件命名为 `<视频名>_voice.m4a`。
6. **查看记录**：主界面点击“查看录制内容”，视频与语音条目会按时间倒序显示；点击 MP4 会走系统视频播放器，点击 `_voice.m4a` 会走音频播放器。

---

## 7. 常见问题排查
| 现象 | 排查步骤 |
| --- | --- |
| 桌宠未出现 | 确认当前 App 在白名单、无障碍仍启用；运行 `adb logcat -s AppMonitorService` 查看。 |
| 双击无反应 | 检查主界面“屏幕录制权限”是否为“已开启”；若被系统回收，下一次双击会再次弹出授权。 |
| 长按录音无麦克风图标 | 必须先保存最新视频；否则日志有 `Voice note start ignored: no clip base`。 |
| 视频无法播放/无声 | 确认录制服务未被杀；查看 `ScreenRecorderService` 日志有无编码错误。 |
| 录屏/无障碍频繁失效 | 某些 ROM 会清理后台，建议将 FlashPick 加入系统白名单并保持主应用常驻。 |
| 播放器进度显示 59h | 旧版本文件时间戳未归零所致；升级后重新录制即可，旧文件需重新封装或忽略进度条。 |

抓取关键日志命令：
```bash
adb logcat -d | grep -E "OverlayService|ScreenRecorderService" -C 3
```

---

## 8. 变更记录
- **2025-11-30**：新增桌宠长按日志、语音笔记前置校验、录音期间菜单保持显示，并完成本 SOP。

> 若后续手势逻辑、权限流程或录制窗口有更新，请同步修订本文档。

