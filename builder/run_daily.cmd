@echo off
REM Daily feed refresh: parse new archive PDFs, write Drive documents, publish the feed.
REM Scheduled by Windows Task Scheduler (see README). Safe to run by hand.
setlocal
cd /d "%~dp0"
set LOG=%~dp0daily.log
echo ==== %date% %time% ==== >> "%LOG%"
python build_feed.py >> "%LOG%" 2>&1
if errorlevel 1 (
  echo builder failed with %errorlevel% >> "%LOG%"
  exit /b 1
)
cd /d "%~dp0.."
git add -A docs >> "%LOG%" 2>&1
git diff --cached --quiet && (echo no feed changes >> "%LOG%" & exit /b 0)
git -c user.name="feed-bot" -c user.email="feed-bot@localhost" commit -q -m "feed: %date%" >> "%LOG%" 2>&1
git push -q origin main >> "%LOG%" 2>&1
echo pushed >> "%LOG%"
