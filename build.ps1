param(
    [string]$SdkPath = "",
    [string]$JdkPath = "",
    [string]$KeystorePath = "",
    [string]$KeystorePass = "android",
    [string]$KeyAlias = "androiddebugkey",
    [string]$KeyPass = "android",
    [string]$OutName = "android-credential-switcher-v1.0.0.apk"
)

$ErrorActionPreference = "Stop"
$proj = Split-Path -Parent $MyInvocation.MyCommand.Path

function Find-AndroidSdk([string]$ExplicitPath) {
    $candidates = New-Object System.Collections.Generic.List[string]
    if ($ExplicitPath) { $candidates.Add($ExplicitPath) }
    if ($env:ANDROID_HOME) { $candidates.Add($env:ANDROID_HOME) }
    if ($env:ANDROID_SDK_ROOT) { $candidates.Add($env:ANDROID_SDK_ROOT) }
    if ($env:LOCALAPPDATA) { $candidates.Add((Join-Path $env:LOCALAPPDATA "Android\Sdk")) }
    foreach ($drive in @("C", "D", "E", "F")) {
        $candidates.Add("${drive}:\Android\Sdk")
        $candidates.Add("${drive}:\Program Files (x86)\Android\android-sdk")
        $candidates.Add("${drive}:\Microsoft\Microsoft Visual Studio\Shared\Android\android-sdk")
    }
    foreach ($dir in $candidates) {
        if ($dir -and (Test-Path (Join-Path $dir "platforms")) -and (Test-Path (Join-Path $dir "build-tools"))) {
            return $dir
        }
    }
    throw "Android SDK not found. Set ANDROID_HOME / ANDROID_SDK_ROOT or pass -SdkPath."
}

function Find-JdkHome([string]$ExplicitPath, [string]$SdkRoot) {
    $candidates = New-Object System.Collections.Generic.List[string]
    if ($ExplicitPath) { $candidates.Add($ExplicitPath) }
    if ($env:JAVA_HOME) { $candidates.Add($env:JAVA_HOME) }

    $sharedAndroid = Split-Path $SdkRoot -Parent
    $openjdkSibling = Join-Path $sharedAndroid "openjdk"
    if (Test-Path $openjdkSibling) {
        $candidates.Add($openjdkSibling)
        Get-ChildItem $openjdkSibling -Directory -ErrorAction SilentlyContinue | ForEach-Object { $candidates.Add($_.FullName) }
    }

    foreach ($vendorDir in @(
        "$env:ProgramFiles\Microsoft",
        "$env:ProgramFiles\Eclipse Adoptium",
        "$env:ProgramFiles\Java"
    )) {
        if ($vendorDir -and (Test-Path $vendorDir)) {
            Get-ChildItem $vendorDir -Filter "jdk*" -Directory -ErrorAction SilentlyContinue |
                Sort-Object Name -Descending |
                ForEach-Object { $candidates.Add($_.FullName) }
        }
    }

    foreach ($c in $candidates) {
        if ($c -and (Test-Path (Join-Path $c "bin\javac.exe")) -and (Test-Path (Join-Path $c "bin\keytool.exe"))) {
            return $c
        }
    }

    $wc = where.exe javac 2>$null | Select-Object -First 1
    if ($wc) {
        $resolved = Split-Path (Split-Path $wc -Parent) -Parent
        if (Test-Path (Join-Path $resolved "bin\keytool.exe")) {
            return $resolved
        }
    }
    throw "JDK containing bin\javac.exe and bin\keytool.exe not found. Set JAVA_HOME or pass -JdkPath."
}

$sdk = Find-AndroidSdk $SdkPath
$jdkHome = Find-JdkHome $JdkPath $sdk

$platformDir = Get-ChildItem (Join-Path $sdk "platforms") -Directory |
    Where-Object { $_.Name -match '^android-(\d+)$' -and [int]$Matches[1] -ge 34 } |
    Sort-Object { [int]($_.Name -replace '^android-', '') } -Descending |
    Select-Object -First 1
if (-not $platformDir) {
    throw "No Android platform >= android-34 found under $sdk\platforms"
}
$androidJar = Join-Path $platformDir.FullName "android.jar"

$btDirItem = Get-ChildItem (Join-Path $sdk "build-tools") -Directory |
    Sort-Object { try { [version]($_.Name -replace '-.*$', '') } catch { [version]"0.0.0" } } -Descending |
    Select-Object -First 1
if (-not $btDirItem) {
    throw "No build-tools directory found under $sdk\build-tools"
}
$btDir = $btDirItem.FullName

$aapt2 = Join-Path $btDir "aapt2.exe"
$d8 = Join-Path $btDir "d8.bat"
$zipalign = Join-Path $btDir "zipalign.exe"
$apksigner = Join-Path $btDir "apksigner.bat"

$env:JAVA_HOME = $jdkHome
$env:PATH = (Join-Path $jdkHome "bin") + ";" + $env:PATH
$javac = Join-Path $jdkHome "bin\javac.exe"
$keytool = Join-Path $jdkHome "bin\keytool.exe"

Write-Host "Using JDK: $jdkHome"
Write-Host "Using Android JAR: $androidJar"
Write-Host "Using Build-Tools: $btDir"

$buildDir = Join-Path $proj "build"
$distDir = Join-Path $proj "dist"
if (Test-Path $buildDir) { Remove-Item $buildDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $buildDir, "$buildDir\gen", "$buildDir\classes", "$buildDir\dex", $distDir | Out-Null

# 1. Compile resources
& $aapt2 compile --dir (Join-Path $proj "res") -o (Join-Path $buildDir "res.zip")
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

# 2. Link resources -> base.apk + R.java
$baseApk = Join-Path $buildDir "base.apk"
& $aapt2 link -o $baseApk -I $androidJar --manifest (Join-Path $proj "AndroidManifest.xml") --java (Join-Path $buildDir "gen") --auto-add-overlay (Join-Path $buildDir "res.zip")
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

# 3. Compile Java sources
$javaFiles = @(
    Get-ChildItem (Join-Path $proj "src") -Filter "*.java" -Recurse | Select-Object -ExpandProperty FullName
    Get-ChildItem (Join-Path $buildDir "gen") -Filter "*.java" -Recurse | Select-Object -ExpandProperty FullName
)
& $javac -encoding UTF-8 --release 11 -classpath $androidJar -d (Join-Path $buildDir "classes") @javaFiles
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

# 4. Dex class files
$classFiles = Get-ChildItem (Join-Path $buildDir "classes") -Filter "*.class" -Recurse | Select-Object -ExpandProperty FullName
& $d8 --min-api 29 --lib $androidJar --output (Join-Path $buildDir "dex") @classFiles
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

# 5. Add classes.dex into base.apk preserving resources.arsc uncompressed
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$dexPath = Join-Path $buildDir "dex\classes.dex"
$zip = [System.IO.Compression.ZipFile]::Open($baseApk, [System.IO.Compression.ZipArchiveMode]::Update)
try {
    $existing = $zip.GetEntry("classes.dex")
    if ($existing) { $existing.Delete() }
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $dexPath, "classes.dex", [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
} finally {
    $zip.Dispose()
}

# 6. Zipalign
$alignedApk = Join-Path $buildDir "aligned.apk"
& $zipalign -p -f 4 $baseApk $alignedApk
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

# 7. Ensure persistent keystore (ignored by Git)
if (-not $KeystorePath) {
    $KeystorePath = Join-Path $proj "debug.keystore"
}
if (-not (Test-Path $KeystorePath)) {
    & $keytool -genkeypair -v -keystore $KeystorePath -storepass $KeystorePass -alias $KeyAlias -keypass $KeyPass -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Credential Switcher, OU=OpenSource, O=Community, L=Local, ST=Local, C=US"
    if ($LASTEXITCODE -ne 0) { throw "keytool failed" }
}

# 8. Sign APK
$outApk = Join-Path $distDir $OutName
& $apksigner sign --ks $KeystorePath --ks-pass "pass:$KeystorePass" --ks-key-alias $KeyAlias --key-pass "pass:$KeyPass" --out $outApk $alignedApk
if ($LASTEXITCODE -ne 0) { throw "apksigner sign failed" }

# 9. Verify APK
& $apksigner verify --print-certs $outApk
if ($LASTEXITCODE -ne 0) { throw "apksigner verify failed" }

$info = Get-Item $outApk
Write-Host "BUILD SUCCESS: $($info.FullName) ($($info.Length) bytes)"
