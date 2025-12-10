import subprocess
import re
import sys

# Base path on the device
BASE_PATH = "/storage/emulated/0/Android/data/com.flashpick.app.debug/files/Movies/FlashPick"

def run_adb(command):
    cmd = f"adb shell {command}"
    result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"Error executing: {cmd}")
        print(result.stderr)
    return result.stdout.strip()

def migrate():
    # 1. List files
    print("Scanning files...")
    output = run_adb(f"ls {BASE_PATH}")
    files = output.splitlines()
    
    video_moved = 0
    audio_moved = 0

    for filename in files:
        if not filename.startswith("clip_"):
            continue
            
        # Parse date: clip_20251130_...
        match = re.search(r"clip_(\d{4})(\d{2})(\d{2})_", filename)
        if not match:
            print(f"Skipping unknown format: {filename}")
            continue
            
        year, month, day = match.groups()
        date_folder = f"{year}-{month}-{day}"
        
        # Determine type
        if filename.endswith(".mp4") or filename.endswith(".jpg"):
            sub_folder = "video"
            video_moved += 1
        elif filename.endswith(".m4a"):
            sub_folder = "audio"
            audio_moved += 1
        else:
            continue
            
        # Construct paths
        target_dir = f"{BASE_PATH}/{date_folder}/{sub_folder}"
        source_file = f"{BASE_PATH}/{filename}"
        target_file = f"{target_dir}/{filename}"
        
        # 2. Create directory (mkdir -p is safe to run repeatedly)
        run_adb(f"mkdir -p {target_dir}")
        
        # 3. Move file
        print(f"Moving {filename} -> {date_folder}/{sub_folder}/")
        run_adb(f"mv {source_file} {target_file}")

    print("-" * 30)
    print(f"Migration Complete.")
    print(f"Videos moved: {video_moved}")
    print(f"Audios moved: {audio_moved}")

if __name__ == "__main__":
    migrate()

