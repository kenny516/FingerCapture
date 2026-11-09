# Run after Maven packaging. Uses a fake Java executable: no capture, GUI or keyboard input.
$ErrorActionPreference = 'Stop'
$utilityDirectory = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$targetDirectory = Join-Path $utilityDirectory 'target'
$fixtureDirectory = Join-Path $targetDirectory 'launcher mode test fixture'
$javaBin = Join-Path $fixtureDirectory 'jdk/bin'
$nativeDirectory = Join-Path $fixtureDirectory 'native dlls'
New-Item -ItemType Directory -Path $javaBin, $nativeDirectory -Force | Out-Null
Set-Content -LiteralPath (Join-Path $nativeDirectory 'libzkfp.dll') -Value ''
$probeSource = @'
using System;
using System.IO;
using System.Linq;
class LauncherProbe {
    public static void Main(string[] args) {
        string exe = System.Diagnostics.Process.GetCurrentProcess().MainModule.FileName;
        File.WriteAllLines(Environment.GetEnvironmentVariable("ENROLLMENT_LAUNCH_PROBE"), new[] { exe }.Concat(args));
    }
}
'@
foreach ($program in @('javaw.exe', 'java.exe')) {
    $fakeJava = Join-Path $javaBin $program
    if (-not (Test-Path -LiteralPath $fakeJava)) {
        $outputType = if ($program -eq 'javaw.exe') { 'WindowsApplication' } else { 'ConsoleApplication' }
        $programSource = if ($program -eq 'java.exe') { $probeSource.Replace('class LauncherProbe', 'class ConsoleLauncherProbe') } else { $probeSource }
        Add-Type -TypeDefinition $programSource -OutputAssembly $fakeJava -OutputType $outputType
    }
}
$probeOutput = Join-Path $fixtureDirectory ([Guid]::NewGuid().ToString() + '.txt')
$env:ENROLLMENT_LAUNCH_PROBE = $probeOutput
$launcher = Join-Path $utilityDirectory 'run.ps1'
try {
    & $launcher -JavaHome (Join-Path $fixtureDirectory 'jdk') -NativeDirectory $nativeDirectory
    for ($attempt = 0; $attempt -lt 50 -and -not (Test-Path -LiteralPath $probeOutput); $attempt++) { Start-Sleep -Milliseconds 100 }
    if (-not (Test-Path -LiteralPath $probeOutput)) { throw 'Background Java did not start.' }
    $received = @(Get-Content -LiteralPath $probeOutput)
    if ($received.Count -ne 4 -or (Split-Path $received[0] -Leaf) -ne 'javaw.exe' -or $received[1] -ne "-Djava.library.path=$nativeDirectory" -or $received[2] -ne '-jar' -or -not (Test-Path -LiteralPath $received[3])) { throw "Invalid background Java invocation: $received" }
    Write-Output 'PASS: background javaw launcher preserves arguments with spaces.'

    & $launcher -Console -JavaHome (Join-Path $fixtureDirectory 'jdk') -NativeDirectory $nativeDirectory
    $received = @(Get-Content -LiteralPath $probeOutput)
    if ($received.Count -ne 4 -or (Split-Path $received[0] -Leaf) -ne 'java.exe') { throw 'Console mode did not use java.exe.' }
    Write-Output 'PASS: console mode uses java.exe and waits for completion.'

    & $launcher -CheckSdk -JavaHome (Join-Path $fixtureDirectory 'jdk') -NativeDirectory $nativeDirectory
    $received = @(Get-Content -LiteralPath $probeOutput)
    if ($received.Count -ne 5 -or (Split-Path $received[0] -Leaf) -ne 'java.exe' -or $received[4] -ne '--check-sdk') { throw 'Diagnostic mode arguments are invalid.' }
    Write-Output 'PASS: diagnostic mode uses java.exe with --check-sdk.'
} finally {
    Remove-Item Env:ENROLLMENT_LAUNCH_PROBE -ErrorAction SilentlyContinue
}
