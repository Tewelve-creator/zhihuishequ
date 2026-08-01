@echo off
chcp 65001 >nul
setlocal
REM 启动本地 MinIO（无需 Docker）
REM 下载：https://dl.min.io/server/minio/release/windows-amd64/minio.exe
REM 放到本目录 tools\minio.exe，或把 minio.exe 加入 PATH

set "ROOT=%~dp0.."
set "DATA=%ROOT%\minio-data"
set "BIN=%ROOT%\tools\minio.exe"

if not exist "%DATA%" mkdir "%DATA%"

where minio >nul 2>&1
if %ERRORLEVEL%==0 (
  echo 使用 PATH 中的 minio
  minio server "%DATA%" --address ":9000" --console-address ":9001"
  goto :eof
)

if exist "%BIN%" (
  echo 使用 %BIN%
  "%BIN%" server "%DATA%" --address ":9000" --console-address ":9001"
  goto :eof
)

echo [错误] 未找到 minio.exe
echo 请下载 https://dl.min.io/server/minio/release/windows-amd64/minio.exe
echo 放到：%ROOT%\tools\minio.exe
echo 或安装后把 minio 加入 PATH，再重新运行本脚本。
echo.
echo 也可用 Docker：docker compose up -d minio
exit /b 1
