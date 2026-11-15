param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Goals
)

$ErrorActionPreference = "Stop"
$projDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $projDir

if (-not $Goals -or $Goals.Count -eq 0) {
    $Goals = @("verify")
}

$jdkHome = $env:JAVA_HOME
if (-not $jdkHome -or -not (Test-Path "$jdkHome\bin\javac.exe")) {
    if (Test-Path "C:\Program Files\Eclipse Adoptium\jdk-17.0.11.9-hotspot\bin\javac.exe") {
        $jdkHome = "C:\Program Files\Eclipse Adoptium\jdk-17.0.11.9-hotspot"
    } elseif (Test-Path "C:\Program Files\Java\jdk-21.0.12\bin\javac.exe") {
        $jdkHome = "C:\Program Files\Java\jdk-21.0.12"
    }
}

$javac = if ($jdkHome) { "$jdkHome\bin\javac.exe" } else { "javac" }
$java  = if ($jdkHome) { "$jdkHome\bin\java.exe" }  else { "java" }
$jar   = if ($jdkHome) { "$jdkHome\bin\jar.exe" }   else { "jar" }

$cacheBase = "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1"
$jars = Get-ChildItem -Path $cacheBase -Recurse -File -Filter "*.jar" -ErrorAction SilentlyContinue | Where-Object {
    $_.Name -notmatch "natives-" -and
    $_.Name -notmatch "1\.13\.1" -and
    $_.Name -notmatch "1\.13\.2-SNAPSHOT" -and
    $_.Name -notmatch "1\.2\.25" -and
    $_.Name -notmatch "-sources\.jar$" -and
    $_.Name -notmatch "-javadoc\.jar$"
} | Select-Object -ExpandProperty FullName
$cp = ($jars -join ";")

if ($Goals -contains "clean") {
    if (Test-Path "$projDir\target") {
        Remove-Item -Recurse -Force "$projDir\target"
    }
}

New-Item -ItemType Directory -Force -Path "$projDir\target\classes", "$projDir\target\test-classes" | Out-Null

if (Test-Path "$projDir\src\main\resources") {
    Copy-Item -Path "$projDir\src\main\resources\*" -Destination "$projDir\target\classes" -Recurse -Force
}

Write-Host "[INFO] Compiling main Java sources (Java 11 target)..."
$mainSources = Get-ChildItem -Path "$projDir\src\main\java" -Recurse -Filter "*.java" | Select-Object -ExpandProperty FullName
if ($mainSources) {
    & $javac --release 11 -encoding UTF-8 -cp $cp -d "$projDir\target\classes" $mainSources
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
    Write-Host "[INFO] Compiled $($mainSources.Count) main source files."
}

if (Test-Path "$projDir\src\test\resources") {
    Copy-Item -Path "$projDir\src\test\resources\*" -Destination "$projDir\target\test-classes" -Recurse -Force
}

$testSources = Get-ChildItem -Path "$projDir\src\test\java" -Recurse -Filter "*.java" | Select-Object -ExpandProperty FullName
if ($testSources) {
    Write-Host "[INFO] Compiling test Java sources..."
    & $javac --release 11 -encoding UTF-8 -cp "$projDir\target\classes;$cp" -d "$projDir\target\test-classes" $testSources
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
    Write-Host "[INFO] Compiled $($testSources.Count) test source files."
}

if ($Goals -contains "test" -or $Goals -contains "verify" -or $Goals -contains "package") {
    $testClasses = Get-ChildItem -Path "$projDir\src\test\java" -Recurse -Filter "*Test.java" | Where-Object {
        $_.Name -ne "CraftWisePluginTest.java"
    } | ForEach-Object {
        $rel = $_.FullName.Substring("$projDir\src\test\java\".Length)
        $rel.Replace("\", ".").Replace(".java", "")
    }
    if ($testClasses) {
        Write-Host "[INFO] Running unit tests: $($testClasses -join ', ')"
        & $java -cp "$projDir\target\test-classes;$projDir\target\classes;$cp" org.junit.runner.JUnitCore $testClasses
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }
    }
}

if ($Goals -contains "package" -or $Goals -contains "verify") {
    Write-Host "[INFO] Packaging craftwise-1.0.0.jar..."
    & $jar cf "$projDir\target\craftwise-1.0.0.jar" -C "$projDir\target\classes" .
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
    Copy-Item -Path "$projDir\target\craftwise-1.0.0.jar" -Destination "$projDir\craftwise-1.0.0.jar" -Force
}

Write-Host "[INFO] BUILD SUCCESS"
exit 0
