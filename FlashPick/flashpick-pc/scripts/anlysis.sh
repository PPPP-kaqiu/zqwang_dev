# 基础用法
python3 flashpick-pc/scripts/analyze_clip.py /Users/moonshot/Desktop/zqwang/ReadingMyself/FlashPick/flashpick-pc/raw_data/FlashPick/2025-12-01/video/clip_20251201_134116.mp4

# 带语音笔记上下文
python3 flashpick-pc/scripts/analyze_clip.py <视频文件路径> --transcript "我当时在查阅关于 Transformer 的论文"

# 指定触发时间点 (毫秒) - 用于模拟“双击保存”时的焦点帧逻辑
python3 flashpick-pc/scripts/analyze_clip.py <视频文件路径> --trigger 5000