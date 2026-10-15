param(
    [Parameter(Mandatory=$true)][string]$SigningDirectory,
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [Parameter(Mandatory=$true)][string]$AndroidHome
)
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$credentialFile=Join-Path $SigningDirectory 'upload-credential.xml'
$keyFile=Join-Path $SigningDirectory 'upload.p12'
if(!(Test-Path -LiteralPath $credentialFile) -or !(Test-Path -LiteralPath $keyFile)){throw 'Upload key or DPAPI credential not found'}
$credential=Import-Clixml -LiteralPath $credentialFile
$oldJava=$env:JAVA_HOME; $oldAndroid=$env:ANDROID_HOME
try {
    $env:JAVA_HOME=$JavaHome; $env:ANDROID_HOME=$AndroidHome
    Push-Location $repo
    try {
        & .\gradlew.bat --no-daemon testDebugUnitTest lintRelease bundleRelease -PcreatorTools=false
        if($LASTEXITCODE -ne 0){throw 'Release build failed'}
        $env:ATTENDANCE_UPLOAD_PASSWORD=$credential.GetNetworkCredential().Password
        $inputBundle=Join-Path $repo 'app\build\outputs\bundle\release\app-release.aab'
        $signedBundle=Join-Path $repo 'app\build\outputs\bundle\release\attendance-play-signed.aab'
        & (Join-Path $JavaHome 'bin\jarsigner.exe') -keystore $keyFile -storetype PKCS12 -storepass:env ATTENDANCE_UPLOAD_PASSWORD -sigalg SHA256withRSA -digestalg SHA-256 -signedjar $signedBundle $inputBundle $credential.UserName
        if($LASTEXITCODE -ne 0){throw 'Bundle signing failed'}
        & (Join-Path $JavaHome 'bin\jarsigner.exe') -verify $signedBundle
        if($LASTEXITCODE -ne 0){throw 'Signature verification failed'}
        Get-FileHash -LiteralPath $signedBundle -Algorithm SHA256
    } finally {Pop-Location}
} finally {
    Remove-Item Env:ATTENDANCE_UPLOAD_PASSWORD -ErrorAction SilentlyContinue
    $env:JAVA_HOME=$oldJava; $env:ANDROID_HOME=$oldAndroid; $credential=$null
}

