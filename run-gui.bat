@echo off
set MAVEN_HOME=C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.2\plugins\maven\lib\maven3
set JAVA_HOME=C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.2\jbr
set PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%

cd /d "%~dp0"

echo Compilando...
call "%MAVEN_HOME%\bin\mvn.cmd" -f pom.xml compile

echo.
echo Iniciando SMA Generator Rental GUI...
call "%MAVEN_HOME%\bin\mvn.cmd" -f pom.xml exec:java -Dexec.mainClass=br.univali.cc.ia2.m2.sma.gui.TrafficControlApp
pause