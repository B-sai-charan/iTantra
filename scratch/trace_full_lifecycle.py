import subprocess

ADB = r"C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe"
PHONE = "10BE830QG8000MX"
TABLET = "HNP097HQ"

def trace_pid(dev_id):
    pid = subprocess.run([ADB, "-s", dev_id, "shell", "pidof org.itantra.app"], capture_output=True, text=True).stdout.strip()
    print(f"=== {dev_id} (PID: {pid}) ===")
    logs = subprocess.run([ADB, "-s", dev_id, "shell", f"logcat -d --pid={pid}"], capture_output=True, text=True).stdout
    for line in logs.splitlines():
        if any(k in line for k in ["PHYSICAL_E2E", "MainActivity", "Transceiver", "TcpConnectionManager", "SocketMessageTransport", "reconnectWithConfig", "startTransceiver", "stopTransceiver", "release"]):
            print(line)

print("PHONE TRACE:")
trace_pid(PHONE)

print("\nTABLET TRACE:")
trace_pid(TABLET)
