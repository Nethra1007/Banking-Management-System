@echo off
REM Compiles and starts the Java API on http://localhost:8080
cd /d "%~dp0"
if exist out rmdir /s /q out
mkdir out
dir /s /b src\*.java > sources.txt
javac -d out @sources.txt
if errorlevel 1 exit /b 1
del sources.txt
java -cp out com.bank.app.Main
