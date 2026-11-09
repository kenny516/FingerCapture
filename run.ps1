param(
    [string]$NativeDirectory,
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$CheckSdk,
    [switch]$Console
)
$ErrorActionPreference = 'Stop'
$javaProgram = if ($CheckSdk -or $Console) { 'java.exe' } else { 'javaw.exe' }
$javaExecutable = if ($JavaHome) { Join-Path $JavaHome "bin/$javaProgram" } else { $javaProgram }
$packageDirectory = if (Test-Path (Join-Path $PSScriptRoot 'finger-enrollment-1.0.0.jar')) { $PSScriptRoot } else { Join-Path $PSScriptRoot 'target' }
$jar = Join-Path $packageDirectory 'finger-enrollment-1.0.0.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Compilez avec build.ps1 ou utilisez le paquet distribue.' }
$javaArguments = @()
if ($NativeDirectory) {
    $nativePath = (Resolve-Path -LiteralPath $NativeDirectory).Path
    if (-not (Test-Path -LiteralPath (Join-Path $nativePath 'libzkfp.dll'))) { throw 'libzkfp.dll absente du dossier natif.' }
    $env:PATH = "$nativePath;$env:PATH"
    $javaArguments += "-Djava.library.path=$nativePath"
}
$javaArguments += @('-jar', $jar)
if ($CheckSdk) { $javaArguments += '--check-sdk' }
if ($CheckSdk -or $Console) {
    & $javaExecutable @javaArguments
    if ($LASTEXITCODE -ne 0) { throw "Arret de l'utilitaire avec le code $LASTEXITCODE." }
} else {
    # Start-Process joins arguments; quote each one for Windows, including trailing backslashes.
    $quotedArguments = ($javaArguments | ForEach-Object {
        '"' + (($_ -replace '(\\*)"', '$1$1\"') -replace '(\\+)$', '$1$1') + '"'
    }) -join ' '
    # javaw has no console. Keep the explicitly requested Swing application visible.
    Start-Process -FilePath $javaExecutable -ArgumentList $quotedArguments -WindowStyle Normal
}
