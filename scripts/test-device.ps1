param([string]$Device = 'emulator-5554')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$jdkDir = Get-ChildItem -LiteralPath (Join-Path $projectRoot '.tools') -Directory | Where-Object { $_.Name -like 'jdk-17*' } | Select-Object -First 1
$env:JAVA_HOME = $jdkDir.FullName
$adb = Join-Path $projectRoot '.tools\android-sdk\platform-tools\adb.exe'
& (Join-Path $projectRoot 'gradlew.bat') -p $projectRoot assembleDebug assembleDebugAndroidTest --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Test build failed' }
& $adb -s $Device install -r (Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk')
if ($LASTEXITCODE -ne 0) { throw 'Debug APK installation failed. A release build must be uninstalled from this test device first because it uses a different signing key.' }
& $adb -s $Device install -r (Join-Path $projectRoot 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk')
if ($LASTEXITCODE -ne 0) { throw 'Test APK installation failed' }
& $adb -s $Device shell am force-stop com.screentranslate.app
$testOutput = & $adb -s $Device shell am instrument -w 'com.screentranslate.app.test/androidx.test.runner.AndroidJUnitRunner'
$testOutput | Write-Output
if (($testOutput -join "`n") -notmatch 'OK \(\d+ tests?\)') { throw 'Device test failed' }
$verificationDir = Join-Path $projectRoot 'verification'
New-Item -ItemType Directory -Path $verificationDir -Force | Out-Null
& $adb -s $Device pull /sdcard/Android/data/com.screentranslate.app/files $verificationDir
