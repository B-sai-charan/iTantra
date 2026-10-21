# M1-DEVICE-B-RED-TEXT-AUDIT-01: Forensic UI & Log Audit of Device B Status Banner

**Document ID:** `M1-DEVICE-B-RED-TEXT-AUDIT-01`  
**Date:** 2026-09-28  
**Device Under Audit:** Device B (`10BE830QG8000MX`, vivo I2304)  
**Status:** **`AUDIT COMPLETE — BEHAVIOR EXPLAINED & CLASSIFIED`**  
**Production Code Changed:** **0 (READ-ONLY AUDIT)**  

---

## 1. Device & Application Information

| Property | Value |
| :--- | :--- |
| **Device Model** | vivo I2304 (`vivo I2304`, `I2304T`) |
| **ADB Serial** | `10BE830QG8000MX` |
| **Android Version** | Android 16 (API 36 / Preview) |
| **CPU Architecture** | `arm64-v8a` |
| **Installed Package** | `org.itantra.app` (`org.itantra`) |
| **Current Focused Activity** | `org.itantra.app/org.itantra.MainActivity` |
| **Installed Build** | `app-debug.apk` (`315,167,039 bytes`) |
| **Build Checksum** | `2EAFAAA7D3F0C265CF36BA66D3F678DFF9D47E0DF9EB8318F60CC97BC45AACC2` |

---

## 2. Exact Visible Red Text on Device B

From the live screen capture (`device_b_red_text.png`) and UI hierarchy dump (`window_dump.xml`), the exact text appearing in the red banner at the bottom of Device B is:

```
Network transceiver link disconnected
([M2 Transport Error] Transport Error: Cannot send speech message: no active MessageTransport bound to SpeechMessageTransportAdapter)
```

Additionally, the central status text below the PTT button displays:
```
Error (M2 Transport Error)
```

And the Transmitted card displays the locally recognized speech hypothesis:
```
"no."
```

---

## 3. UI Component & Styling Trace

| Property | Value in Code / Layout |
| :--- | :--- |
| **Target View ID** | `android:id="@+id/tv_last_error"` |
| **Layout File** | `app/src/main/res/layout/activity_main.xml` (Lines 282–296) |
| **Container Background** | `android:background="@drawable/bg_status_pill"` |
| **Container Tint** | `android:backgroundTint="#2D1515"` (Dark crimson red) |
| **Text Color Resource** | `android:textColor="@color/status_red"` (`#EF4444`) |
| **Parent Component** | Root `ConstraintLayout` inside `activity_main.xml` |

```xml
<!-- Error Banner in activity_main.xml -->
<TextView
    android:id="@+id/tv_last_error"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="12dp"
    android:background="@drawable/bg_status_pill"
    android:backgroundTint="#2D1515"
    android:paddingStart="12dp"
    android:paddingTop="8dp"
    android:paddingEnd="12dp"
    android:paddingBottom="8dp"
    android:textColor="@color/status_red"
    android:textSize="12sp"
    android:visibility="gone"
    tools:text="Error details" />
```

---

## 4. End-to-End Source Code Trace

The exact execution flow that caused the red banner to appear on Device B:

```
[ EVENT ]
1. Device B was launched in Server/Receiver mode via RUN_PHYSICAL_M2_M3_RECEIVER.
2. In Server mode, Device B binds a TCP Server on port 42173 to receive inbound messages from Device A.
3. Because Device B is in Server mode, its outbound SpeechMessageTransportAdapter does NOT have an active outbound TCP client transport bound to transmit back to Device A.

[ TRIGGER ]
4. An ambient sound/voice utterance ("no.") occurred near Device B's physical microphone.
5. Silero VAD triggered a speech boundary and Whisper Tiny INT8 transcribed the word "no.".
6. TransceiverCoordinator attempted to emit this local transcript outbound by calling:
   SpeechMessageTransportAdapter.sendSpeechMessage(message)

[ EXCEPTION ]
7. SpeechMessageTransportAdapter.kt (Line 130) checked _transport, found it was null, and threw:
   TransportException("Cannot send speech message: no active MessageTransport bound to SpeechMessageTransportAdapter")

[ PROPAGATION & FORMATTING ]
8. TransceiverCoordinator caught the exception and invoked listener.onError(TransceiverError.M2TransportError(...)).
9. TransceiverUiFormatter.kt (Line 79) mapped the error to:
   - User-friendly message: "Network transceiver link disconnected"
   - Detail string: "([M2 Transport Error] Transport Error: Cannot send speech message: no active MessageTransport bound to SpeechMessageTransportAdapter)"

[ UI RENDERING ]
10. MainActivity.kt (Line 1578 - createTransceiverListener.onError) executed on UI thread:
    - tvStatus.text = "Error (M2 Transport Error)"
    - tvLastError.text = "$userFriendly\n($detail)"
    - tvLastError.visibility = View.VISIBLE (Rendered with #EF4444 red text and #2D1515 red background)
```

---

## 5. Receiver State & Functional Assessment

| Subsystem on Device B | Current State | Operational Verdict |
| :--- | :--- | :--- |
| **TCP Server Listener (Port 42173)** | **ACTIVE & BOUND** | Inbound network port 42173 remains open and ready for incoming packets from Device A. |
| **Inbound Speech Processing** | **OPERATIONAL** | When Device A transmits, `onInboundSpeechMessage` updates `tvRemoteTranscript` cleanly. |
| **Neural TTS Engine (Piper VITS)** | **OPERATIONAL** | `SherpaOfflineTtsEngine` synthesizes received text without stalls. |
| **AudioTrack Playback Engine** | **OPERATIONAL** | Renders synthesized speech through the physical loudspeaker. |
| **App Process Stability** | **HEALTHY** | Process PID `682` is active with 0 ANRs, 0 OOMs, 0 crashes. |

---

## 6. Classification

### **`B. EXPECTED WARNING / EXPECTED UNBOUND OUTBOUND TRANSPORT IN RECEIVER-ONLY MODE`**

**Rationale:**  
The red text is **NOT** a crash, fatal error, or defect in the inbound reception pipeline. It is a protective, handled warning indicating that Device B was initialized as a dedicated inbound receiver server, and therefore attempted outbound transmissions (from its own local microphone) cannot be sent because there is no reverse client socket bound to Device A.

---

## 7. Impact on Live SIH E2E Demonstration

- **Inbound Voice-to-Voice Demo (Device A $\to$ Device B):** **NO IMPACT**.  
  When Device A speaks and transmits across Wi-Fi, Device B receives the packet, displays the remote text on the **REMOTE (Received)** card, and synthesizes speech through the loudspeaker.
- **Reverse Direction (Device B $\to$ Device A):** Requires establishing a reverse client connection to Device A's IP (as documented in `M1_FINAL_TWO_DEVICE_LIVE_E2E_04.md`).

---

## 8. Recommended Action

### **`NO PRODUCTION CHANGE REQUIRED.`**

The application handled the unbound transport scenario gracefully via its exception handlers, prevented a native crash, updated the UI cleanly, and maintained full readiness for inbound transmissions.
