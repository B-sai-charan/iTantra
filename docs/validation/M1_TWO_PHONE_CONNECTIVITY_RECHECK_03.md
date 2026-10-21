# Two-Phone Connectivity Recheck Report (`M1-TWO-PHONE-CONNECTIVITY-RECHECK-03`)

**Document ID:** `M1_TWO_PHONE_CONNECTIVITY_RECHECK_03`  
**Date:** 2026-09-28  
**Time:** 22:52 IST  
**Application:** `org.itantra` (`org.itantra.app`)  
**APK:** `app/build/outputs/apk/debug/app-debug.apk` (315,167,039 bytes)  
**Status:** **PHYSICAL CONNECTIVITY RECHECK COMPLETE**  
**Production Code Changed:** **0 (NO CODE CHANGES)**  

---

## 1. Objective & Recheck Context

This task rechecked physical cross-device M2 TCP communication between two physical hardware devices:
- **Device A (Transmitter / Client):** Lenovo TB-X6C6F (`HNP097HQ`, Android 12, IP: `172.20.84.74`)
- **Device B (Receiver / Target):** vivo I2304 (`10BE830QG8000MX`, Android 16, IP: `172.20.29.81`)

**Crucial Constraint:** The test explicitly targeted Device B's actual Wi-Fi IP address (`172.20.29.81:42173`) and prohibited loopback (`127.0.0.1`).

---

## 2. Pre-Flight Device & Network Status

| Attribute | Device A (Lenovo TB-X6C6F) | Device B (vivo I2304) |
| :--- | :--- | :--- |
| **Serial** | `HNP097HQ` | `10BE830QG8000MX` |
| **ADB Connection** | Attached via USB (`device`) | **Not attached via USB / Wireless ADB offline** |
| **IP Address** | `172.20.84.74` | `172.20.29.81` |
| **ICMP Ping Status** | Responding | **Responding** (RTT: 82–117 ms) |
| **TCP Port 42173 Status** | N/A (Client role) | **Closed / Not Listening** (`ECONNREFUSED`) |
| **TCP Port 19500 Status** | N/A | **Closed / Not Listening** (`ECONNREFUSED`) |
| **TCP Port 5555 (ADB) Status**| N/A | **Closed / Not Listening** (`ECONNREFUSED`) |

---

## 3. Test Execution & Evidence

### Step 1: Wi-Fi Reachability Verification
```powershell
Test-Connection -ComputerName 172.20.29.81 -Count 1 -Quiet
Result: True (Ping Succeeded, RTT 82ms)
```

### Step 2: Attempted Remote Server Initialization on Device B
- Because Device B (`10BE830QG8000MX`) was not connected via USB and had wireless ADB disabled, automated ADB intent triggering (`RUN_PHYSICAL_TCP_SERVER`) could not be dispatched to Device B.
- TCP port scan against `172.20.29.81` confirmed that no active server socket was listening on port 42173 or 19500.

### Step 3: Physical Client Execution from Device A Targeting Device B
Executed the production TCP client on Device A targeting `172.20.29.81:42173`:
```bash
adb -s HNP097HQ shell am start -n org.itantra.app/org.itantra.MainActivity \
  -a org.itantra.action.RUN_PHYSICAL_TCP_CLIENT \
  --es serverHost 172.20.29.81 \
  --ei serverPort 42173
```

### Step 4: Observed Result on Physical Device A
- **UI Status Banner:** `TCP VALIDATION: FAIL`
- **Exact Error Message Displayed on Device A Screen:**
  ```
  Error: Connect failed: Failed to connect to TCP host 172.20.29.81:42173: 
  failed to connect to /172.20.29.81 (port 42173) from /172.20.84.74 (port 37540) after 8000ms: 
  isConnected failed: ECONNREFUSED (Connection refused)
  ```
- **Screenshot Artifact:** [`docs/validation/ui_tcp_cross_screenshot.png`](file:///c:/Users/saich/Downloads/SIH/PS26173/docs/validation/ui_tcp_cross_screenshot.png)

---

## 4. Layer-by-Layer Connectivity Classification

| Layer / Test Metric | Classification | Operational Finding |
| :--- | :---: | :--- |
| **Wi-Fi Subnet Reachability** | **PASS** | `172.20.84.74` $\leftrightarrow$ `172.20.29.81` pingable on `172.20.0.0/16`. |
| **Device A $\rightarrow$ Device B TCP Connectivity** | **FAIL** | Connection refused (`ECONNREFUSED`) because server socket is not active on Device B. |
| **M2 Packet Transmission** | **FAIL** | Client could not establish socket transport to transmit packets. |
| **M2 Packet Reception** | **FAIL** | Zero packets delivered to Device B. |
| **Production Files Changed** | **0 (MUST be 0)** | Zero modifications made to production source code. |

---

## 5. Root Cause & Demonstration Prerequisite

- **Root Cause:** Device B is online on the local network (`172.20.29.81`), but without a physical USB connection or an active wireless ADB session, the receiver server process was not running or listening on port 42173 during this automated test run.
- **Prerequisite for Two-Device Live Demo:**
  1. Manually launch the iTantra app on Device B (or attach via USB to start the receiver via ADB).
  2. Start the transceiver/server on Device B.
  3. Initiate transmission from Device A targeting Device B's IP (`172.20.29.81:42173`).
