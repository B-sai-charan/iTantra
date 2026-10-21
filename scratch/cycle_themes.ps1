$adb = 'C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$phone = '10BE830QG8000MX'
$tablet = 'HNP097HQ'
$pkg = 'org.itantra.app'
$outDir = 'c:\Users\saich\Downloads\SIH\PS26173\scratch\uiux_phase2'

function CaptureScreenshot($deviceId, $filename) {
    $remotePath = "/sdcard/$filename"
    $localPath = Join-Path $outDir $filename
    & $adb -s $deviceId shell screencap -p $remotePath
    & $adb -s $deviceId pull $remotePath $localPath | Out-Null
    & $adb -s $deviceId shell rm $remotePath
    Write-Output "Captured $filename from $deviceId"
}

# 1. PHONE LIGHT MODE
& $adb -s $phone shell am start -n org.itantra.app/org.itantra.MainActivity
Start-Sleep -Seconds 2

for ($i = 0; $i -lt 4; $i++) {
    & $adb -s $phone shell uiautomator dump /sdcard/dump.xml
    $dump = (& $adb -s $phone shell cat /sdcard/dump.xml) -join ""
    if ($dump -match 'resource-id="org.itantra.app:id/btn_theme_toggle"[^>]*text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
        $cur = $matches[1]
        $x = ([int]$matches[2] + [int]$matches[4]) / 2
        $y = ([int]$matches[3] + [int]$matches[5]) / 2
        Write-Output "Phone current theme mode button text: $cur"
        if ($cur -eq "LIGHT") {
            break
        }
        Write-Output "Tapping theme toggle to cycle..."
        & $adb -s $phone shell input tap $x $y
        Start-Sleep -Seconds 2
    }
}
Start-Sleep -Seconds 1
CaptureScreenshot $phone "01_light_receiver.png"

# 2. PHONE DARK MODE
for ($i = 0; $i -lt 4; $i++) {
    & $adb -s $phone shell uiautomator dump /sdcard/dump.xml
    $dump = (& $adb -s $phone shell cat /sdcard/dump.xml) -join ""
    if ($dump -match 'resource-id="org.itantra.app:id/btn_theme_toggle"[^>]*text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
        $cur = $matches[1]
        $x = ([int]$matches[2] + [int]$matches[4]) / 2
        $y = ([int]$matches[3] + [int]$matches[5]) / 2
        Write-Output "Phone current theme mode button text: $cur"
        if ($cur -eq "DARK") {
            break
        }
        Write-Output "Tapping theme toggle to cycle..."
        & $adb -s $phone shell input tap $x $y
        Start-Sleep -Seconds 2
    }
}
Start-Sleep -Seconds 1
CaptureScreenshot $phone "02_dark_receiver.png"

# 3. TABLET LIGHT MODE & TRANSMITTER
& $adb -s $tablet shell am start -n org.itantra.app/org.itantra.MainActivity
Start-Sleep -Seconds 2

for ($i = 0; $i -lt 4; $i++) {
    & $adb -s $tablet shell uiautomator dump /sdcard/dump.xml
    $tDump = (& $adb -s $tablet shell cat /sdcard/dump.xml) -join ""
    if ($tDump -match 'resource-id="org.itantra.app:id/btn_theme_toggle"[^>]*text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
        $cur = $matches[1]
        $x = ([int]$matches[2] + [int]$matches[4]) / 2
        $y = ([int]$matches[3] + [int]$matches[5]) / 2
        Write-Output "Tablet current theme mode button text: $cur"
        if ($cur -eq "LIGHT") {
            break
        }
        Write-Output "Tapping theme toggle to cycle..."
        & $adb -s $tablet shell input tap $x $y
        Start-Sleep -Seconds 2
    }
}

# Select Transmitter role on tablet
& $adb -s $tablet shell uiautomator dump /sdcard/dump.xml
$tDump = (& $adb -s $tablet shell cat /sdcard/dump.xml) -join ""
if ($tDump -match 'resource-id="org.itantra.app:id/btn_mode_client"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
    $cx = ([int]$matches[1] + [int]$matches[3]) / 2
    $cy = ([int]$matches[2] + [int]$matches[4]) / 2
    & $adb -s $tablet shell input tap $cx $cy
    Start-Sleep -Seconds 1
}
CaptureScreenshot $tablet "03_light_transmitter.png"

# 4. TABLET DARK MODE & TRANSMITTER
for ($i = 0; $i -lt 4; $i++) {
    & $adb -s $tablet shell uiautomator dump /sdcard/dump.xml
    $tDump = (& $adb -s $tablet shell cat /sdcard/dump.xml) -join ""
    if ($tDump -match 'resource-id="org.itantra.app:id/btn_theme_toggle"[^>]*text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
        $cur = $matches[1]
        $x = ([int]$matches[2] + [int]$matches[4]) / 2
        $y = ([int]$matches[3] + [int]$matches[5]) / 2
        Write-Output "Tablet current theme mode button text: $cur"
        if ($cur -eq "DARK") {
            break
        }
        Write-Output "Tapping theme toggle to cycle..."
        & $adb -s $tablet shell input tap $x $y
        Start-Sleep -Seconds 2
    }
}
Start-Sleep -Seconds 1
CaptureScreenshot $tablet "04_dark_transmitter.png"

Write-Output "THEME CYCLE CAPTURE COMPLETE"
