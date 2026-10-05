param([switch]$SkipChecks)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolRoot = Join-Path $projectRoot '.tools'
$jdkDir = Get-ChildItem -LiteralPath $toolRoot -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -like 'jdk-17*' } | Select-Object -First 1
if (!$jdkDir -or !(Test-Path -LiteralPath (Join-Path $projectRoot 'local.properties'))) {
    & (Join-Path $PSScriptRoot 'bootstrap.ps1')
    $jdkDir = Get-ChildItem -LiteralPath $toolRoot -Directory | Where-Object { $_.Name -like 'jdk-17*' } | Select-Object -First 1
}
$env:JAVA_HOME = $jdkDir.FullName
$env:ANDROID_HOME = Join-Path $toolRoot 'android-sdk'
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"
$signingDir = Join-Path $projectRoot '.signing'
New-Item -ItemType Directory -Path $signingDir -Force | Out-Null
$keystore = Join-Path $signingDir 'screen-translate.jks'
$passwordFile = Join-Path $signingDir 'password.txt'
if (!(Test-Path -LiteralPath $keystore)) {
    if (!(Test-Path -LiteralPath $passwordFile)) {
        $randomBytes = New-Object byte[] 32
        [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($randomBytes)
        [IO.File]::WriteAllText($passwordFile, [Convert]::ToBase64String($randomBytes))
    }
    & (Join-Path $env:JAVA_HOME 'bin\keytool.exe') -genkeypair -keystore $keystore -alias screen-translate -keyalg RSA -keysize 3072 -validity 10000 -storetype JKS -storepass:file $passwordFile -keypass:file $passwordFile -dname 'CN=Screen Translate, OU=Personal Sideload, O=Screen Translate'
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the local APK signing key' }
}
$gradleTasks = if ($SkipChecks) { @('lintRelease', 'assembleRelease') } else { @('testDebugUnitTest', 'lintDebug', 'lintRelease', 'assembleRelease', 'assembleDebug', 'assembleDebugAndroidTest') }
& (Join-Path $projectRoot 'gradlew.bat') -p $projectRoot @gradleTasks --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Android build failed' }
$outputDir = Join-Path $projectRoot 'dist'
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
$metadata = Get-Content -Raw -LiteralPath (Join-Path $projectRoot 'app\build\outputs\apk\release\output-metadata.json') | ConvertFrom-Json
$version = $metadata.elements[0].versionName
if ($version -notmatch '^\d+\.\d+\.\d+$') { throw 'Unexpected APK version' }
$artifactName = "glance-$version.apk"
$artifactPath = Join-Path $outputDir $artifactName
Copy-Item -LiteralPath (Join-Path $projectRoot 'app\build\outputs\apk\release\app-release.apk') -Destination $artifactPath
$apkHash = Get-FileHash -LiteralPath $artifactPath -Algorithm SHA256
[IO.File]::WriteAllText("$artifactPath.sha256", "$($apkHash.Hash.ToLowerInvariant())  $artifactName`n")
$apkHash | Select-Object Hash,Path
