@echo off
title 哔哩哔哩仿站 - 启动中
setlocal

set "ROOT=%~dp0"
set "JAR=%ROOT%backend\target\bili-web.jar"

rem 把工作目录切到项目根目录，这样 uploads（头像、用户资料）会固定存在项目里，
rem 不会跑到「双击时所在的目录」去。
cd /d "%ROOT%"

rem 8080 被占用通常是上次没关干净，先自动停掉，避免新进程启动失败
netstat -ano | findstr ":8080" | findstr "LISTENING" >nul
if errorlevel 1 goto NOPORT
echo [提示] 检测到 8080 端口被占用（可能是上次没关干净），先自动关掉旧的...
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%tools\stop-server.ps1"
timeout /t 2 /nobreak >nul
echo.
:NOPORT

echo =====================================================
echo    哔哩哔哩仿站  Vue 3 + Spring Boot 3
echo =====================================================
echo.

if not exist "%JAR%" goto NOJAR

set "JAVA_EXE="
call :try "%JAVA_HOME%\bin\java.exe"
call :try "D:\ProgramData\jdk\jdk-17\bin\java.exe"
call :try "D:\ProgramData\jdk\jdk-21\bin\java.exe"
call :try "C:\Program Files\Java\jdk-21\bin\java.exe"
call :try "C:\Program Files\Java\jdk-17\bin\java.exe"
call :try "C:\Program Files\Java\latest\bin\java.exe"
for %%J in (java.exe) do call :try "%%~$PATH:J"

if not defined JAVA_EXE goto NOJAVA

echo [1/3] 使用的 Java 版本：
"%JAVA_EXE%" -version
echo.
echo [2/3] 正在启动服务，第一次启动大约 5~10 秒，请稍等...
echo.
echo       网站地址： http://localhost:8080
echo       关闭这个黑窗口 = 停止网站
echo.
echo [3/3] 启动日志（看到 Started BiliApplication 就是好了）：
echo -----------------------------------------------------

start "" /min cmd /c "timeout /t 9 /nobreak >nul & start http://localhost:8080"

"%JAVA_EXE%" -jar "%JAR%"

echo.
echo 服务已停止，再见~
pause
exit /b 0

:NOJAR
echo [错误] 没有找到后端程序：
echo        %JAR%
echo.
echo 请先双击运行「重新构建.bat」把项目编译一次。
echo.
pause
exit /b 1

:NOJAVA
echo [错误] 没有找到 Java 17 或更高版本。
echo.
echo 本项目用的是 Spring Boot 3，需要 Java 17 以上（Java 8 跑不了）。
echo 你电脑上已经装了 JDK 17，可以在本文件开头加一行指定它：
echo     set JAVA_HOME=D:\ProgramData\jdk\jdk-17
echo 保存后重新双击本文件即可。
echo.
pause
exit /b 1

:try
if "%~1"=="" exit /b
if defined JAVA_EXE exit /b
if not exist "%~1" exit /b
"%~1" -version > "%TEMP%\bili_java_version.txt" 2>&1
findstr /r /c:"version \"17\." /c:"version \"18\." /c:"version \"19\." /c:"version \"2[0-9]\." "%TEMP%\bili_java_version.txt" >nul
if not errorlevel 1 set "JAVA_EXE=%~1"
exit /b
