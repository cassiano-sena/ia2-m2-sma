@echo off
setlocal EnableExtensions
cd /d "%~dp0"

REM --- Resolver comando Maven (wrapper do repo > PATH > MAVEN_HOME) ---
set "MVN_CMD="
if exist "%~dp0mvnw.cmd" (
    set "MVN_CMD=%~dp0mvnw.cmd"
    goto :maven_ok
)
where mvn >nul 2>&1
if %ERRORLEVEL%==0 (
    set "MVN_CMD=mvn"
    goto :maven_ok
)
if defined MAVEN_HOME (
    if exist "%MAVEN_HOME%\bin\mvn.cmd" (
        set "MVN_CMD=%MAVEN_HOME%\bin\mvn.cmd"
        goto :maven_ok
    )
)

echo [ERRO] Maven nao encontrado.
echo.
echo Opcoes:
echo   1. Use este repositorio com mvnw.cmd na raiz ^(recomendado^).
echo   2. Instale Maven e adicione ao PATH, ou defina MAVEN_HOME.
echo.
pause
exit /b 1

:maven_ok
REM Overrides locais (JAVA_HOME, etc.)
if exist "%~dp0run-gui.local.bat" call "%~dp0run-gui.local.bat"

REM --- JDK com javac (Maven precisa de JDK, nao JRE do PATH) ---
call :find_jdk
if errorlevel 1 (
    echo [ERRO] JDK 21+ nao encontrado ^(apenas JRE no PATH ou Java antigo^).
    echo.
    echo Instale um JDK 21+ ou copie run-gui.local.bat.example para run-gui.local.bat
    echo e defina JAVA_HOME, por exemplo:
    echo   set "JAVA_HOME=C:\Program Files\Java\jdk-21"
    echo.
    pause
    exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"
echo Usando Java: %JAVA_HOME%
"%JAVA_HOME%\bin\java.exe" -version 2>&1

echo.
echo Compilando...
call "%MVN_CMD%" -f pom.xml compile
if %ERRORLEVEL% neq 0 (
    echo [ERRO] Falha na compilacao.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo Iniciando SMA Generator Rental GUI...
call "%MVN_CMD%" -f pom.xml exec:java -Dexec.mainClass=br.univali.cc.ia2.m2.sma.gui.TrafficControlApp -Dexec.cleanupDaemonThreads=false
pause
exit /b 0

:find_jdk
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" exit /b 0

REM IntelliJ IDEA (JBR = JDK completo, Java 21+)
for /d %%i in ("%ProgramFiles%\JetBrains\IntelliJ IDEA*") do (
    if exist "%%i\jbr\bin\javac.exe" (
        set "JAVA_HOME=%%i\jbr"
        exit /b 0
    )
)

REM Instalacoes comuns de JDK 21+ no Windows
for %%p in (
    "%ProgramFiles%\Java\jdk-21*"
    "%ProgramFiles%\Java\jdk-22*"
    "%ProgramFiles%\Java\jdk-23*"
    "%ProgramFiles%\Java\jdk-24*"
    "%ProgramFiles%\Java\jdk-25*"
    "%ProgramFiles%\Eclipse Adoptium\jdk-21*"
    "%ProgramFiles%\Eclipse Adoptium\jdk-22*"
    "%ProgramFiles%\Microsoft\jdk-21*"
    "%ProgramFiles%\Microsoft\jdk-22*"
) do (
    for /d %%j in (%%p) do (
        if exist "%%j\bin\javac.exe" (
            set "JAVA_HOME=%%j"
            exit /b 0
        )
    )
)

exit /b 1
