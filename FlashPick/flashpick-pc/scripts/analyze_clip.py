import os
import sys
import json
import base64
import cv2
import requests
import argparse
import numpy as np

# --- Configuration ---
API_KEY = "1337868c-2baf-4aa1-a684-c3a885ba802c"
MODEL_ENDPOINT_ID = "doubao-seed-1-6-vision-250815"
BASE_URL = "https://ark.cn-beijing.volces.com/api/v3/chat/completions"

# --- Prompts ---
SYSTEM_PROMPT = """
分析这一组手机应用截图（它们是连续视频的关键帧）。
请综合画面内容，判断内容属性（是“娱乐休闲”还是“知识学术”），并以严格有效的 JSON 格式输出，包含以下键：
- "app_name": 根据界面元素识别应用名称（如“抖音”、“微信”、“小红书”、“Arxiv”、“VSCode”等）。如果不确定，请使用“未知应用”。
- "title": 一个简短、吸引人的标题（最多 15 个字），概括整个过程。
- "summary": 内容总结。请根据内容类型采取不同策略：
    * 如果是【娱乐/生活/购物】类（如短视频、聊天、商品浏览）：保持简短（50字以内），描述用户在做什么。
    * 如果是【知识/学术/技术/工作】类（如论文、代码、长文章、图表）：请提供详细的深度解读（150字以内），提取核心论点、技术原理、代码功能或关键数据，帮助用户快速回忆知识点。
- "keywords": 提取 3-5 个关键标签（如 "SwiftUI", "食谱", "旅行攻略"）。
- "entities": 提取画面中的关键实体名称（如书名、电影名、地名、代码库名）。
"""

def encode_image(image_path):
    with open(image_path, "rb") as image_file:
        return base64.b64encode(image_file.read()).decode('utf-8')

def extract_keyframes(video_path, output_dir, count=5, focus_time_ms=None):
    """
    Extracts keyframes from video using logic similar to Android app.
    Uniform sampling + Focused sampling (if focus_time_ms provided).
    """
    if not os.path.exists(output_dir):
        os.makedirs(output_dir)

    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        print(f"Error opening video file: {video_path}")
        return []

    fps = cap.get(cv2.CAP_PROP_FPS)
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    duration_ms = (total_frames / fps) * 1000 if fps > 0 else 0

    if duration_ms == 0:
        return []

    timestamps_ms = []

    # 1. Uniform Sampling
    step = duration_ms / (count + 1)
    for i in range(1, count + 1):
        timestamps_ms.append(i * step)

    # 2. Focused Sampling
    if focus_time_ms is not None and focus_time_ms > 0:
        timestamps_ms.append(max(0, focus_time_ms - 500))
        timestamps_ms.append(min(duration_ms, focus_time_ms))
        timestamps_ms.append(min(duration_ms, focus_time_ms + 500))

    # 3. Deduplicate and Sort
    timestamps_ms.sort()
    unique_timestamps = []
    for ts in timestamps_ms:
        if not unique_timestamps or (ts - unique_timestamps[-1] > 300):
            unique_timestamps.append(ts)

    extracted_files = []
    
    video_basename = os.path.splitext(os.path.basename(video_path))[0]

    for i, ts_ms in enumerate(unique_timestamps):
        cap.set(cv2.CAP_PROP_POS_MSEC, ts_ms)
        ret, frame = cap.read()
        if ret:
            # Resize for efficiency (Android does compress 60 quality, we resize to 720p height max maybe?)
            # Android MediaMetadataRetriever usually gives full res.
            # We'll save as jpg with quality 60
            
            # Simple resize if too big (optional, but VLM has token limits)
            h, w = frame.shape[:2]
            if h > 1080:
                scale = 1080 / h
                frame = cv2.resize(frame, (int(w * scale), 1080))

            out_path = os.path.join(output_dir, f"{video_basename}_frame_{i}.jpg")
            cv2.imwrite(out_path, frame, [int(cv2.IMWRITE_JPEG_QUALITY), 60])
            extracted_files.append(out_path)

    cap.release()
    return extracted_files

def analyze_video(video_path, transcript=None, trigger_time_ms=None):
    print(f"Analyzing {video_path}...")
    
    # 1. Extract Keyframes
    images_dir = os.path.join(os.path.dirname(os.path.dirname(video_path)), "images_extracted") # ../images_extracted
    # Or just use the global raw_data/images path
    # Let's use specific temp folder or raw_data/images
    images_dir = os.path.join(os.path.dirname(video_path), "frames")
    
    keyframes = extract_keyframes(video_path, images_dir, count=5, focus_time_ms=trigger_time_ms)
    print(f"Extracted {len(keyframes)} keyframes.")

    if not keyframes:
        print("No keyframes extracted.")
        return

    # 2. Construct Request
    content_parts = []
    
    # Prompt
    final_prompt = SYSTEM_PROMPT
    if transcript:
        final_prompt += f"\n此外，用户在录制时说了以下语音笔记，请将其作为**最重要的上下文**来生成标题和总结，这代表了用户的核心意图：\n\"{transcript}\""
    
    content_parts.append({"type": "text", "text": final_prompt})

    # Images
    for img_path in keyframes:
        base64_img = encode_image(img_path)
        content_parts.append({
            "type": "image_url",
            "image_url": {"url": f"data:image/jpeg;base64,{base64_img}"}
        })

    payload = {
        "model": MODEL_ENDPOINT_ID,
        "messages": [
            {
                "role": "user",
                "content": content_parts
            }
        ]
    }

    # 3. Call API
    headers = {
        "Authorization": f"Bearer {API_KEY}",
        "Content-Type": "application/json"
    }

    print("Calling VLM API...")
    try:
        response = requests.post(BASE_URL, headers=headers, json=payload, timeout=60)
        response.raise_for_status()
        
        result = response.json()
        content = result['choices'][0]['message']['content']
        
        # 4. Parse JSON
        # Content might be wrapped in markdown code block ```json ... ```
        if "```json" in content:
            content = content.split("```json")[1].split("```")[0].strip()
        elif "```" in content:
            content = content.split("```")[1].strip()

        print("\n=== Analysis Result ===")
        try:
            parsed = json.loads(content)
            print(json.dumps(parsed, indent=2, ensure_ascii=False))
            
            # Save to file
            output_path = video_path + ".analysis.json"
            with open(output_path, "w", encoding='utf-8') as f:
                json.dump(parsed, f, indent=2, ensure_ascii=False)
            print(f"\nResult saved to {output_path}")

        except json.JSONDecodeError:
            print("Raw Content (JSON Parse Failed):")
            print(content)

    except Exception as e:
        print(f"API Call Failed: {e}")
        if 'response' in locals():
            print(response.text)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Analyze video clip using FlashPick AI logic.")
    parser.add_argument("video_path", help="Path to the mp4 video file.")
    parser.add_argument("--transcript", help="Optional audio transcript text.", default=None)
    parser.add_argument("--trigger", type=int, help="Optional trigger time in ms (focus point).", default=None)

    args = parser.parse_args()
    
    if not os.path.exists(args.video_path):
        print("Video file not found.")
        sys.exit(1)

    analyze_video(args.video_path, args.transcript, args.trigger)

