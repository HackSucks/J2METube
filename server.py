from flask import Flask, request
import socket
import yt_dlp
import os
import datetime

app = Flask(__name__)


BASE_DIR = os.path.dirname(os.path.realpath(__file__)) 
DEBUG_LOG = os.path.join(BASE_DIR, 'server_debug.log') 
STREAM_FILE = os.path.join(BASE_DIR, 'current_stream.txt')

def log_debug(message):
    timestamp = datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    with open(DEBUG_LOG, 'a') as f:
        f.write(f"[{timestamp}] {message}\n")
    print(f"DEBUG: {message}")

def get_local_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(('10.255.255.255', 1))
        IP = s.getsockname()[0]
    except:
        IP = '127.0.0.1'
    finally:
        s.close()
    return IP

def resolve_url(vid):
    ydl_opts = {
        'format': 'best[height<=360]',
        'quiet': True,
        'no_warnings': True,
    }
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info(f'https://www.youtube.com/watch?v={vid}', download=False)
        return info['url']

@app.route('/play')
def play_video():
    vid = request.args.get('v')
    if not vid:
        return 'Missing video ID', 400

    # Unique file for this specific video ID
    stream_file = os.path.join(BASE_DIR, f"{vid}.txt")

    log_debug(f"Request for ID: {vid}")
    
    try:
        # Resolve and write if file doesn't exist
        if not os.path.exists(stream_file):
            direct_url = resolve_url(vid)
            with open(stream_file, 'w') as f:
                f.write(direct_url)
            log_debug(f"Created link file: {vid}.txt")
    except Exception as e:
        log_debug(f"Error: {e}")
        return "Error resolving video", 500

    local_ip = get_local_ip()
    # Path is now the video ID, not 'nokia'
    rtsp_url = f'rtsp://{local_ip}:8554/{vid}'
    
    return rtsp_url

if __name__ == '__main__':
    log_debug("Flask Multi-User Server Starting...")
    app.run(host='0.0.0.0', port=5000)
