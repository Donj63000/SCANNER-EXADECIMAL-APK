@echo off
if "%OS%"=="Windows_NT" setlocal
set "APP_HOME=%~dp0"
if "%APP_HOME%"=="" set "APP_HOME=."
for %%i in ("%APP_HOME%") do set "APP_HOME=%%~fi"
set "DISTRIBUTION_URL=https://services.gradle.org/distributions/gradle-8.10.2-bin.zip"
set "DIST_NAME=gradle-8.10.2"
set "WRAPPER_DIR=%APP_HOME%\gradle\wrapper"
set "DIST_DIR=%WRAPPER_DIR%\%DIST_NAME%"
set "GRADLE_CMD=%DIST_DIR%\bin\gradle.bat"
if not exist "%GRADLE_CMD%" (
    set "TMP=%TEMP%\gradle-wrapper-%RANDOM%%RANDOM%"
    powershell -NoProfile -Command ^
        "$ErrorActionPreference='Stop';" ^
        "$url='%DISTRIBUTION_URL%';" ^
        "$tmp='%TMP%';" ^
        "[System.IO.Directory]::CreateDirectory($tmp) | Out-Null;" ^
        "$zip=Join-Path $tmp 'gradle.zip';" ^
        "Invoke-WebRequest -Uri $url -OutFile $zip;" ^
        "$dest='%WRAPPER_DIR%';" ^
        "$dist='%DIST_NAME%';" ^
        "Add-Type -AssemblyName System.IO.Compression.FileSystem;" ^
        "$target = Join-Path $dest $dist;" ^
        "if (Test-Path $target) { Remove-Item -Recurse -Force $target; }" ^
        "[System.IO.Directory]::CreateDirectory($dest) | Out-Null;" ^
        "$archive=[System.IO.Compression.ZipFile]::OpenRead($zip);" ^
        "foreach ($entry in $archive.Entries) { if ($entry.FullName.StartsWith($dist + '/')) { $relative = $entry.FullName.Substring($dist.Length + 1); if ($relative.Length -eq 0) { continue } $targetPath = Join-Path $target $relative; if ($entry.FullName.EndsWith('/')) { [System.IO.Directory]::CreateDirectory($targetPath) | Out-Null; } else { [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($targetPath)) | Out-Null; $entry.ExtractToFile($targetPath, $true); } } }" ^
        "$archive.Dispose();" ^
        "Remove-Item -Recurse -Force $tmp;"
    if errorlevel 1 goto fail
)
call "%GRADLE_CMD%" %*
set EXIT_CODE=%ERRORLEVEL%
if not "%EXIT_CODE%"=="0" goto fail
if "%OS%"=="Windows_NT" endlocal
exit /b 0
:fail
if not defined EXIT_CODE set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if "%OS%"=="Windows_NT" endlocal
if not ""=="%GRADLE_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%
