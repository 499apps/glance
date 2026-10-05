$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolRoot = Join-Path $projectRoot '.tools'
New-Item -ItemType Directory -Path $toolRoot -Force | Out-Null

function Get-Archive($url, $destination, $hash, $algorithm) {
    if (!(Test-Path -LiteralPath $destination)) {
        & curl.exe -fL --retry 3 --silent --show-error $url -o $destination
        if ($LASTEXITCODE -ne 0) { throw "Download failed: $url" }
    }
    if ($hash -and (Get-FileHash -LiteralPath $destination -Algorithm $algorithm).Hash.ToLowerInvariant() -ne $hash.ToLowerInvariant()) {
        throw "Checksum mismatch: $destination"
    }
}

$jdkDir = Get-ChildItem -LiteralPath $toolRoot -Directory | Where-Object { $_.Name -like 'jdk-17*' } | Select-Object -First 1
if (!$jdkDir) {
    $assets = Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'
    $package = $assets[0].binary.package
    $jdkZip = Join-Path $toolRoot 'jdk.zip'
    Write-Output 'Downloading JDK 17...'
    Get-Archive $package.link $jdkZip $package.checksum 'SHA256'
    & tar.exe -xf $jdkZip -C $toolRoot
    if ($LASTEXITCODE -ne 0) { throw 'JDK extraction failed' }
    $jdkDir = Get-ChildItem -LiteralPath $toolRoot -Directory | Where-Object { $_.Name -like 'jdk-17*' } | Select-Object -First 1
}
$env:JAVA_HOME = $jdkDir.FullName
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$sdkRoot = Join-Path $toolRoot 'android-sdk'
$sdkManager = Join-Path $sdkRoot 'cmdline-tools\19.0\bin\sdkmanager.bat'
if (!(Test-Path -LiteralPath $sdkManager)) {
    [xml]$repository = (Invoke-WebRequest -UseBasicParsing 'https://dl.google.com/android/repository/repository2-1.xml').Content
    $archive = $repository.SelectSingleNode('//*[local-name()="remotePackage" and @path="cmdline-tools;19.0"]/*[local-name()="archives"]/*[local-name()="archive"][*[local-name()="host-os"]="windows"]/*[local-name()="complete"]')
    $sdkZip = Join-Path $toolRoot 'sdk-19.zip'
    Write-Output 'Downloading Android build tools...'
    Get-Archive ('https://dl.google.com/android/repository/' + $archive.url) $sdkZip $archive.checksum 'SHA1'
    $stage = Join-Path $toolRoot 'sdk-19-stage'
    New-Item -ItemType Directory -Path $stage -Force | Out-Null
    & tar.exe -xf $sdkZip -C $stage
    if ($LASTEXITCODE -ne 0) { throw 'SDK extraction failed' }
    New-Item -ItemType Directory -Path (Join-Path $sdkRoot 'cmdline-tools') -Force | Out-Null
    Move-Item -LiteralPath (Join-Path $stage 'cmdline-tools') -Destination (Join-Path $sdkRoot 'cmdline-tools\19.0')
}
# Install official SDK packages in this project only.
1..100 | ForEach-Object { 'y' } | & $sdkManager --sdk_root=$sdkRoot --licenses | Out-Null
& $sdkManager --sdk_root=$sdkRoot 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0'
if ($LASTEXITCODE -ne 0) { throw 'SDK package installation failed' }
$escapedSdk = $sdkRoot.Replace('\', '/').Replace(':', '\:')
Set-Content -LiteralPath (Join-Path $projectRoot 'local.properties') -Value "sdk.dir=$escapedSdk" -Encoding ascii

$gradleDir = Join-Path $toolRoot 'gradle-8.11.1'
if (!(Test-Path -LiteralPath $gradleDir)) {
    $gradleZip = Join-Path $toolRoot 'gradle.zip'
    $hashContent = (Invoke-WebRequest -UseBasicParsing 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip.sha256').Content
    $gradleHash = if ($hashContent -is [byte[]]) { [System.Text.Encoding]::UTF8.GetString($hashContent).Trim() } else { $hashContent.Trim() }
    Write-Output 'Downloading Gradle...'
    Get-Archive 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip' $gradleZip $gradleHash 'SHA256'
    & tar.exe -xf $gradleZip -C $toolRoot
    if ($LASTEXITCODE -ne 0) { throw 'Gradle extraction failed' }
}
& (Join-Path $gradleDir 'bin\gradle.bat') -p $projectRoot wrapper --gradle-version 8.11.1 --distribution-type bin
if ($LASTEXITCODE -ne 0) { throw 'Gradle wrapper generation failed' }
Write-Output 'Build tools are ready.'
