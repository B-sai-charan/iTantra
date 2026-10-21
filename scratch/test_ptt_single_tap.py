import subprocess
import time

ADB = r"C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe"
PHONE = "10BE830QG8000MX"

subprocess.run([ADB, "-s", PHONE, "logcat", "-c"])

# Click PTT once
print("Clicking PTT at (630, 1803)...")
subprocess.run([ADB, "-s", PHONE, "shell", "input", "tap", "630", "1803"])
time.sleep(1)

logs = subprocess.run([ADB, "-s", PHONE, "logcat", "-d"], capture_output=True, text=True).stdout
print("--- Logcat after PTT tap ---")
for line in logs.splitlines()[-40:]:
    print(line)
