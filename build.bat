@echo off

set SDK_PATH=C:\Java_ME_platform_SDK_3.0.5
set JAVA6_PATH=C:\Program Files\Java\jdk1.6.0_45\bin
set LWUIT_JAR=%SDK_PATH%\lib\LWUIT.jar

:: Core classpath
set CP=%SDK_PATH%\lib\midp_2.1.jar;%SDK_PATH%\lib\cldc_1.1.jar;%SDK_PATH%\lib\jsr135_1.2.jar;%SDK_PATH%\lib\jsr75_1.0.jar;%LWUIT_JAR%;%SDK_PATH%\lib\jsr184_1.1.jar;%SDK_PATH%\lib\jsr226_1.0.jar

echo.
echo ===== CLEANING =====
if exist classes rd /s /q classes
if exist lwuit_classes rd /s /q lwuit_classes
if exist preverified rd /s /q preverified

mkdir classes
mkdir lwuit_classes
mkdir preverified

echo.
echo ===== [1/5] Compiling MIDlet =====
"%JAVA6_PATH%\javac.exe" -source 1.3 -target 1.3 -cp "%CP%" -d classes J2METube.java
if errorlevel 1 ( echo COMPILE FAILED & pause & exit /b 1 )

echo.
echo ===== [2/5] Extracting LWUIT =====
cd lwuit_classes
"%JAVA6_PATH%\jar.exe" xf "%LWUIT_JAR%"
if exist META-INF rd /s /q META-INF
cd ..
del /s lwuit_classes\*SVG*.class
echo.
echo ===== [3/5] Merging classes =====
xcopy classes preverified /E /I /Y >nul
xcopy lwuit_classes preverified /E /I /Y >nul

echo.
echo ===== [4/5] Preverifying =====
"%SDK_PATH%\bin\preverify.exe" ^
-classpath "%SDK_PATH%\lib\midp_2.1.jar;%SDK_PATH%\lib\cldc_1.1.jar;%SDK_PATH%\lib\jsr135_1.2.jar;%SDK_PATH%\lib\jsr75_1.0.jar;%SDK_PATH%\lib\jsr184_1.1.jar;%SDK_PATH%\lib\jsr226_1.0.jar" ^
-d preverified preverified
if errorlevel 1 ( echo PREVERIFY FAILED & pause & exit /b 1 )

echo.
echo ===== [5/5] Packaging JAR =====
cd preverified
"%JAVA6_PATH%\jar.exe" cvfm ../J2METube.jar ../manifest.mf .
cd ..

echo.
echo ===== Updating JAD =====
for %%I in (J2METube.jar) do set size=%%~zI
powershell -Command "(gc J2METube.jad) -replace 'MIDlet-Jar-Size:.*', 'MIDlet-Jar-Size: %size%' | Out-File -encoding ascii J2METube.jad"

echo.
echo BUILD COMPLETE
echo Copy J2METube.jar and J2METube.jad to the phone.
pause
