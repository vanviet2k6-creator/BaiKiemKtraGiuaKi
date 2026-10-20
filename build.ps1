param([switch]$Verify)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    # Resolve a valid JDK for this process without changing the user's system settings.
    if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
        $taskJava = Get-Command java.exe -All -ErrorAction SilentlyContinue |
            Where-Object { $_.Source -match '\\bin\\java.exe$' } | Select-Object -First 1
        if (-not $taskJava) { throw 'Install JDK 21, or select a valid Gradle JDK in Android Studio.' }
        $env:JAVA_HOME = Split-Path (Split-Path $taskJava.Source -Parent) -Parent
    }
    if (-not (Test-Path -LiteralPath 'local.properties')) {
        $taskSdk = $env:ANDROID_HOME
        if (-not $taskSdk) { $taskSdk = $env:ANDROID_SDK_ROOT }
        if (-not $taskSdk) { $taskSdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
        if (-not (Test-Path -LiteralPath $taskSdk)) { throw 'Install Android SDK 35 using Android Studio.' }
        $taskProperty = 'sdk.dir=' + $taskSdk.Replace('\','/').Replace(':','\:')
        [System.IO.File]::WriteAllText((Join-Path $PSScriptRoot 'local.properties'), $taskProperty, (New-Object System.Text.UTF8Encoding($false)))
    }
    $taskTargets = @('assembleDebug')
    if ($Verify) { $taskTargets += @('assembleDebugAndroidTest', 'lintDebug') }
    & .\gradlew.bat --console=plain @taskTargets
    if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed. See the error output above.' }
}
finally { Pop-Location }
