import os
import subprocess
import sys

PACKAGE_NAME = "com.flashpick.app.debug"
REMOTE_MEDIA_PATH = f"/sdcard/Android/data/{PACKAGE_NAME}/files/Movies/FlashPick"
LOCAL_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOCAL_RAW_DATA = os.path.join(LOCAL_ROOT, "raw_data")
LOCAL_AI_OUTPUT = os.path.join(LOCAL_ROOT, "ai_output")

def adb_pull(remote_path, local_path):
    print(f"Pulling {remote_path} to {local_path}...")
    subprocess.run(["adb", "pull", remote_path, local_path], check=False)

def main():
    print(f"Syncing FlashPick data to {LOCAL_ROOT}...")
    
    if not os.path.exists(LOCAL_RAW_DATA):
        os.makedirs(LOCAL_RAW_DATA)
        
    # 1. Pull Media Files (Recursive)
    # The remote path contains folders like 2024-12-01/video/, 2024-12-01/audio/
    # adb pull will recreate this structure inside LOCAL_RAW_DATA/FlashPick
    # We might want to clean it up, but simple pull is fine.
    
    # Check if remote path exists
    check_cmd = ["adb", "shell", "ls", REMOTE_MEDIA_PATH]
    if subprocess.run(check_cmd, capture_output=True).returncode != 0:
        print(f"Remote path {REMOTE_MEDIA_PATH} not found. Have you recorded anything?")
    else:
        adb_pull(REMOTE_MEDIA_PATH, LOCAL_RAW_DATA)

    # 2. Pull Database (Requires run-as for non-rooted debug builds)
    print("Attempting to pull database...")
    local_db_path = os.path.join(LOCAL_AI_OUTPUT, "flashpick_db")
    
    # Using run-as to copy to tmp then pull (more reliable than exec-out sometimes)
    # But exec-out cat is standard for one-file pull
    cmd = f"adb exec-out run-as {PACKAGE_NAME} cat /data/data/{PACKAGE_NAME}/databases/flashpick_db > \"{local_db_path}\""
    ret = os.system(cmd)
    
    if ret == 0 and os.path.exists(local_db_path) and os.path.getsize(local_db_path) > 0:
        print(f"Database pulled successfully to {local_db_path}.")
    else:
        print("Failed to pull database. Ensure device is connected and app is debuggable.")

if __name__ == "__main__":
    main()

