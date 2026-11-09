$ErrorActionPreference = 'Stop'
foreach ($scriptName in @('build.ps1', 'run.ps1')) {
    $syntaxTokens = $null
    $syntaxErrors = $null
    [System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot $scriptName), [ref]$syntaxTokens, [ref]$syntaxErrors) | Out-Null
    if ($syntaxErrors.Count -gt 0) { throw ($syntaxErrors | Out-String) }
}
$sdkJar = Join-Path $PSScriptRoot '../lib/ZKFingerReader.jar'
& mvn -B -ntp org.apache.maven.plugins:maven-install-plugin:3.1.3:install-file "-Dfile=$sdkJar" '-DgroupId=com.zkteco.sdk' '-DartifactId=finger-reader' '-Dversion=5.3.0.33' '-Dpackaging=jar' '-DgeneratePom=true'
if ($LASTEXITCODE -ne 0) { throw 'Installation du JAR SDK impossible.' }
& mvn -B -ntp -f (Join-Path $PSScriptRoot 'pom.xml') verify
if ($LASTEXITCODE -ne 0) { throw 'Compilation ou tests en echec.' }
& powershell.exe -NoProfile -File (Join-Path $PSScriptRoot 'src/test/powershell/LauncherTest.ps1')
if ($LASTEXITCODE -ne 0) { throw 'Tests du lanceur Windows en echec.' }
$targetDirectory = Join-Path $PSScriptRoot 'target'
$distributionDirectory = Join-Path $targetDirectory 'finger-enrollment-1.0.0'
New-Item -ItemType Directory -Path $distributionDirectory -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $targetDirectory 'finger-enrollment-1.0.0.jar') -Destination $distributionDirectory -Force
Copy-Item -LiteralPath (Join-Path $targetDirectory 'lib') -Destination $distributionDirectory -Recurse -Force
foreach ($file in @('run.ps1', 'README.md', 'example-form.html')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $file) -Destination $distributionDirectory -Force
}
Compress-Archive -Path (Join-Path $distributionDirectory '*') -DestinationPath (Join-Path $targetDirectory 'finger-enrollment-1.0.0.zip') -Force
Write-Output "Paquet cree : $targetDirectory\finger-enrollment-1.0.0.zip"
