import subprocess

ADB = r"C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe"
PHONE = "10BE830QG8000MX"
TABLET = "HNP097HQ"

def adb_shell(device, cmd):
    res = subprocess.run([ADB, "-s", device, "shell", cmd], capture_output=True, text=True)
    return res.stdout.strip()

print("--- PHONE ---")
print("Tethering/AP interfaces:")
for line in adb_shell(PHONE, "ip addr").splitlines():
    if "inet " in line or "state UP" in line or "swlan" in line or "wlan" in line or "ap" in line or "softap" in line or "rndis" in line:
        print(line)

print("\n--- TABLET ---")
print("wlan0 / interfaces:")
for line in adb_shell(TABLET, "ip addr").splitlines():
    if "inet " in line or "state UP" in line or "wlan0" in line:
        print(line)

print("\nTABLET GATEWAY & ROUTE:")
print(adb_shell(TABLET, "ip route"))

print("\nTABLET WIFI SSID:")
print(adb_shell(TABLET, "dumpsys wifi | grep -i SSID | head -n 5"))
