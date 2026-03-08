@echo off
set SDK_PATH=C:\Java_ME_platform_SDK_3.0.5
:: Update this to your actual Java 6 bin path
set JAVA6_PATH=C:\Program Files\Java\jdk1.6.0_45\bin

echo [1/4] Compiling...
"%JAVA6_PATH%\javac.exe" -source 1.3 -target 1.3 -cp "%SDK_PATH%\lib\midp_2.1.jar;%SDK_PATH%\lib\cldc_1.1.jar;%SDK_PATH%\lib\jsr135_1.2.jar" J2METube.java

echo [2/4] Preverifying...
if exist preverified rd /s /q preverified
mkdir preverified
:: Pointing to the specific classes helps avoid the "Wrong Name" loading error
"%SDK_PATH%\bin\preverify.exe" -classpath "%SDK_PATH%\lib\midp_2.1.jar;%SDK_PATH%\lib\cldc_1.1.jar;%SDK_PATH%\lib\jsr135_1.2.jar;%SDK_PATH%\lib\jsr75_1.0.jar" -d preverified J2METube J2METube$1 J2METube$2 J2METube$2$1 J2METube$2$2 J2METube$3

echo [3/4] Packaging JAR...
:: Move into the preverified folder to package ONLY the verified classes
cd preverified
"%JAVA6_PATH%\jar.exe" cvfm ../J2METube.jar ../manifest.mf *.class
cd ..

echo [4/4] Finalizing JAD...
for %%I in (J2METube.jar) do set size=%%~zI
powershell -Command "(gc J2METube.jad) -replace 'MIDlet-Jar-Size:.*', 'MIDlet-Jar-Size: %size%' | Out-File -encoding ascii J2METube.jad"

echo Done! Copy J2METube.jar and J2METube.jad to your Asha 303.
