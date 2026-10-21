$adb = 'C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$phone = '10BE830QG8000MX'
$tablet = 'HNP097HQ'
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

# 1. Stop any running instance on Phone
& $adb -s $phone shell am force-stop org.itantra
Start-Sleep -Milliseconds 500

# Set Light Mode directly in prefs or via launch
# Launch Receiver mode on Phone
& $adb -s $phone shell am start -n org.itantra/.MainActivity
Start-Sleep -Seconds 2

# Click theme toggle to set LIGHT if needed or verify current
# Let's inspect current UI
CaptureScreenshot $phone "01_light_receiver.png"

# Now click theme toggle on Phone to switch to DARK
# Find view coordinates or click btn_theme_toggle or tap header right
# Let's dump uiautomator to get exact bounds of btn_theme_toggle
& $adb -s $phone shell uiautomator dump /sdcard/window_dump.xml
& $adb -s $phone pull /sdcard/window_dump.xml $outDir\phone_dump.xml | Out-Null

Write-Output "Dumped UI hierarchy for Phone"
