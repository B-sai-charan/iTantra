$adb = 'C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$phone = '10BE830QG8000MX'
$tablet = 'HNP097HQ'
$pkg = 'org.itantra.app'
$activity = 'org.itantra.MainActivity'
$component = "$pkg/$activity"
$outDir = 'c:\Users\saich\Downloads\SIH\PS26173\scratch\uiux_phase2'

if (-not (Test-Path $outDir)) {
    New-Item -ItemType Directory -Path $outDir -Force | Out-Null
}

function CaptureScreenshot($deviceId, $filename) {
    $remotePath = "/sdcard/$filename"
    $localPath = Join-Path $outDir $filename
    & $adb -s $deviceId shell screencap -p $remotePath
    & $adb -s $deviceId pull $remotePath $localPath | Out-Null
    & $adb -s $deviceId shell rm $remotePath
    Write-Output "Captured $filename from $deviceId"
}

# Stop any previous instances
& $adb -s $phone shell am force-stop $pkg
& $adb -s $tablet shell am force-stop $pkg
Start-Sleep -Milliseconds 500

# ----------------------------------------------------
# STEP 1: Phone in LIGHT MODE (Receiver)
# ----------------------------------------------------
Write-Output "--- Setting Phone to Light Mode (Receiver) ---"
& $adb -s $phone shell am start -n $component
Start-Sleep -Seconds 2

# Inspect UI and find Theme Toggle button
& $adb -s $phone shell uiautomator dump /sdcard/dump.xml
$dump = (& $adb -s $phone shell cat /sdcard/dump.xml) -join ""

# If text is AUTO or DARK, click to get LIGHT
# We can find btn_theme_toggle bounds from dump
if ($dump -match 'resource-id="org.itantra.app:id/btn_theme_toggle"[^>]*text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
    $themeText = $matches[1]
    $x = ([int]$matches[2] + [int]$matches[4]) / 2
    $y = ([int]$matches[3] + [int]$matches[5]) / 2
    Write-Output "Theme toggle is currently: $themeText at ($x, $y)"
    
    # We want LIGHT mode
    while ($themeText -ne "LIGHT") {
        & $adb -s $phone shell input tap $x $y
        Start-Sleep -Seconds 1
        & $adb -s $phone shell uiautomator dump /sdcard/dump.xml
        $dump = (& $adb -s $phone shell cat /sdcard/dump.xml) -join ""
        if ($dump -match 'resource-id="org.itantra.app:id/btn_theme_toggle"[^>]*text="([^"]*)"') {
            $themeText = $matches[1]
            Write-Output "Theme toggle switched to: $themeText"
        } else {
            break
        }
    }
}
Start-Sleep -Seconds 1
CaptureScreenshot $phone "01_light_receiver.png"

# ----------------------------------------------------
# STEP 2: Phone in DARK MODE (Receiver)
# ----------------------------------------------------
Write-Output "--- Switching Phone to Dark Mode (Receiver) ---"
& $adb -s $phone shell uiautomator dump /sdcard/dump.xml
$dump = (& $adb -s $phone shell cat /sdcard/dump.xml) -join ""
if ($dump -match 'resource-id="org.itantra.app:id/btn_theme_toggle"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
    $x = ([int]$matches[1] + [int]$matches[3]) / 2
    $y = ([int]$matches[2] + [int]$matches[4]) / 2
    & $adb -s $phone shell input tap $x $y
    Start-Sleep -Seconds 1
}
CaptureScreenshot $phone "02_dark_receiver.png"

# ----------------------------------------------------
# STEP 3: Tablet in LIGHT MODE (Transmitter)
# ----------------------------------------------------
Write-Output "--- Setting Tablet to Light Mode & Transmitter ---"
& $adb -s $tablet shell am start -n $component
Start-Sleep -Seconds 2

& $adb -s $tablet shell uiautomator dump /sdcard/dump.xml
$tDump = (& $adb -s $tablet shell cat /sdcard/dump.xml) -join ""

if ($tDump -match 'resource-id="org.itantra.app:id/btn_theme_toggle"[^>]*text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
    $themeText = $matches[1]
    $x = ([int]$matches[2] + [int]$matches[4]) / 2
    $y = ([int]$matches[3] + [int]$matches[5]) / 2
    Write-Output "Tablet Theme toggle is currently: $themeText at ($x, $y)"
    while ($themeText -ne "LIGHT") {
        & $adb -s $tablet shell input tap $x $y
        Start-Sleep -Seconds 1
        & $adb -s $tablet shell uiautomator dump /sdcard/dump.xml
        $tDump = (& $adb -s $tablet shell cat /sdcard/dump.xml) -join ""
        if ($tDump -match 'resource-id="org.itantra.app:id/btn_theme_toggle"[^>]*text="([^"]*)"') {
            $themeText = $matches[1]
            Write-Output "Tablet Theme toggle switched to: $themeText"
        } else {
            break
        }
    }
}

# Click Transmitter role button (btn_mode_client)
if ($tDump -match 'resource-id="org.itantra.app:id/btn_mode_client"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
    $cx = ([int]$matches[1] + [int]$matches[3]) / 2
    $cy = ([int]$matches[2] + [int]$matches[4]) / 2
    & $adb -s $tablet shell input tap $cx $cy
    Start-Sleep -Seconds 1
}
CaptureScreenshot $tablet "03_light_transmitter.png"

# ----------------------------------------------------
# STEP 4: Tablet in DARK MODE (Transmitter)
# ----------------------------------------------------
Write-Output "--- Switching Tablet to Dark Mode (Transmitter) ---"
& $adb -s $tablet shell uiautomator dump /sdcard/dump.xml
$tDump = (& $adb -s $tablet shell cat /sdcard/dump.xml) -join ""
if ($tDump -match 'resource-id="org.itantra.app:id/btn_theme_toggle"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
    $x = ([int]$matches[1] + [int]$matches[3]) / 2
    $y = ([int]$matches[2] + [int]$matches[4]) / 2
    & $adb -s $tablet shell input tap $x $y
    Start-Sleep -Seconds 1
}
CaptureScreenshot $tablet "04_dark_transmitter.png"

# ----------------------------------------------------
# STEP 5: Error State screenshot (e.g. Empty IP validation or invalid IP on Tablet)
# ----------------------------------------------------
Write-Output "--- Generating and capturing Error state ---"
# Clear input field on tablet and tap Connect
if ($tDump -match 'resource-id="org.itantra.app:id/btn_connect_peer"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
    $connX = ([int]$matches[1] + [int]$matches[3]) / 2
    $connY = ([int]$matches[2] + [int]$matches[4]) / 2
    & $adb -s $tablet shell input tap $connX $connY
    Start-Sleep -Seconds 1
}
CaptureScreenshot $tablet "06_error.png"

# ----------------------------------------------------
# STEP 6: Two-device Connection (Tablet -> Phone) & Connected State screenshot
# ----------------------------------------------------
Write-Output "--- Connecting Tablet to Phone (172.20.105.179) ---"
# Phone Receiver is running on 172.20.105.179:42173
# Type IP into Tablet's et_peer_ip and Connect
& $adb -s $tablet shell uiautomator dump /sdcard/dump.xml
$tDump = (& $adb -s $tablet shell cat /sdcard/dump.xml) -join ""
if ($tDump -match 'resource-id="org.itantra.app:id/et_peer_ip"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
    $etX = ([int]$matches[1] + [int]$matches[3]) / 2
    $etY = ([int]$matches[2] + [int]$matches[4]) / 2
    & $adb -s $tablet shell input tap $etX $etY
    Start-Sleep -Milliseconds 300
    & $adb -s $tablet shell input text "172.20.105.179"
    Start-Sleep -Milliseconds 500
    # Tap Connect
    if ($tDump -match 'resource-id="org.itantra.app:id/btn_connect_peer"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
        $connX = ([int]$matches[1] + [int]$matches[3]) / 2
        $connY = ([int]$matches[2] + [int]$matches[4]) / 2
        & $adb -s $tablet shell input tap $connX $connY
        Start-Sleep -Seconds 2
    }
}
CaptureScreenshot $tablet "05_connected.png"

# ----------------------------------------------------
# STEP 7: PTT Active state screenshot
# ----------------------------------------------------
Write-Output "--- Starting Transceiver PTT on Tablet and capturing active state ---"
# Tap PTT button (btn_ptt)
& $adb -s $tablet shell uiautomator dump /sdcard/dump.xml
$tDump = (& $adb -s $tablet shell cat /sdcard/dump.xml) -join ""
if ($tDump -match 'resource-id="org.itantra.app:id/btn_ptt"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
    $pttX = ([int]$matches[1] + [int]$matches[3]) / 2
    $pttY = ([int]$matches[2] + [int]$matches[4]) / 2
    & $adb -s $tablet shell input tap $pttX $pttY
    Start-Sleep -Seconds 1
}
CaptureScreenshot $tablet "07_ptt_active.png"

# Tap PTT again to stop
if ($tDump -match 'resource-id="org.itantra.app:id/btn_ptt"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
    $pttX = ([int]$matches[1] + [int]$matches[3]) / 2
    $pttY = ([int]$matches[2] + [int]$matches[4]) / 2
    & $adb -s $tablet shell input tap $pttX $pttY
    Start-Sleep -Seconds 1
}

Write-Output "--- ALL UI VALIDATION SCREENSHOTS COMPLETE ---"
