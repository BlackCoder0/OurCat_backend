param(
    [Parameter(Mandatory = $true)][string]$RuntimeJdk,
    [Parameter(Mandatory = $true)][string]$DemoApk,
    [string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
if (!$OutputDirectory) { $OutputDirectory = Join-Path $repo 'target/OurCat-demo-windows-x64' }
$output = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $output) { throw "Output already exists: $output. Choose a new directory." }
$jar = Join-Path $repo 'target/backend-1.0.0-SNAPSHOT.jar'
foreach ($file in @($jar, $DemoApk, (Join-Path $RuntimeJdk 'bin/jlink.exe'))) {
    if (!(Test-Path -LiteralPath $file)) { throw "Missing input: $file. Build Backend and Demo APK first." }
}
$javaRelease = [IO.File]::ReadAllText((Join-Path $RuntimeJdk 'release'))
if ($javaRelease -notmatch 'JAVA_VERSION="17\.' -or $javaRelease -notmatch 'OS_ARCH="x86_64"' -or $javaRelease -notmatch 'OS_NAME="Windows"') {
    throw 'Use a Windows x64 JDK 17 to build this package.'
}
$apkMetadata = Join-Path (Split-Path -Parent $DemoApk) 'output-metadata.json'
if (!(Test-Path -LiteralPath $apkMetadata) -or
    (([IO.File]::ReadAllText($apkMetadata) | ConvertFrom-Json).applicationId -ne 'com.ourcat.app.demo')) {
    throw 'Use the APK from assembleDemo, with its output-metadata.json alongside it.'
}
New-Item -ItemType Directory -Path $output | Out-Null
& (Join-Path $RuntimeJdk 'bin/jlink.exe') --add-modules java.se,jdk.crypto.ec,jdk.unsupported,jdk.charsets `
    --strip-debug --no-header-files --no-man-pages --compress=2 --output (Join-Path $output 'runtime')
if ($LASTEXITCODE -ne 0) { throw 'jlink failed.' }
Copy-Item -LiteralPath $jar -Destination (Join-Path $output 'backend.jar')
Copy-Item -LiteralPath $DemoApk -Destination (Join-Path $output 'OurCat-demo.apk')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'start.cmd'), (Join-Path $PSScriptRoot 'start.ps1') -Destination $output
Copy-Item -LiteralPath (Join-Path $repo 'LICENSE') -Destination $output
Copy-Item -LiteralPath (Join-Path $repo 'docs/local-experience.md') -Destination (Join-Path $output 'README.md')
$revision = (& git -C $repo rev-parse HEAD).Trim()
$androidRevision = (& git -C (Split-Path -Parent $DemoApk) rev-parse HEAD).Trim()
$manifest = [ordered]@{ backendCommit = $revision; builtAtUtc = [DateTime]::UtcNow.ToString('o');
    androidCommit = $androidRevision; backendHasLocalChanges = [bool](& git -C $repo status --porcelain);
    androidHasLocalChanges = [bool](& git -C (Split-Path -Parent $DemoApk) status --porcelain);
    javaRelease = $javaRelease;
    files = @(Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $output 'backend.jar'), (Join-Path $output 'OurCat-demo.apk') |
        ForEach-Object { @{ name = [IO.Path]::GetFileName($_.Path); sha256 = $_.Hash } }) }
[IO.File]::WriteAllText((Join-Path $output 'build-info.json'), ($manifest | ConvertTo-Json -Depth 5))
$archive = $output + '.zip'
if (Test-Path -LiteralPath $archive) { throw "Archive already exists: $archive" }
Compress-Archive -Path (Join-Path $output '*') -DestinationPath $archive
Get-FileHash -Algorithm SHA256 -LiteralPath $archive
