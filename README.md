# J2METube
YouTube implementation for J2ME equipped devices with MIDP 2.1 and CLDC 1.1, and JSR 135 using a proxy server.

# Wha?? why??
I created this project becauae the only current youtube implementation for J2ME devices is JTube, which has long since been abandoned by its developer and is practically dead. so i created J2METube.

# how it work?

U need a proxy server :<. you can selfhost it on your ip currently, unless some maniac would offer to host it online themselves. i unfortunately dont have tthe fundz to make a proper online proxy server, so selfhosting on ur own ip is the best we can do. it uses an Invidious instance, speciffically S60Tube, which u can check out at http://s60tube.io.vn. so far the app is very basic, no thumbnails or anything, just a search bar, click the video u want and wait a bit. on first startup the app will prompt for ur proxy server. u can go to releases tab, and download serverpackage.zip and run the mediamtx.exe and server.py (linux versions coming soon ;3), itll print a IP. put that IP in the app. when clicking a video and it asks u to open a "rtsp://..." link, press "Yes". if everything went well it should open in the media player and u enjoy the video !

# bugs?
there is an issue where ffmpeg streams about ~10 seconds of video before handing the rtsp stream to the device. this means by the time u open the stream, the video may be around 15 seconds in of stream. this should be fixed in an update to the server package :3c.
























































































boo !

#
