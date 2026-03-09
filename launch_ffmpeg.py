import os
import subprocess
import sys

# 1. Capture the path name from MediaMTX (passed via $RTSP_PATH)
if len(sys.argv) < 2:
    sys.exit(1)

path_name = sys.argv[1]  # This will be the video ID (e.g. fZc-ZKz0p8U)

# Ensure this matches the BASE_DIR in server.py
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
STREAM_FILE = os.path.join(BASE_DIR, f"{path_name}.txt")
LOG_FILE = os.path.join(BASE_DIR, 'ffmpeg_log.txt')
FFMPEG_PATH = r'C:\Program Files\ffmpeg\bin\ffmpeg.exe'

def log(msg):
    with open(LOG_FILE, 'a') as f:
        f.write(f"[{path_name}] {msg}\n")

try:
    if not os.path.exists(STREAM_FILE):
        log(f"Error: Link file {STREAM_FILE} not found!")
        sys.exit(1)

    with open(STREAM_FILE, 'r') as f:
        direct_url = f.read().strip()

    cmd = [
        FFMPEG_PATH,
        '-re',
        '-i', direct_url,            
        '-c:v', 'mpeg4',
        '-vtag', 'xvid',
        '-s', '320x240',
        '-b:v', '180k',
        '-r', '15',
        '-pix_fmt', 'yuv420p',
        '-c:a', 'aac',
        '-ar', '32000',
        '-b:a', '32k',
        '-ac', '1',
        '-async', '1',
        '-f', 'rtsp',
        '-rtsp_transport', 'tcp',
        f'rtsp://127.0.0.1:8554/{path_name}'
    ]

    log(f"Starting stream for {path_name}")
    
    # We use run() so the script stays alive as long as FFmpeg is running
    subprocess.run(cmd)

    # Cleanup: Remove the txt file after the stream ends to save space
    if os.path.exists(STREAM_FILE):
        os.remove(STREAM_FILE)

except Exception as e:
    log(f"Launcher crashed: {str(e)}")
    sys.exit(1)