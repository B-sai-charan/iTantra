$adb = 'C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $adb -s 10BE830QG8000MX shell uiautomator dump /sdcard/dump.xml
$c = (& $adb -s 10BE830QG8000MX shell cat /sdcard/dump.xml) -join ""
[regex]::Matches($c, '<node[^>]*btn_theme_toggle[^>]*>') | ForEach-Object { Write-Output $_.Value }
