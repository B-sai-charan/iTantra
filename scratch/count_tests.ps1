$testFiles = Get-ChildItem -Path "app\build\test-results\testDebugUnitTest\*.xml"
$totalTests = 0
$totalFailures = 0
$totalSkipped = 0
foreach ($f in $testFiles) {
    [xml]$x = Get-Content $f.FullName
    $totalTests += [int]$x.testsuite.tests
    $totalFailures += [int]$x.testsuite.failures + [int]$x.testsuite.errors
    $totalSkipped += [int]$x.testsuite.skipped
}
Write-Output "Total Unit Tests: $totalTests, Failures: $totalFailures, Skipped: $totalSkipped"
