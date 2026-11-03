@echo off
cd /d C:\Users\Administrator\Downloads\emoney
echo [restart] launcher started %DATE% %TIME% old-pid=%~1 >> restart-sm.log
set OLD=%~1
set /a tries=0
:wait
tasklist /FI "PID eq %OLD%" /NH 2>nul | findstr /i "java.exe" >nul
if errorlevel 1 goto settle
set /a tries+=1
if %tries% geq 90 goto settle
timeout /t 4 /nobreak >nul
goto wait
:settle
echo [restart] old process gone after %tries% polls >> restart-sm.log
if exist mods\sunlitmarket-1.20.1-0.1.0.jar.new (
  echo [restart] installing staged jar %DATE% %TIME% >> restart-sm.log
  move /y mods\sunlitmarket-1.20.1-0.1.0.jar.new mods\sunlitmarket-1.20.1-0.1.0.jar >> restart-sm.log 2>&1
)
timeout /t 20 /nobreak >nul
echo [restart] launching %DATE% %TIME% >> restart-sm.log
java -Xms18G -Xmx18G -jar rosetta-1.20.1-ecf8d5ad-server.jar >> restart-sm.log 2>&1
echo [restart] java exited %DATE% %TIME% >> restart-sm.log
