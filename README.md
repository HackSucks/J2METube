# J2METube
YouTube implementation for J2ME equipped devices with MIDP 2.1 and CLDC 1.1, and JSR 135 using a proxy server.

---

# Wha?? why??
I created this project because the only current YouTube implementation for J2ME devices is JTube, which has long since been abandoned by its developer and is practically dead.  
So I created J2METube.

---

# How it work?

You need a proxy server :<

You can self-host it on your IP currently, unless some maniac would offer to host it online themselves. I unfortunately don’t have the funds to make a proper online proxy server, so self-hosting on your own IP is the best we can do.

It uses an Invidious instance, specifically S60Tube:  
http://s60tube.io.vn

So far the app is very basic — ~~no thumbnails or anything yet.~~

1. Use the search bar.
2. Click the video you want.
3. Wait a bit.

On first startup the app will prompt for your proxy server.

Go to the **Releases** tab and download `serverpackage.zip`.  
Run:

- `mediamtx.exe`
- `server.py`

(Linux versions coming soon ;3)

It will print an IP. Put that IP in the app.

When clicking a video and it asks to open an `rtsp://...` link, press **Yes**.  
If everything worked, the media player will open and play the video!

---
# update watch :O


~~1.1.0 upcoming will have a slight UI redesign.~~
 
 ~~1.1.1~~ 1.1.2 (planned concept) will have ease of use functions to make it more intuitive and easy to use.
 
---

# Bugs?

There is an issue where **ffmpeg streams about ~10 seconds of video before handing the RTSP stream to the device**.

This means by the time you open the stream, the video may already be ~15 seconds in.

This should be fixed in a future update to the server package :3c.

---

boo !
