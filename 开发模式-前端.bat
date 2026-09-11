@echo off
title 开发模式 - 前端 Vite 热更新 5173 端口
setlocal

set "ROOT=%~dp0"
cd /d "%ROOT%frontend"

echo =====================================================
echo    前端开发模式
echo.
echo    - 改动 frontend\src 里的文件，页面会自动刷新
echo    - 接口自动代理到 http://localhost:8080
echo    - 请先另开一个窗口双击「启动网站.bat」把后端起起来
echo =====================================================
echo.

where npm >nul 2>&1
if errorlevel 1 goto NONODE

if exist "node_modules" goto RUN
echo 首次运行，正在安装依赖...
set "NODE_ENV="
call npm install --include=dev --no-audit --no-fund
if errorlevel 1 goto FAIL

:RUN
set "NODE_ENV="
call npm run dev
exit /b 0

:NONODE
echo [错误] 没有找到 npm，请先安装 Node.js 18+：https://nodejs.org/
pause
exit /b 1

:FAIL
echo [错误] 依赖安装失败，请检查网络后重试。
pause
exit /b 1
