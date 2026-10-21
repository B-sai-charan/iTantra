# iTantra PS26173 — Adversarial Bug Hunt Baseline

**Document ID**: `BUG_HUNT_BASELINE_01`  
**Date**: October 1, 2026  
**Role**: Lead Bug Hunter / Adversarial QA Engineer  
**Scope**: Complete System Adversarial Audit & Physical Hotspot Validation  

---

## 1. Environment & Build Baseline

| Property | Value | Notes |
|---|---|---|
| **Repository Root** | `c:\Users\saich\Downloads\SIH\PS26173` | Working tree (standalone directory) |
| **Git Status** | Standalone directory (No `.git` root in folder) | Snapshot baseline |
| **Gradle Version** | Gradle 8.11.1 (via wrapper) | Verified via `gradle-wrapper.properties` |
| **Android Gradle Plugin (AGP)** | 8.7.2 | `libs.versions.toml` |
| **Kotlin Version** | 2.0.21 | JVM Target: 17 |
| **Compile SDK / Target SDK** | 35 / 35 | Android 15 ready |
| **Min SDK** | 24 (Android 7.0) | Target ABI: `arm64-v8a` |
| **Unit Test Suite Status** | **408 Passed / 0 Failed / 0 Ignored** | 47 Test Suites in 5.4s (`testDebugUnitTest`) |
| **Debug APK Path** | `app/build/outputs/apk/debug/app-debug.apk` | Built with `./gradlew assembleDebug` |
| **Debug APK File Size** | 315,427,692 bytes (300.82 MB) | Includes ONNX runtime + quantized models |
| **Debug APK SHA-256** | `80E0C2DBE3B30306F62FB623F76630286079EE05D213761B42D3072CC2CA274A` | Baseline verified |

---

## 2. Physical Devices Hardware & OS Baseline

### Device A (Transmitter / Primary Phone)
- **Model**: iQOO Z9 (`vivo I2304` / `I2304T`)
- **ADB Serial**: `10BE830QG8000MX`
- **Android OS**: Android 14 / 15 (`ro.build.version.release = 16`, SDK `36` preview/dev API level)
- **CPU Architecture**: ARM64-v8a (MediaTek Dimensity 7200 / Snapdragon 7 Gen 3 family)
- **RAM Total**: 7,445,084 kB (~8 GB RAM)
- **Status**: Online, Streamed APK install Successful

### Device B (Receiver / Target Tablet)
- **Model**: Lenovo Tab (`Lenovo TB-X6C6F` / `X6C6F`)
- **ADB Serial**: `HNP097HQ`
- **Android OS**: Android 12 (`ro.build.version.release = 12`, SDK `31`)
- **CPU Architecture**: ARM64-v8a
- **RAM Total**: 2,924,092 kB (~3 GB RAM)
- **Available Flash Storage**: 5.6 GB free (`/data/user/0` partition)
- **Status**: Connected via Mobile Hotspot (`10.179.231.149`)

---

## 3. Dynamic Hotspot Network Topology Discovery

Both devices are connected via a dedicated Mobile Hotspot:

```
[ Mobile Hotspot Router: Subnet 10.179.231.0/24 ]
                |
    +-----------+-----------+
    |                       |
[ Device A: iQOO ]     [ Device B: Lenovo Tablet ]
  Interface: wlan2       Interface: wlan0
  IP: 10.179.231.166     IP: 10.179.231.149
```

- **Subnet**: `10.179.231.0/24`
- **Device A (iQOO) Dynamic IP**: `10.179.231.166`
- **Device B (Lenovo Tablet) Dynamic IP**: `10.179.231.149`
- **Bidirectional Ping Verification**:
  - `A -> B` (10.179.231.166 -> 10.179.231.149): 3 packets, 0% loss, Avg RTT = 56.1 ms
  - `B -> A` (10.179.231.149 -> 10.179.231.166): 3 packets, 0% loss, Avg RTT = 17.1 ms
- **Target TCP Communication Port**: `42173`

---

## 4. Initial Adversarial Findings during Baseline Establishment

1. **APK Signature Stream Issue on Legacy Android 12 (Device B)**:
   - When streaming a 300MB APK over ADB to Android 12 device under low memory/fast transfer, ADB reported `INSTALL_PARSE_FAILED_NO_CERTIFICATES: SHA-256 digest did not verify`.
   - Cause: Streaming large monolithic APK over unstable USB connection.
   - Mitigation: Ensure USB debugging is stable or pre-push to `/data/local/tmp` / Wi-Fi ADB.

2. **Network Interface Heterogeneity**:
   - Device A assigns hotspot Wi-Fi to interface `wlan2` (due to dual-Wi-Fi acceleration or cellular dual-SIM routing), while Device B binds to `wlan0`. The app must not assume `wlan0` when enumerating local IP addresses.

---
