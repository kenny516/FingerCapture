$ErrorActionPreference = 'Stop'

$jpackage = (Get-Command jpackage.exe -ErrorAction Stop).Source
$pomPath = Join-Path $PSScriptRoot 'pom.xml'
$project = ([xml](Get-Content -LiteralPath $pomPath -Raw)).project
$version = $project.version
$mainJar = "$($project.artifactId)-$version.jar"
$targetDirectory = Join-Path $PSScriptRoot 'target'
$archive = "FingerCapture-$version-portable.zip"

# Maven must already have the SDK JAR registered by build.ps1.
& mvn -B -ntp -f $pomPath verify
if ($LASTEXITCODE -ne 0) { throw 'Compilation ou tests en echec.' }

$workDirectory = Join-Path $targetDirectory ([guid]::NewGuid().ToString('N'))
$inputDirectory = Join-Path $workDirectory 'input'
$outputDirectory = Join-Path $workDirectory 'output'
New-Item -ItemType Directory -Path $inputDirectory | Out-Null
try {
    Copy-Item -LiteralPath (Join-Path $targetDirectory $mainJar) -Destination $inputDirectory
    Copy-Item -LiteralPath (Join-Path $targetDirectory 'lib') -Destination $inputDirectory -Recurse

    & $jpackage --type app-image --name FingerCapture --app-version $version `
        --input $inputDirectory --main-jar $mainJar --dest $outputDirectory
    if ($LASTEXITCODE -ne 0) { throw 'Creation du paquet Windows en echec.' }

    $appDirectory = Join-Path $outputDirectory 'FingerCapture'
    foreach ($file in @('README.md', 'example-form.html')) {
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot $file) -Destination $appDirectory
    }
    $stagedArchive = Join-Path $workDirectory $archive
    Compress-Archive -LiteralPath $appDirectory -DestinationPath $stagedArchive
    $destination = Join-Path $targetDirectory $archive
    Move-Item -LiteralPath $stagedArchive -Destination $destination -Force
    Write-Output "Paquet portable cree : $destination"
} finally {
    $resolvedWorkDirectory = (Resolve-Path -LiteralPath $workDirectory).Path
    if ((Split-Path -Parent $resolvedWorkDirectory) -eq $targetDirectory) {
        Remove-Item -LiteralPath $resolvedWorkDirectory -Recurse -Force
    }
}
