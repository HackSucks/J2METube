from flask import Flask, request
import subprocess
import socket
import time
import threading
import requests
import yt_dlp

app = Flask(__name__)
current_process = None

FFMPEG_PATH = r'C:\Program Files\ffmpeg\bin\ffmpeg.exe'


MEDIAMTX_API = 'http://127.0.0.1:9997'
STREAM_PATH  = 'nokia'


READER_TIMEOUT = 30


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


def get_reader_count():
    """
    Ask the MediaMTX API how many readers are currently on /nokia.
    Returns 0 on any error (API not up yet, path not exists, etc.)
    """
    try:
        r = requests.get(f'{MEDIAMTX_API}/v3/paths/get/{STREAM_PATH}', timeout=2)
        if r.status_code == 200:
            data = r.json()
            
            return len(data.get('readers', []))
    except Exception:
        pass
    return 0


def resolve_url(vid):
    """Use yt-dlp to get a direct stream URL for the given video ID."""
    ydl_opts = {
        'format': 'best[height<=360]',
        'quiet': True,
        'no_warnings': True,
    }
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info(f'https://www.youtube.com/watch?v={vid}', download=False)
        return info['url']


def start_ffmpeg(direct_url):
    """Launch FFmpeg and return the process handle."""
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
        f'rtsp://127.0.0.1:8554/{STREAM_PATH}'
    ]
    print('Starting FFmpeg transcode...')
    return subprocess.Popen(cmd)


@app.route('/play')
def play_video():
    global current_process

    vid = request.args.get('v')
    if not vid:
        return 'Missing video ID', 400

    # Kill any previous stream
    if current_process:
        try:
            current_process.kill()
        except Exception:
            pass
        current_process = None

    print(f'Resolving YouTube ID: {vid}')
    try:
        direct_url = resolve_url(vid)
    except Exception as e:
        print(f'yt-dlp failed: {e}')
        direct_url = f'http://s60tube.io.vn/videoplayback?v={vid}'

    local_ip  = get_local_ip()
    rtsp_url  = f'rtsp://{local_ip}:8554/{STREAM_PATH}'

    def delayed_start():
        global current_process
        print(f'Waiting for Nokia to open {rtsp_url} ...')

        deadline = time.time() + READER_TIMEOUT
        while time.time() < deadline:
            if get_reader_count() > 0:
                print('Nokia connected — starting FFmpeg now.')
                current_process = start_ffmpeg(direct_url)
                return
            time.sleep(0.5)

        print(f'Timed out waiting for a reader after {READER_TIMEOUT}s.')

    t = threading.Thread(target=delayed_start, daemon=True)
    t.start()

    print(f'Handing link to Nokia: {rtsp_url}')
    return rtsp_url


if __name__ == '__main__':
    print(f'Server started. Point your Nokia to {get_local_ip()}:5000')
    app.run(host='0.0.0.0', port=5000)