# J2METube
YouTube implementation for J2ME equipped devices with MIDP 2.1 and CLDC 1.1, and JSR 135 using a proxy server.

or u culd say  

# YouTube for J2ME devices using a proxy server.

---

# Wha?? why??
I created this project because the only current YouTube implementation for J2ME devices is JTube, which has long since been abandoned by its developer and is practically dead.  
So I made J2METube !!

---

# How it work?

You need a proxy server :<

yous can self-host it on your IP currently, unless some guy with too much money to burn would offer to host it online themselves. I don’t have the funds to make a proper online proxy server, so self-hosting on your own IP is the best u can do :c.

It uses an Invidious instance, specifically S60Tube:  
http://s60tube.io.vn

So far the app is moderately good looking.

1. Use the search bar.
2. Click the video you want.
3. Wait a bit.
4. press "Watch"

On first startup the app will prompt for your proxy server.

Go to the **Releases** tab and download the latest SU/ServerUpdate package.
Run:

- `mediamtx.exe`
- `server.py`

(Linux versions coming soon ;3)

It will print an IP. Put that IP in the app.

When clicking "Watch" and it asks to open an `rtsp://...` link, press **Yes**.  
If everything worked, the media player will open and play the video!

---
# update watch :O


~~1.1.0 upcoming will have a slight UI redesign.~~
 
 ~~1.1.1~~ ~~1.1.2 will have ease of use functions to make it more intuitive and easy to use.~~

 1.1.3 will have...uh... really im not sure what to add anymore. ill think about it :3c.
 
---

# Bugs?

~~There is an issue where **ffmpeg streams about ~10 seconds of video before handing the RTSP stream to the device**.~~

~~This means by the time you open the stream, the video may already be ~15 seconds in.~~

~~This should be fixed in a future update to the server package :3c.~~ This bug should hopefully have been fixed in SU1.2/ServerUpdate 1.2.

---

boo !
