$adb = 'C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$apk = 'c:\Users\saich\Downloads\SIH\PS26173\app\build\outputs\apk\debug\app-debug.apk'

Write-Output "Installing new APK on Phone (10BE830QG8000MX)..."
& $adb -s 10BE830QG8000MX install -r $apk

Write-Output "Installing new APK on Tablet (HNP097HQ)..."
& $adb -s HNP097HQ install -r $apk

Write-Output "Checking IP addresses..."
$phoneIp = (& $adb -s 10BE830QG8000MX shell ip -f inet addr show wlan0) -join "`n"
$tabletIp = (& $adb -s HNP097HQ shell ip -f inet addr show wlan0) -join "`n"

Write-Output "--- Phone wlan0 ---"
Write-Output $phoneIp
Write-Output "--- Tablet wlan0 ---"
Write-Output $tabletIp
