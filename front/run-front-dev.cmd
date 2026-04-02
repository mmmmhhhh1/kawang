@echo off
cd /d D:\kawang\front
npm.cmd run dev -- --host 127.0.0.1 --port 5173 1>front-run.out.log 2>front-run.err.log
