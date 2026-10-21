$adb = 'C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$phone = '10BE830QG8000MX'
$tablet = 'HNP097HQ'
$pkg = 'org.itantra.app'
$outDir = 'c:\Users\saich\Downloads\SIH\PS26173\scratch\uiux_phase2'

function GetNodeBoundsAndText($deviceId, $resId) {
    & $adb -s $deviceId shell uiautomator dump /sdcard/dump.xml | Out-Null
    [xml]$xml = (& $adb -s $deviceId shell cat /sdcard/dump.xml) -join ""
    $node = $xml.SelectSingleNode("//node[@resource-id='$resId']")
    if ($node) {
        $text = $node.GetAttribute("text")
        $bounds = $node.GetAttribute("bounds") # e.g. [711,177][858,285]
        if ($bounds -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
            $x = ([int]$matches[1] + [int]$matches[3]) / 2
            $y = ([int]$matches[2] + [int]$matches[4]) / 2
            return @{ Text = $text; X = $x; Y = $y; Found = $true }
        }
    }
    return @{ Found = $false }
}

function SetDeviceTheme($deviceId, $targetTheme) {
    for ($i = 0; $i -lt 5; $i++) {
        $info = GetNodeBoundsAndText $deviceId "org.itantra.app:id/btn_theme_toggle"
        if ($info.Found) {
            Write-Output "[$deviceId] Theme Button is '${info.Text}' (Target: $targetTheme)"
            if ($info.Text -eq $targetTheme) {
                return $true
            }
            Write-Output "[$deviceId] Tapping theme button at ($($info.X), $($info.Y))..."
            & $adb -s $deviceId shell input tap $info.X $info.Y
            Start-Sleep -Seconds 2
        } else {
            Write-Output "[$deviceId] Theme button not found"
            Start-Sleep -Seconds 1
        }
    }
    return $false
}

function CaptureScreenshot($deviceId, $filename) {
    $remotePath = "/sdcard/$filename"
    $localPath = Join-Path $outDir $filename
    & $adb -s $deviceId shell screencap -p $remotePath
    & $adb -s $deviceId pull $remotePath $localPath | Out-Null
    & $adb -s $deviceId shell rm $remotePath
    Write-Output "Captured $filename from $deviceId"
}

# 1. PHONE -> LIGHT MODE (Receiver)
Write-Output "=== 1. Phone Light Mode ==="
& $adb -s $phone shell am start -n org.itantra.app/org.itantra.MainActivity
Start-Sleep -Seconds 2
SetDeviceTheme $phone "LIGHT"
Start-Sleep -Seconds 1
CaptureScreenshot $phone "01_light_receiver.png"

# 2. PHONE -> DARK MODE (Receiver)
Write-Output "=== 2. Phone Dark Mode ==="
SetDeviceTheme $phone "DARK"
Start-Sleep -Seconds 1
CaptureScreenshot $phone "02_dark_receiver.png"

# 3. TABLET -> LIGHT MODE & TRANSMITTER
Write-Output "=== 3. Tablet Light Mode (Transmitter) ==="
& $adb -s $tablet shell am start -n org.itantra.app/org.itantra.MainActivity
Start-Sleep -Seconds 2
SetDeviceTheme $tablet "LIGHT"
# Switch to Transmitter
$clientBtn = GetNodeBoundsAndText $tablet "org.itantra.app:id/btn_mode_client"
if ($clientBtn.Found) {
    & $adb -s $tablet shell input tap $clientBtn.X $clientBtn.Y
    Start-Sleep -Seconds 1
}
CaptureScreenshot $tablet "03_light_transmitter.png"

# 4. TABLET -> DARK MODE (Transmitter)
Write-Output "=== 4. Tablet Dark Mode (Transmitter) ==="
SetDeviceTheme $tablet "DARK"
Start-Sleep -Seconds 1
CaptureScreenshot $tablet "04_dark_transmitter.png"

# 5. ERROR BANNER SCREENSHOT
Write-Output "=== 5. Error State ==="
# Tap Connect with empty IP or invalid IP
$connBtn = GetNodeBoundsAndText $tablet "org.itantra.app:id/btn_connect_peer"
if ($connBtn.Found) {
    & $adb -s $tablet shell input tap $connBtn.X $connBtn.Y
    Start-Sleep -Seconds 1
}
CaptureScreenshot $tablet "06_error.png"

# 6. CONNECTED STATE SCREENSHOT
Write-Output "=== 6. Connected State ==="
$etIp = GetNodeBoundsAndText $tablet "org.itantra.app:id/et_peer_ip"
if ($etIp.Found) {
    & $adb -s $tablet shell input tap $etIp.X $etIp.Y
    Start-Sleep -Milliseconds 300
    & $adb -s $tablet shell input text "172.20.105.179"
    Start-Sleep -Milliseconds 300
    $connBtn = GetNodeBoundsAndText $tablet "org.itantra.app:id/btn_connect_peer"
    if ($connBtn.Found) {
        & $adb -s $tablet shell input tap $connBtn.X $connBtn.Y
        Start-Sleep -Seconds 2
    }
}
CaptureScreenshot $tablet "05_connected.png"

# 7. PTT ACTIVE STATE SCREENSHOT
Write-Output "=== 7. PTT Active State ==="
$ptt = GetNodeBoundsAndText $tablet "org.itantra.app:id/btn_ptt"
if ($ptt.Found) {
    & $adb -s $tablet shell input tap $ptt.X $ptt.Y
    Start-Sleep -Seconds 1
}
CaptureScreenshot $tablet "07_ptt_active.png"
# Tap PTT to stop
if ($ptt.Found) {
    & $adb -s $tablet shell input tap $ptt.X $ptt.Y
    Start-Sleep -Seconds 1
}

Write-Output "ALL SCREENSHOTS CAPTURED SUCCESSFULLY!"
