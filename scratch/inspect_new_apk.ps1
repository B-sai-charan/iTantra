$newApk = 'c:\Users\saich\Downloads\SIH\PS26173\app\build\outputs\apk\debug\app-debug.apk'
$item = Get-Item $newApk
$size = $item.Length
$time = $item.LastWriteTime.ToString("yyyy-MM-dd HH:mm:ss")
$hash = (Get-FileHash -Path $newApk -Algorithm SHA256).Hash

Write-Output "--- NEW DEBUG APK DETAILS ---"
Write-Output "Path: $newApk"
Write-Output "Size: $size bytes"
Write-Output "Timestamp: $time"
Write-Output "SHA256: $hash"
