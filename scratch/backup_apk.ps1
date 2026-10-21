$origApk = 'c:\Users\saich\Downloads\SIH\PS26173\app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $origApk)) {
    Write-Error "Original APK not found at $origApk"
    exit 1
}

$origItem = Get-Item $origApk
$origSize = $origItem.Length
$origTimestamp = $origItem.LastWriteTime.ToString("yyyy-MM-dd HH:mm:ss")
$origHash = (Get-FileHash -Path $origApk -Algorithm SHA256).Hash

$backupDir = 'c:\Users\saich\Downloads\SIH\PS26173\scratch\backup'
if (-not (Test-Path $backupDir)) {
    New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
}

$backupPath = Join-Path $backupDir 'iTantra-PRE-UIUX-CHANGE-BACKUP.apk'
Copy-Item -Path $origApk -Destination $backupPath -Force

$backupItem = Get-Item $backupPath
$backupSize = $backupItem.Length
$backupTimestamp = $backupItem.LastWriteTime.ToString("yyyy-MM-dd HH:mm:ss")
$backupHash = (Get-FileHash -Path $backupPath -Algorithm SHA256).Hash

$match = ($origHash -eq $backupHash)

Write-Output "--- APK BACKUP VERIFICATION REPORT ---"
Write-Output "Original Path: $origApk"
Write-Output "Original Size: $origSize bytes"
Write-Output "Original Timestamp: $origTimestamp"
Write-Output "Original SHA256: $origHash"
Write-Output "Backup Path: $backupPath"
Write-Output "Backup Size: $backupSize bytes"
Write-Output "Backup Timestamp: $backupTimestamp"
Write-Output "Backup SHA256: $backupHash"
Write-Output "Hash Match Result: $match"

if ($match) {
    Write-Output "STATUS: SUCCESS"
} else {
    Write-Output "STATUS: FAILED"
    exit 2
}
