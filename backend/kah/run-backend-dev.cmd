@echo off
cd /d D:\kawang\backend\kah
mvn.cmd -Dmaven.repo.local=D:\kawang\.m2 spring-boot:run 1>backend-run.out.log 2>backend-run.err.log
