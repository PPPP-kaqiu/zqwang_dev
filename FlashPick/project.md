FlashPick (Android) - 产品与技术开发全案
1. 项目愿景 (Vision)
FlashPick 是一款基于 Android 平台的智能屏幕记忆代理。它旨在解决全天候录屏带来的存储焦虑和隐私问题。 核心逻辑： 仅在特定应用（如小红书、知乎）中自动唤醒，利用 内存环形缓冲 (Ring Buffer) 技术，只保留用户主动触发时刻的前后片段（如前2分钟+后30秒），并通过 AI 辅助整理灵感。

2. 产品需求文档 (PRD)
2.1 核心功能模块
模块	功能描述	优先级
哨兵系统 (Monitor)	利用无障碍服务监听前台 App，匹配用户设定的白名单。	P0
环形录制 (Ring Buffer)	后台静默录制屏幕流到内存（RAM），不写硬盘，循环覆盖旧数据。	P0
桌宠交互 (Overlay)	悬浮窗显示录制状态。支持点击/双击/拖拽。	P0
时光切片 (Capture)	用户触发后，将内存数据 + 未来30秒数据写入本地 MP4 文件。	P0
隐私保护 (Privacy)	检测到敏感页面（FLAG_SECURE）自动暂停录制并模糊画面。	P1
数据回顾 (Review)	本地相册展示，支持调用 OCR 提取文字。	P1

导出到 Google 表格

2.2 用户交互流程 (User Flow)
初始化: 用户授权（悬浮窗权限、无障碍权限、录屏权限、通知权限）。

待机: App 后台运行，无 UI。

唤醒: 用户打开“小红书”。-> 桌宠悬浮球从边缘滑入。-> 录制服务在内存中启动。

捕捉: 用户看到精彩内容 -> 双击悬浮球。

反馈: 悬浮球变色/震动。

动作: 保存过去 2 分钟 + 继续录制 30 秒 -> 生成视频文件。

结束: 用户返回桌面 -> 桌宠消失，内存缓冲区清空。

3. 技术架构设计 (Technical Architecture)
3.1 技术栈 (Tech Stack)
语言: Kotlin

最低版本: Android 8.0 (API 26)

UI 框架: Jetpack Compose (用于主界面), Android View (用于 WindowManager 悬浮窗)

核心组件:

AccessibilityService: 监听前台包名。

MediaProjection: 屏幕画面捕获。

MediaCodec: H.265 硬编码。

Service (Foreground): 后台保活。

Room Database: 存储记录元数据。

3.2 核心模块实现细节
A. 哨兵模块 (App Monitor)
实现: 继承 AccessibilityService。

逻辑: 监听 AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED。

去抖动: 设置 500ms 阈值，防止在同一 App 内页面跳转导致频繁重启录制。

B. 环形缓冲录制 (The Ring Buffer Engine) - 最关键部分
不使用 MediaRecorder (因为它直接写文件)。

使用 MediaCodec (Async Mode):

配置编码器为 H.265 (video/hevc), 码率动态调整 (1Mbps - 4Mbps)。

MediaCodec 输出 ByteBuffer (编码后的 H.264/H.265 NAL units)。

I-Frame (关键帧) 管理: 必须强制编码器定期生成关键帧 (Key Frame Interval = 1s)，否则截取的时间段无法播放。

内存队列:

创建一个 LinkedList<VideoPacket>。

VideoPacket 包含: ByteBuffer 数据, presentationTimeUs, isKeyFrame。

覆盖逻辑: 每次添加新包时，检查队列总时长是否超过 2 分钟。如果超过，移除头部数据，直到遇到下一个 Key Frame 为止 (保证文件开头是关键帧)。

C. 落盘合成 (Muxing)
当触发保存时：

标记当前时间戳 T 
trigger
​
 。

启动一个 MediaMuxer。

将内存队列中的数据写入 Muxer。

继续接收 MediaCodec 的新数据直通写入 Muxer，持续 30 秒。

关闭 Muxer，释放内存队列。

D. 悬浮窗 (Overlay Service)
使用 WindowManager.LayoutParams:

type: TYPE_APPLICATION_OVERLAY

flags: FLAG_NOT_FOCUSABLE (不抢占输入法) | FLAG_LAYOUT_IN_SCREEN

拖拽实现: 监听 OnTouchListener，更新 LayoutParams 的 x, y 坐标。

4. Cursor 辅助开发路线 (Step-by-Step for Cursor)
请按照以下顺序向 Cursor 发出指令，不要一次性生成所有代码。

Phase 1: 基础脚手架与权限
Prompt: 创建一个 Kotlin Android 项目。配置 ViewBinding 和 Jetpack Compose。在 AndroidManifest.xml 中添加 FOREGROUND_SERVICE, SYSTEM_ALERT_WINDOW, RECORD_AUDIO 权限。创建一个 PermissionManager 类来处理运行时权限请求。

Phase 2: 哨兵服务 (Accessibility)
Prompt: 实现一个继承自 AccessibilityService 的类 AppMonitorService。

在 onServiceConnected 中配置监听 TYPE_WINDOW_STATE_CHANGED。

实现一个单例 WhiteListManager，包含 {"com.xingin.xhs", "tv.danmaku.bili"}。

当检测到包名匹配时，打印日志 "Entered Target App"；离开时打印 "Exited"。

注意处理 AndroidManifest 中的 service 声明和 accessibility_service_config.xml。

Phase 3: 悬浮窗 UI (Overlay)
Prompt: 创建一个 OverlayService (Foreground Service)。

在 onCreate 中使用 WindowManager 添加一个简单的圆形 View (红色小球)。

实现 View 的 OnTouchListener，支持手指拖拽移动位置。

实现双击事件检测：双击时 Toast 显示 "Triggered"。

将 OverlayService 与 AppMonitorService 联动：进入白名单 App 启动 Overlay，退出则停止。

Phase 4: 屏幕录制核心 (MediaProjection)
Prompt: 创建 ScreenRecorderService。

请求 MediaProjectionManager 权限（需要 Activity 配合 startActivityForResult）。

初始化 MediaCodec (video/avc, 1080p, 2Mbps)。

创建一个 VirtualDisplay 将屏幕内容输入给 Surface。

关键: 实现 MediaCodec.Callback。目前只需将 onOutputBufferAvailable 获取到的 ByteBuffer info 打印日志，证明获取到了数据流。

Phase 5: 环形缓冲逻辑 (Ring Buffer)
Prompt: 实现内存环形缓冲逻辑。

定义数据类 VideoFrame(val data: ByteArray, val info: BufferInfo)。

创建一个 CircularBuffer 类，维护一个 ArrayDeque<VideoFrame>。

实现 add(frame) 方法：添加新帧，如果总时长超过 2 分钟，从头部移除帧（注意：移除时要确保下一个头部是 I帧/KeyFrame）。

将 Phase 4 中的数据流接入此 Buffer。

Phase 6: 视频合成与保存 (Muxer)
Prompt: 实现保存逻辑。

当悬浮窗双击时，调用 saveVideo()。

saveVideo 初始化 MediaMuxer，路径为 Context.getExternalFilesDir。

将 Buffer 中的所有旧数据写入 Muxer。

设置一个标志位 isSaving = true，让后续 30 秒的新数据直接写入 Muxer。

30秒后，stop() Muxer 并提示保存成功。

5. 给 Cursor 的核心 Prompt (复制即用)
开始开发前，请先将以下这段话发给 Cursor，让它理解项目背景：

Markdown

I am building an Android application called "FlashPick".
Core Concept: An intelligent screen recorder that only records when specific "Whitelisted Apps" (e.g., Instagram, TikTok) are in the foreground.
Key Mechanism: "Ring Buffer Recording". It continuously records the screen into a RAM buffer (keeping only the last 2 minutes). It does NOT write to disk continuously.
User Action: When the user sees something interesting, they double-tap a floating overlay button (Desktop Pet).
System Action: The app dumps the last 2 minutes from RAM + records the next 30 seconds -> saves as a single MP4 file.

I need you to act as my Senior Android Architect. We will build this step-by-step using Kotlin.
We will use:
- AccessibilityService for detecting foreground apps.
- MediaProjection & MediaCodec for low-level recording.
- WindowManager for the floating UI.
- A custom Ring Buffer implementation for managing H.265 byte streams in memory.

Please wait for my specific instructions for each phase. Do not generate the whole app at once.
6. 开发中的注意事项 (Tips)
I-Frame (关键帧) 是噩梦:

MediaCodec 生成的关键帧可能很久才有一个。

技巧: 在配置 MediaFormat 时，设置 KEY_I_FRAME_INTERVAL 为 1 (即每秒一个关键帧)。虽然会增加一点体积，但能保证切割视频时不会花屏。

Android 14 权限:

如果是 Android 14，MediaProjection 的前台服务类型需要显式声明为 mediaProjection。

内存泄漏:

录制服务涉及大量 Byte 数组，务必小心 OOM。确保移除队列时显式释放引用。

无障碍服务死掉:

有些国产 ROM 会杀无障碍服务。建议在主页加一个 "检查服务状态" 的按钮，如果服务死了，引导用户去重新开启。