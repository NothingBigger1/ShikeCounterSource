param([Parameter(Mandatory=$true)][string]$AndroidJar)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$checkDir=Join-Path $projectRoot 'build/export-check'
New-Item -ItemType Directory -Force $checkDir | Out-Null
$sourceDir=Join-Path $projectRoot 'app/src/main/java/cn/shike/counter'
& "$env:JAVA_HOME/bin/javac.exe" -encoding UTF-8 -cp $AndroidJar -d $checkDir (Join-Path $sourceDir 'CounterStore.java') (Join-Path $sourceDir 'ExcelExporter.java') (Join-Path $PSScriptRoot 'ExportSmokeTest.java')
if($LASTEXITCODE -ne 0){throw 'Export smoke test compilation failed'}
& "$env:JAVA_HOME/bin/java.exe" -cp "$checkDir;$AndroidJar" cn.shike.counter.ExportSmokeTest (Join-Path $checkDir 'export-smoke.xlsx')
if($LASTEXITCODE -ne 0){throw 'Export smoke test failed'}
