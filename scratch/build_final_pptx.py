import collections.abc
import os
import pptx
from pptx.util import Inches, Pt
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE

def create_presentation():
    prs = pptx.Presentation()
    prs.slide_width = Inches(13.333)
    prs.slide_height = Inches(7.5)

    # Blank slide layout
    blank_layout = prs.slide_layouts[6]

    # Color Palette
    COLOR_BG = RGBColor(15, 23, 42)         # #0F172A Deep Navy
    COLOR_CARD = RGBColor(30, 41, 59)       # #1E293B Slate Card
    COLOR_CARD_BORDER = RGBColor(51, 65, 85)# #334155 Border
    COLOR_ACCENT = RGBColor(56, 189, 248)   # #38BDF8 Cyan Blue
    COLOR_GREEN = RGBColor(16, 185, 129)    # #10B981 Emerald
    COLOR_AMBER = RGBColor(245, 158, 11)    # #F59E0B Amber
    COLOR_WHITE = RGBColor(255, 255, 255)   # #FFFFFF
    COLOR_MUTED = RGBColor(148, 163, 184)   # #94A3B8 Muted text
    COLOR_HIGHLIGHT = RGBColor(129, 140, 248) # #818CF8 Indigo Accent

    def set_slide_background(slide):
        bg_shape = slide.shapes.add_shape(
            MSO_SHAPE.RECTANGLE, 0, 0, Inches(13.333), Inches(7.5)
        )
        bg_shape.fill.solid()
        bg_shape.fill.fore_color.rgb = COLOR_BG
        bg_shape.line.fill.background() # No border
        return bg_shape

    def add_header(slide, title_text, category_text="iTantra // SIH 2026"):
        # Category Tag
        cat_box = slide.shapes.add_textbox(Inches(0.8), Inches(0.4), Inches(11.7), Inches(0.35))
        tf_c = cat_box.text_frame
        tf_c.word_wrap = True
        tf_c.margin_left = tf_c.margin_top = tf_c.margin_right = tf_c.margin_bottom = 0
        p_c = tf_c.paragraphs[0]
        p_c.text = category_text.upper()
        p_c.font.size = Pt(11)
        p_c.font.bold = True
        p_c.font.color.rgb = COLOR_ACCENT
        p_c.font.name = "Segoe UI"

        # Main Title
        title_box = slide.shapes.add_textbox(Inches(0.8), Inches(0.75), Inches(11.7), Inches(0.6))
        tf_t = title_box.text_frame
        tf_t.word_wrap = True
        tf_t.margin_left = tf_t.margin_top = tf_t.margin_right = tf_t.margin_bottom = 0
        p_t = tf_t.paragraphs[0]
        p_t.text = title_text
        p_t.font.size = Pt(24)
        p_t.font.bold = True
        p_t.font.color.rgb = COLOR_WHITE
        p_t.font.name = "Segoe UI"

    def add_card(slide, left, top, width, height, bg_color=COLOR_CARD, border_color=COLOR_CARD_BORDER):
        card = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, left, top, width, height)
        card.fill.solid()
        card.fill.fore_color.rgb = bg_color
        card.line.color.rgb = border_color
        card.line.width = Pt(1.5)
        return card

    def set_notes(slide, notes_text):
        notes_slide = slide.notes_slide
        text_frame = notes_slide.notes_text_frame
        text_frame.text = notes_text

    # =========================================================================
    # SLIDE 1: Title
    # =========================================================================
    s1 = prs.slides.add_slide(blank_layout)
    set_slide_background(s1)

    # Accent decorative bar
    bar = s1.shapes.add_shape(MSO_SHAPE.RECTANGLE, Inches(0.8), Inches(1.5), Inches(0.12), Inches(4.5))
    bar.fill.solid()
    bar.fill.fore_color.rgb = COLOR_ACCENT
    bar.line.fill.background()

    # Main Title & Subtitle Box
    tbox1 = s1.shapes.add_textbox(Inches(1.2), Inches(1.5), Inches(11.3), Inches(4.5))
    tf1 = tbox1.text_frame
    tf1.word_wrap = True

    p = tf1.paragraphs[0]
    p.text = "SMART INDIA HACKATHON 2026"
    p.font.size = Pt(13)
    p.font.bold = True
    p.font.color.rgb = COLOR_ACCENT
    p.font.name = "Segoe UI"
    p.space_after = Pt(10)

    p = tf1.add_paragraph()
    p.text = "iTantra"
    p.font.size = Pt(46)
    p.font.bold = True
    p.font.color.rgb = COLOR_WHITE
    p.font.name = "Segoe UI"
    p.space_after = Pt(6)

    p = tf1.add_paragraph()
    p.text = "Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access for Low Bitrate Links"
    p.font.size = Pt(18)
    p.font.color.rgb = COLOR_HIGHLIGHT
    p.font.name = "Segoe UI"
    p.space_after = Pt(24)

    p = tf1.add_paragraph()
    p.text = "An edge-native, cloud-independent neural voice transceiver enabling low-bitrate semantic voice transmission over local peer networks using on-device STT, compact binary framing, and on-device neural TTS."
    p.font.size = Pt(14)
    p.font.color.rgb = COLOR_MUTED
    p.font.name = "Segoe UI"
    p.space_after = Pt(28)

    p = tf1.add_paragraph()
    p.text = "Platform: Native Android (API 26–35)  |  Transport: Local TCP / Wi-Fi Peer-to-Peer  |  Build: v1.0-debug"
    p.font.size = Pt(12)
    p.font.bold = True
    p.font.color.rgb = COLOR_GREEN
    p.font.name = "Segoe UI"

    set_notes(s1, """PRESENTER NOTES (Slide 1):
1. What to say:
"Respected judges, we present iTantra — an on-device neural voice transceiver designed for low-bitrate, cloud-independent voice communication over constrained peer-to-peer networks. iTantra replaces continuous audio streaming with edge AI speech transcription, lightweight binary framing, and local neural speech synthesis."

2. Evidence supporting it:
Production APK identity (app-debug.apk), package org.itantra.app, native Android implementation (API 26–35).

3. Likely judge question:
"Is this an audio compressor or an AI pipeline?"

4. Safe answer:
"It is an AI-driven semantic voice transceiver. Instead of compressing audio waveforms, it performs speech recognition on the transmitting device, transmits the text semantically in a 29-byte frame, and reconstructs natural speech at the receiver using neural TTS."

5. Claim to avoid:
Do not claim it is a "universal replacement for all cellular radios" or "military-grade equipment." """)

    # =========================================================================
    # SLIDE 2: Problem
    # =========================================================================
    s2 = prs.slides.add_slide(blank_layout)
    set_slide_background(s2)
    add_header(s2, "Problem: Constraints of Traditional Audio Transmission")

    # Card 1: Traditional Audio Streaming
    add_card(s2, Inches(0.8), Inches(1.5), Inches(5.6), Inches(5.2))
    tb_p1 = s2.shapes.add_textbox(Inches(1.1), Inches(1.8), Inches(5.0), Inches(4.6))
    tf_p1 = tb_p1.text_frame
    tf_p1.word_wrap = True

    p = tf_p1.paragraphs[0]
    p.text = "TRADITIONAL AUDIO STREAMING"
    p.font.size = Pt(14)
    p.font.bold = True
    p.font.color.rgb = COLOR_AMBER
    p.space_after = Pt(14)

    p = tf_p1.add_paragraph()
    p.text = "• Continuous High Bandwidth Demand:\n  Digitized audio requires streaming continuous high-throughput data (~256 kbps for 16kHz 16-bit PCM), congesting tactical links."
    p.font.size = Pt(13)
    p.font.color.rgb = COLOR_MUTED
    p.space_after = Pt(12)

    p = tf_p1.add_paragraph()
    p.text = "• Severe Cloud Dependency:\n  Standard voice AI solutions depend on cloud APIs that fail completely in disconnected or disaster zones."
    p.font.size = Pt(13)
    p.font.color.rgb = COLOR_MUTED
    p.space_after = Pt(12)

    p = tf_p1.add_paragraph()
    p.text = "• Fragile to Packet Drops:\n  Dropped waveform packets cause severe audio distortion, clipping, and unintelligible communication."
    p.font.size = Pt(13)
    p.font.color.rgb = COLOR_MUTED

    # Card 2: Semantic Transmission Approach
    add_card(s2, Inches(6.9), Inches(1.5), Inches(5.6), Inches(5.2))
    tb_p2 = s2.shapes.add_textbox(Inches(7.2), Inches(1.8), Inches(5.0), Inches(4.6))
    tf_p2 = tb_p2.text_frame
    tf_p2.word_wrap = True

    p = tf_p2.paragraphs[0]
    p.text = "iTANTRA SEMANTIC APPROACH"
    p.font.size = Pt(14)
    p.font.bold = True
    p.font.color.rgb = COLOR_GREEN
    p.space_after = Pt(14)

    p = tf_p2.add_paragraph()
    p.text = "• Meaning Extracted at the Edge:\n  Audio is converted locally into semantic text metadata, eliminating the need to stream heavy audio waveforms over the air."
    p.font.size = Pt(13)
    p.font.color.rgb = COLOR_WHITE
    p.space_after = Pt(12)

    p = tf_p2.add_paragraph()
    p.text = "• Ultra-Compact Wire Framing:\n  Transmits a structured 29-byte binary SpeechMessage frame for an utterance like 'Hello', enabling delivery over narrow links."
    p.font.size = Pt(13)
    p.font.color.rgb = COLOR_WHITE
    p.space_after = Pt(12)

    p = tf_p2.add_paragraph()
    p.text = "• 100% Cloud-Free Architecture:\n  Complete speech transcription and neural synthesis execute on-device without internet access."
    p.font.size = Pt(13)
    p.font.color.rgb = COLOR_WHITE

    set_notes(s2, """PRESENTER NOTES (Slide 2):
1. What to say:
"In tactical, emergency, or remote environments, communication links have very low bandwidth and zero internet access. Streaming raw uncompressed audio consumes ~256 kbps, rapidly congesting channels. Meanwhile, standard voice AI solutions depend on cloud APIs that fail completely when the internet is cut off."

2. Evidence supporting it:
Audio engineering facts (16kHz 16-bit PCM = 256 kbps), absence of cloud connectivity in disconnected operational areas.

3. Likely judge question:
"Why not just use standard Opus or AMR audio codecs?"

4. Safe answer:
"Standard codecs like Opus still transmit compressed audio waveforms, requiring several kilobits per second. By transmitting the semantic text representation, iTantra reduces the wire payload for an utterance like 'Hello' to just 29 bytes, enabling voice delivery over ultra-narrowband channels."

5. Claim to avoid:
Do not invent a specific numerical percentage reduction like "99.9% universal savings." """)

    # =========================================================================
    # SLIDE 3: Proposed Solution
    # =========================================================================
    s3 = prs.slides.add_slide(blank_layout)
    set_slide_background(s3)
    add_header(s3, "Proposed Solution: End-to-End Edge AI Pipeline")

    # Step 1: Transmitter Box
    add_card(s3, Inches(0.8), Inches(1.5), Inches(3.6), Inches(4.5))
    tb_s1 = s3.shapes.add_textbox(Inches(1.0), Inches(1.7), Inches(3.2), Inches(4.1))
    tf_s1 = tb_s1.text_frame
    tf_s1.word_wrap = True
    p = tf_s1.paragraphs[0]
    p.text = "1. TRANSMITTER (Edge AI)"
    p.font.size = Pt(13)
    p.font.bold = True
    p.font.color.rgb = COLOR_ACCENT
    p.space_after = Pt(10)

    p = tf_s1.add_paragraph()
    p.text = "• Microphone Audio Ingestion\n  (16kHz 16-bit Mono Linear PCM)\n\n• Silero VAD v5 Frame Gating\n  (512-sample / 32ms windowing)\n\n• Sherpa-ONNX Whisper Tiny\n  (Quantized INT8 ASR engine)\n\n• Semantic SpeechMessage Packaging\n  (Seq, Timestamp, Lang, Text)"
    p.font.size = Pt(12)
    p.font.color.rgb = COLOR_WHITE

    # Step 2: Transport Box
    add_card(s3, Inches(4.8), Inches(1.5), Inches(3.6), Inches(4.5))
    tb_s2 = s3.shapes.add_textbox(Inches(5.0), Inches(1.7), Inches(3.2), Inches(4.1))
    tf_s2 = tb_s2.text_frame
    tf_s2.word_wrap = True
    p = tf_s2.paragraphs[0]
    p.text = "2. LOCAL TRANSPORT"
    p.font.size = Pt(13)
    p.font.bold = True
    p.font.color.rgb = COLOR_AMBER
    p.space_after = Pt(10)

    p = tf_s2.add_paragraph()
    p.text = "• Point-to-Point Local Socket\n  (TCP port 42173 / Local Wi-Fi)\n\n• 4-byte Magic Header ('ITRA')\n  (Strict packet framing)\n\n• 29-byte Wire Frame ('Hello')\n  (Ultra-low transport payload)\n\n• 32-bit CRC Integrity Check\n  (IEEE 802.3 frame validation)"
    p.font.size = Pt(12)
    p.font.color.rgb = COLOR_WHITE

    # Step 3: Receiver Box
    add_card(s3, Inches(8.8), Inches(1.5), Inches(3.6), Inches(4.5))
    tb_s3 = s3.shapes.add_textbox(Inches(9.0), Inches(1.7), Inches(3.2), Inches(4.1))
    tf_s3 = tb_s3.text_frame
    tf_s3.word_wrap = True
    p = tf_s3.paragraphs[0]
    p.text = "3. RECEIVER (Edge AI)"
    p.font.size = Pt(13)
    p.font.bold = True
    p.font.color.rgb = COLOR_GREEN
    p.space_after = Pt(10)

    p = tf_s3.add_paragraph()
    p.text = "• Frame Ingestion & Validation\n  (CRC32 check & decode)\n\n• Language Routing Engine\n  (Selects English or Hindi)\n\n• Piper VITS Neural Synthesis\n  (High-fidelity acoustic model)\n\n• Android AudioTrack Playback\n  (16kHz linear PCM to speaker)"
    p.font.size = Pt(12)
    p.font.color.rgb = COLOR_WHITE

    # Bottom Boundary Banner
    add_card(s3, Inches(0.8), Inches(6.2), Inches(11.6), Inches(0.7), bg_color=RGBColor(24, 33, 47))
    tb_sb = s3.shapes.add_textbox(Inches(1.0), Inches(6.3), Inches(11.2), Inches(0.5))
    tf_sb = tb_sb.text_frame
    p = tf_sb.paragraphs[0]
    p.text = "ARCHITECTURAL BOUNDARY: 100% On-Device Neural Processing  |  Zero Cloud APIs  |  Local Peer Transport Only"
    p.font.size = Pt(12)
    p.font.bold = True
    p.font.color.rgb = COLOR_ACCENT

    set_notes(s3, """PRESENTER NOTES (Slide 3):
1. What to say:
"Our architecture divides the pipeline into three stages: on the transmitter, audio is captured, gated with Silero VAD, and converted to text using an on-device Whisper Tiny model. This is packaged into a compact SpeechMessage and sent over local TCP. On the receiver, the frame is validated and synthesized into natural speech via Piper neural TTS. All AI inference is 100% offline."

2. Evidence supporting it:
M1, M2, M3 source code and verified execution traces in scratch/M1-FINAL-EVIDENCE-FREEZE-01.md.

3. Likely judge question:
"Does the network carry any audio data at all?"

4. Safe answer:
"No. The network medium carries only the 29-byte binary SpeechMessage frame containing the text and metadata. All audio waveform generation happens locally on the receiving device."

5. Claim to avoid:
Do not state that the system operates with "zero network" (local TCP/Wi-Fi is still used). """)

    # =========================================================================
    # SLIDE 4: Innovation / Technical Approach
    # =========================================================================
    s4 = prs.slides.add_slide(blank_layout)
    set_slide_background(s4)
    add_header(s4, "Innovation: Edge-Native Neural Architecture")

    # 4 Quadrant Cards
    # Top Left: Edge AI
    add_card(s4, Inches(0.8), Inches(1.5), Inches(5.6), Inches(2.45))
    tb_i1 = s4.shapes.add_textbox(Inches(1.0), Inches(1.65), Inches(5.2), Inches(2.1))
    tf_i1 = tb_i1.text_frame
    tf_i1.word_wrap = True
    p = tf_i1.paragraphs[0]
    p.text = "100% ON-DEVICE AI EXECUTION"
    p.font.size = Pt(13)
    p.font.bold = True
    p.font.color.rgb = COLOR_ACCENT
    p.space_after = Pt(6)
    p = tf_i1.add_paragraph()
    p.text = "• Bundled native C++ JNI binaries (libsherpa-onnx-jni.so)\n• Hardware-accelerated ONNX Runtime Mobile\n• Zero cloud dependencies, zero external runtime downloads"
    p.font.size = Pt(12)
    p.font.color.rgb = COLOR_WHITE

    # Top Right: Model Optimization
    add_card(s4, Inches(6.8), Inches(1.5), Inches(5.6), Inches(2.45))
    tb_i2 = s4.shapes.add_textbox(Inches(7.0), Inches(1.65), Inches(5.2), Inches(2.1))
    tf_i2 = tb_i2.text_frame
    tf_i2.word_wrap = True
    p = tf_i2.paragraphs[0]
    p.text = "EDGE-OPTIMIZED NEURAL MODELS"
    p.font.size = Pt(13)
    p.font.bold = True
    p.font.color.rgb = COLOR_GREEN
    p.space_after = Pt(6)
    p = tf_i2.add_paragraph()
    p.text = "• Silero VAD v5: 512-sample neural voice activity detector\n• Whisper Tiny INT8: Quantized ASR (encoder + decoder = 98 MB)\n• Piper VITS: Neural acoustic synthesis for English & Hindi"
    p.font.size = Pt(12)
    p.font.color.rgb = COLOR_WHITE

    # Bottom Left: Binary Framing
    add_card(s4, Inches(0.8), Inches(4.2), Inches(5.6), Inches(2.45))
    tb_i3 = s4.shapes.add_textbox(Inches(1.0), Inches(4.35), Inches(5.2), Inches(2.1))
    tf_i3 = tb_i3.text_frame
    tf_i3.word_wrap = True
    p = tf_i3.paragraphs[0]
    p.text = "CUSTOM BINARY WIRE FRAMING"
    p.font.size = Pt(13)
    p.font.bold = True
    p.font.color.rgb = COLOR_AMBER
    p.space_after = Pt(6)
    p = tf_i3.add_paragraph()
    p.text = "• 4-byte Magic 'ITRA' + Version + 8-byte Epoch Timestamp\n• 4-byte Sequence Number + Language Code + UTF-8 Payload\n• 4-byte IEEE 802.3 CRC32 integrity trailer (29 bytes for 'Hello')"
    p.font.size = Pt(12)
    p.font.color.rgb = COLOR_WHITE

    # Bottom Right: Android Architecture
    add_card(s4, Inches(6.8), Inches(4.2), Inches(5.6), Inches(2.45))
    tb_i4 = s4.shapes.add_textbox(Inches(7.0), Inches(4.35), Inches(5.2), Inches(2.1))
    tf_i4 = tb_i4.text_frame
    tf_i4.word_wrap = True
    p = tf_i4.paragraphs[0]
    p.text = "NATIVE ANDROID ENGINEERING"
    p.font.size = Pt(13)
    p.font.bold = True
    p.font.color.rgb = COLOR_HIGHLIGHT
    p.space_after = Pt(6)
    p = tf_i4.add_paragraph()
    p.text = "• Kotlin-first design with Coroutines & StateFlow state machines\n• Android Jetpack Architecture across API 26 through API 35\n• Non-blocking socket lifecycles with graceful reconnect"
    p.font.size = Pt(12)
    p.font.color.rgb = COLOR_WHITE

    set_notes(s4, """PRESENTER NOTES (Slide 4):
1. What to say:
"The technical core of iTantra combines quantized 8-bit Whisper Tiny models and Piper VITS neural acoustic synthesis running locally via native C++ ONNX runtimes. We developed a custom binary wire protocol that encapsulates sequence number, timestamp, language, and text, protected by a 32-bit CRC checksum to ensure data integrity before synthesis."

2. Evidence supporting it:
Asset checksums in app/src/main/assets, native JNI bindings in libsherpa-onnx-jni.so, unit tests in PacketFramerTest.

3. Likely judge question:
"How did you fit large neural models onto a mobile device?"

4. Safe answer:
"We utilized 8-bit integer quantization (INT8) for the Whisper encoder and decoder, reducing the combined ASR model size to ~98 MB. Our complete standalone APK, including ASR, VAD, English TTS, and Hindi TTS, is self-contained at 315 MB."

5. Claim to avoid:
Do not claim information-theoretic source coding breakthroughs or custom neural architectures that were not trained from scratch. """)

    # =========================================================================
    # SLIDE 5: Architecture & Interfaces
    # =========================================================================
    s5 = prs.slides.add_slide(blank_layout)
    set_slide_background(s5)
    add_header(s5, "Architecture: Frozen Module Interfaces")

    # 3 Module Horizontal Cards
    modules = [
        ("MODULE 1 (M1) — INGESTION & ASR", COLOR_ACCENT,
         "• Input: 16kHz 16-bit Mono Linear PCM\n• Preprocessing: Silero VAD v5 (32ms frames)\n• Speech Recognition: Whisper Tiny INT8\n• Output: SpeechMessage Data Class",
         Inches(1.5)),
        ("MODULE 2 (M2) — TRANSPORT PROTOCOL", COLOR_AMBER,
         "• Input: SpeechMessage Object\n• Serialization: Binary Header + CRC32 Trailer\n• Transport: Point-to-Point TCP Socket (Port 42173)\n• Wire Size: 29 bytes for 'Hello' (Magic: 'ITRA')",
         Inches(3.25)),
        ("MODULE 3 (M3) — ACOUSTIC SYNTHESIS", COLOR_GREEN,
         "• Input: Validated SpeechMessage\n• Routing: English ('lessac') / Hindi ('rohan')\n• Synthesis: Piper VITS Neural Engine\n• Playback: Android AudioTrack (16kHz PCM)",
         Inches(5.0))
    ]

    for title, color, desc, top_pos in modules:
        add_card(s5, Inches(0.8), top_pos, Inches(11.7), Inches(1.5))
        tb_m = s5.shapes.add_textbox(Inches(1.0), top_pos + Inches(0.1), Inches(11.3), Inches(1.3))
        tf_m = tb_m.text_frame
        tf_m.word_wrap = True
        p = tf_m.paragraphs[0]
        p.text = title
        p.font.size = Pt(13)
        p.font.bold = True
        p.font.color.rgb = color
        p.space_after = Pt(4)
        p = tf_m.add_paragraph()
        p.text = desc
        p.font.size = Pt(11)
        p.font.color.rgb = COLOR_WHITE

    # Bottom Interface Box
    add_card(s5, Inches(0.8), Inches(6.65), Inches(11.7), Inches(0.55), bg_color=RGBColor(24, 33, 47))
    tb_int = s5.shapes.add_textbox(Inches(1.0), Inches(6.68), Inches(11.3), Inches(0.45))
    tf_int = tb_int.text_frame
    p = tf_int.paragraphs[0]
    p.text = "FROZEN DATA CONTRACT: SpeechMessage(sequenceNumber: Int, timestamp: Long, language: String, text: String)"
    p.font.size = Pt(11)
    p.font.bold = True
    p.font.color.rgb = COLOR_HIGHLIGHT

    set_notes(s5, """PRESENTER NOTES (Slide 5):
1. What to say:
"The codebase is organized into three strictly decoupled modules: Module 1 handles audio ingestion and STT, Module 2 manages binary serialization and socket transport, and Module 3 handles decoding, neural TTS synthesis, and AudioTrack playback. This modularity ensures that transport and AI engines can be updated independently without breaking the SpeechMessage contract."

2. Evidence supporting it:
Kotlin data class SpeechMessage, M2ProtocolTest, RawSocketClient, RawSocketServer.

3. Likely judge question:
"Can you swap TCP for another transport like LoRa or UHF radio?"

4. Safe answer:
"Yes. Because Module 2 outputs a self-contained 29-byte binary packet with its own Magic header and CRC32, the transport layer can be adapted to serial radio, LoRa, or Bluetooth without modifying the M1 or M3 AI modules."

5. Claim to avoid:
Do not claim that LoRa or RF hardware is already implemented and physically tested in this APK. """)

    # =========================================================================
    # SLIDE 6: Working Prototype / Physical Evidence
    # =========================================================================
    s6 = prs.slides.add_slide(blank_layout)
    set_slide_background(s6)
    add_header(s6, "Working Prototype: Physical Hardware Validation")

    # Left: Hardware Setup
    add_card(s6, Inches(0.8), Inches(1.5), Inches(4.5), Inches(5.4))
    tb_hw = s6.shapes.add_textbox(Inches(1.0), Inches(1.7), Inches(4.1), Inches(5.0))
    tf_hw = tb_hw.text_frame
    tf_hw.word_wrap = True

    p = tf_hw.paragraphs[0]
    p.text = "CANONICAL DEMO TOPOLOGY"
    p.font.size = Pt(13)
    p.font.bold = True
    p.font.color.rgb = COLOR_ACCENT
    p.space_after = Pt(10)

    p = tf_hw.add_paragraph()
    p.text = "• TRANSMITTER:\n  Lenovo Smart Tab M10 FHD Plus\n  Serial: HNP097HQ | Android 12\n  SoC: MediaTek Helio P22T (MT8768T)\n  IP: 172.20.161.69\n\n• NETWORK MEDIUM:\n  BOYS AP-2 Campus Wi-Fi\n  TCP Port: 42173\n\n• RECEIVER:\n  iQOO Z9 5G (I2304)\n  Serial: 10BE830QG8000MX | Android 16\n  SoC: Snapdragon 7 Gen 3\n  IP: 172.20.29.81"
    p.font.size = Pt(12)
    p.font.color.rgb = COLOR_WHITE

    # Right: Empirical Execution Timeline
    add_card(s6, Inches(5.6), Inches(1.5), Inches(6.9), Inches(5.4))
    tb_time = s6.shapes.add_textbox(Inches(5.8), Inches(1.7), Inches(6.5), Inches(5.0))
    tf_time = tb_time.text_frame
    tf_time.word_wrap = True

    p = tf_time.paragraphs[0]
    p.text = "EMPIRICAL EXECUTION TIMELINE (Physical Test Run)"
    p.font.size = Pt(13)
    p.font.bold = True
    p.font.color.rgb = COLOR_GREEN
    p.space_after = Pt(10)

    p = tf_time.add_paragraph()
    p.text = "[0 ms]  Tablet Mic Capture: 14,400 samples (900 ms audio) 'Hello'\n\n[900 ms]  Tablet M1 Whisper Tiny STT Inference Starts\n\n[2,032 ms]  Tablet M1 STT Output: 'Hello' (Duration: 1,132 ms | RTF: 1.2578)\n\n[2,035 ms]  Tablet M2 Wire Send: 29-byte SpeechMessage frame sent over TCP\n\n[2,048 ms]  Phone M2 Wire Receive: Frame decoded & CRC32 validated\n\n[2,050 ms]  Phone M3 Piper VITS TTS Starts ('en_US-lessac-low')\n\n[2,246 ms]  Phone M3 TTS Output: 7,424 samples (464 ms) in 196 ms (RTF: 0.422)\n\n[2,250 ms]  Phone AudioTrack Starts: Inbound playback to device speaker\n\n[2,730 ms]  Phone Playback Finished: Completed in 480 ms successfully"
    p.font.size = Pt(11)
    p.font.color.rgb = COLOR_WHITE

    set_notes(s6, """PRESENTER NOTES (Slide 6):
1. What to say:
"We validated the entire pipeline on physical hardware over the BOYS AP-2 campus Wi-Fi network. Spoken input from the tablet microphone was transcribed on-device in 1,132 ms (RTF 1.25), transmitted as a 29-byte frame over TCP 42173, synthesized on the phone in 196 ms (RTF 0.42), and played through the phone speaker in 480 ms."

2. Evidence supporting it:
Device logs and hardware serial numbers (HNP097HQ, 10BE830QG8000MX) in scratch/M1-FINAL-EVIDENCE-FREEZE-01.md.

3. Likely judge question:
"Why is STT inference faster on the phone than on the tablet?"

4. Safe answer:
"The tablet uses an entry-level MediaTek Helio P22T octa-core Cortex-A53 processor, achieving an RTF of 1.25, while the phone features a modern Qualcomm Snapdragon 7 Gen 3 processor with higher IPC and vector instructions."

5. Claim to avoid:
Do not state that 1,132 ms is a universal latency guarantee across all possible sentences. """)

    # =========================================================================
    # SLIDE 7: Quantitative Evidence
    # =========================================================================
    s7 = prs.slides.add_slide(blank_layout)
    set_slide_background(s7)
    add_header(s7, "Quantitative Evidence: Measured Metrics & Assets")

    # Table Shape
    table_shape = s7.shapes.add_table(13, 4, Inches(0.8), Inches(1.4), Inches(11.7), Inches(5.6))
    table = table_shape.table
    table.columns[0].width = Inches(3.2)
    table.columns[1].width = Inches(2.7)
    table.columns[2].width = Inches(3.3)
    table.columns[3].width = Inches(2.5)

    headers = ["Metric / Parameter", "Measured Value", "Target / Hardware Context", "Evidence Source"]
    for i, h in enumerate(headers):
        cell = table.cell(0, i)
        cell.fill.solid()
        cell.fill.fore_color.rgb = RGBColor(24, 33, 47)
        p = cell.text_frame.paragraphs[0]
        p.text = h
        p.font.size = Pt(11)
        p.font.bold = True
        p.font.color.rgb = COLOR_ACCENT

    rows_data = [
        ("Automated Unit Tests", "408 / 408 Passed", "100% Pass Rate (0 fail, 0 skip)", ":app:testDebugUnitTest"),
        ("Production APK Size", "315,427,692 bytes", "Standalone Release Build", "Disk SHA-256 Verified"),
        ("Live Audio Capture", "14,400 samples (900 ms)", "16kHz Mono (Tablet Mic)", "[PTT_RECORDING] Logcat"),
        ("On-Device STT Inference", "1,132 ms (RTF: 1.2578)", "MediaTek Helio P22T Octa-core", "[M1-METRICS] Logcat"),
        ("Transmitted Frame Size", "29 bytes ('Hello')", "Binary Wire Packet", "[M2_SEND] Logcat"),
        ("On-Device TTS Synthesis", "196 ms (RTF: 0.422)", "Qualcomm Snapdragon 7 Gen 3", "[M3-TTS-COMPLETE] Logcat"),
        ("Synthesized Audio Length", "464 ms (7,424 samples)", "16kHz Linear PCM Audio", "[M3-TTS-COMPLETE] Logcat"),
        ("AudioTrack Playback Time", "480 ms", "Device Speaker Output", "[E2E-B-PLAYBACK] Logcat"),
        ("Silero VAD Model Size", "2,327,524 bytes", "Bundled Neural Asset", "SHA-256: 1a153a..."),
        ("Whisper INT8 Total Size", "102,793,173 bytes", "Encoder + Decoder ONNX Assets", "SHA-256 Verified"),
        ("English Piper TTS Model", "63,149,198 bytes", "en_US-lessac-low.onnx", "SHA-256: 817773..."),
        ("Hindi Piper TTS Model", "62,946,428 bytes", "hi_IN-rohan-medium.onnx", "SHA-256: 353d2a...")
    ]

    for row_idx, data in enumerate(rows_data, start=1):
        for col_idx, text in enumerate(data):
            cell = table.cell(row_idx, col_idx)
            cell.fill.solid()
            cell.fill.fore_color.rgb = COLOR_CARD if row_idx % 2 == 0 else RGBColor(22, 32, 48)
            p = cell.text_frame.paragraphs[0]
            p.text = text
            p.font.size = Pt(10)
            p.font.color.rgb = COLOR_WHITE if col_idx < 2 else COLOR_MUTED

    set_notes(s7, """PRESENTER NOTES (Slide 7):
1. What to say:
"Every metric presented here is backed by automated tests and physical measurements: 408 out of 408 automated unit tests pass; the complete standalone APK is 315 MB; the transmission packet is exactly 29 bytes for 'Hello'; and neural TTS generates 464 ms of speech in 196 ms."

2. Evidence supporting it:
Gradle :app:testDebugUnitTest results, physical asset SHA-256 hashes, Logcat metric tags.

3. Likely judge question:
"What is your Word Error Rate (WER) across diverse accents?"

4. Safe answer:
"While we conducted controlled historical diagnostic benchmarks on static test files, live-microphone WER across arbitrary live accents has not been statistically profiled and varies with acoustic conditions."

5. Claim to avoid:
Do not present historical static file benchmarks as live-microphone WER numbers. """)

    # =========================================================================
    # SLIDE 8: Limitations / Engineering Honesty
    # =========================================================================
    s8 = prs.slides.add_slide(blank_layout)
    set_slide_background(s8)
    add_header(s8, "Current Scope & Documented Limitations")

    # 6 Limitation Cards (2 cols x 3 rows)
    limits = [
        ("1. Live Microphone Acoustic Sensitivity", "Whisper Tiny accuracy varies based on ambient noise, microphone clipping, speaker accent, and utterance length."),
        ("2. Enterprise AP Asymmetric Routing", "Under tested BOYS AP-2 conditions, cross-BSSID routing exhibits asymmetric reachability due to AP client isolation."),
        ("3. Manual Peer IP Configuration", "The current UI requires manual entry of the receiver's IP address (mDNS automated discovery is not yet active)."),
        ("4. Hardware Processing Disparity", "STT inference duration scales with chipset capability (RTF 1.25 on Helio P22T vs < 0.3 on flagship SoCs)."),
        ("5. CRC32 Integrity vs Encryption", "The protocol implements a 32-bit CRC32 checksum for frame integrity validation, not cryptographic encryption."),
        ("6. Unmeasured Continuous Metrics", "Aggregate compression percentages across continuous speech and statistical PDR under RF fading remain unmeasured.")
    ]

    for idx, (title, desc) in enumerate(limits):
        col = idx % 2
        row = idx // 2
        left = Inches(0.8 + col * 6.0)
        top = Inches(1.5 + row * 1.8)

        add_card(s8, left, top, Inches(5.7), Inches(1.65))
        tb_l = s8.shapes.add_textbox(left + Inches(0.2), top + Inches(0.15), Inches(5.3), Inches(1.35))
        tf_l = tb_l.text_frame
        tf_l.word_wrap = True

        p = tf_l.paragraphs[0]
        p.text = title
        p.font.size = Pt(12)
        p.font.bold = True
        p.font.color.rgb = COLOR_AMBER
        p.space_after = Pt(4)

        p = tf_l.add_paragraph()
        p.text = desc
        p.font.size = Pt(11)
        p.font.color.rgb = COLOR_WHITE

    set_notes(s8, """PRESENTER NOTES (Slide 8):
1. What to say:
"In the spirit of engineering honesty, we highlight our current limitations: live-microphone accuracy varies in noisy environments; campus networks with multiple APs exhibit asymmetric routing due to client isolation; peer IP configuration is currently manual; and the protocol currently uses CRC32 for data integrity rather than cryptographic encryption."

2. Evidence supporting it:
Section 4 of scratch/M1-JUDGE-EVIDENCE-MATRIX-01.md, network diagnostic traces.

3. Likely judge question:
"Why did Phone to Tablet TCP time out on the campus network?"

4. Safe answer:
"Under the tested BOYS AP-2 conditions, the devices connected to different BSSIDs on separate subnets. While Tablet to Phone traffic was routed, the reverse path was dropped by campus AP client isolation. On a direct hotspot subnet, bidirectional connectivity was fully symmetric."

5. Claim to avoid:
Do not blame a "broken router" or claim unproven internal enterprise firewall mechanisms. """)

    # =========================================================================
    # SLIDE 9: Live Demo Sequence & Fallback
    # =========================================================================
    s9 = prs.slides.add_slide(blank_layout)
    set_slide_background(s9)
    add_header(s9, "Live Demonstration Sequence & Protocol")

    # Left: 8-Step Demo Flow
    add_card(s9, Inches(0.8), Inches(1.5), Inches(6.8), Inches(5.4))
    tb_d = s9.shapes.add_textbox(Inches(1.0), Inches(1.7), Inches(6.4), Inches(5.0))
    tf_d = tb_d.text_frame
    tf_d.word_wrap = True

    p = tf_d.paragraphs[0]
    p.text = "30–60 SECOND LIVE DEMONSTRATION STEPS"
    p.font.size = Pt(13)
    p.font.bold = True
    p.font.color.rgb = COLOR_ACCENT
    p.space_after = Pt(8)

    steps = [
        "1. Present Tablet as TRANSMITTER (IP: 172.20.161.69)",
        "2. Present Phone as RECEIVER (IP: 172.20.29.81, Port 42173)",
        "3. Connect TCP socket and confirm 'Connected' status",
        "4. Press PTT on Tablet and speak clearly: 'Hello'",
        "5. Observe live on-device STT transcript on Tablet screen",
        "6. Observe 29-byte binary packet reception on Phone screen",
        "7. Play live Piper VITS neural speech output from Phone speaker",
        "8. Confirm zero internet data packets were routed to the cloud"
    ]
    for step in steps:
        p = tf_d.add_paragraph()
        p.text = step
        p.font.size = Pt(11)
        p.font.color.rgb = COLOR_WHITE
        p.space_after = Pt(4)

    # Right: Fallback Protocol
    add_card(s9, Inches(7.9), Inches(1.5), Inches(4.6), Inches(5.4), bg_color=RGBColor(28, 25, 40), border_color=COLOR_AMBER)
    tb_fb = s9.shapes.add_textbox(Inches(8.1), Inches(1.7), Inches(4.2), Inches(5.0))
    tf_fb = tb_fb.text_frame
    tf_fb.word_wrap = True

    p = tf_fb.paragraphs[0]
    p.text = "LIVE FALLBACK PROTOCOL"
    p.font.size = Pt(13)
    p.font.bold = True
    p.font.color.rgb = COLOR_AMBER
    p.space_after = Pt(10)

    p = tf_fb.add_paragraph()
    p.text = "• Strict Anti-Faking Policy:\n  If ambient conference noise causes STT misrecognition, the presenter will NOT fake a successful transcript or manually type text."
    p.font.size = Pt(12)
    p.font.color.rgb = COLOR_WHITE
    p.space_after = Pt(12)

    p = tf_fb.add_paragraph()
    p.text = "• Transparent Fallback Procedure:\n  1. Explain the live microphone acoustic sensitivity limitation honestly.\n  2. Immediately display the verified physical logcat trace from the freeze report.\n  3. Demonstrate the verified offline Piper TTS synthesis independently."
    p.font.size = Pt(12)
    p.font.color.rgb = COLOR_MUTED

    set_notes(s9, """PRESENTER NOTES (Slide 9):
1. What to say:
"We will now demonstrate the physical prototype live. The tablet is in Transmitter mode and connected to the phone in Receiver mode over local Wi-Fi. I will speak 'Hello' into the tablet; you will observe the on-device transcription, the transmission of the 29-byte packet, and the immediate neural synthesis and playback on the phone."

2. Evidence supporting it:
Step-by-step reproduction scripts in scratch/.

3. Likely judge question:
"What happens if the spoken phrase is long or complex?"

4. Safe answer:
"The VAD detects the full speech segment and Whisper Tiny processes the entire buffer. The resulting transcript is transmitted as a single text frame, which Piper synthesizes sentence by sentence."

5. Claim to avoid:
If live recognition mishears an utterance due to ambient noise, do not claim it succeeded. Acknowledge the acoustic limitation and show the verified freeze log. """)

    # =========================================================================
    # SLIDE 10: Future Scope & Closing
    # =========================================================================
    s10 = prs.slides.add_slide(blank_layout)
    set_slide_background(s10)
    add_header(s10, "Conclusion & Engineering Roadmap")

    # Left: Currently Demonstrated
    add_card(s10, Inches(0.8), Inches(1.5), Inches(5.6), Inches(4.5))
    tb_c1 = s10.shapes.add_textbox(Inches(1.0), Inches(1.7), Inches(5.2), Inches(4.1))
    tf_c1 = tb_c1.text_frame
    tf_c1.word_wrap = True

    p = tf_c1.paragraphs[0]
    p.text = "CURRENTLY DEMONSTRATED (Production Freeze)"
    p.font.size = Pt(13)
    p.font.bold = True
    p.font.color.rgb = COLOR_GREEN
    p.space_after = Pt(10)

    p = tf_c1.add_paragraph()
    p.text = "• Complete on-device neural voice transceiver\n  (Silero VAD + Whisper Tiny INT8 + Piper VITS)\n\n• Standalone 315 MB APK with zero cloud dependencies\n\n• Point-to-point TCP framing with 4-byte CRC32 check\n\n• English ('lessac') & Hindi ('rohan') neural synthesis\n\n• 408 / 408 passing automated unit tests"
    p.font.size = Pt(12)
    p.font.color.rgb = COLOR_WHITE

    # Right: Future Scope
    add_card(s10, Inches(6.8), Inches(1.5), Inches(5.6), Inches(4.5))
    tb_c2 = s10.shapes.add_textbox(Inches(7.0), Inches(1.7), Inches(5.2), Inches(4.1))
    tf_c2 = tb_c2.text_frame
    tf_c2.word_wrap = True

    p = tf_c2.paragraphs[0]
    p.text = "FUTURE ENGINEERING ROADMAP"
    p.font.size = Pt(13)
    p.font.bold = True
    p.font.color.rgb = COLOR_HIGHLIGHT
    p.space_after = Pt(10)

    p = tf_c2.add_paragraph()
    p.text = "• Ad-Hoc Mesh Networking:\n  Automated zero-conf peer discovery via 802.11s Wi-Fi mesh\n\n• Expanded Regional Language Coverage:\n  Exporting Piper VITS models for Tamil, Telugu, and Bengali\n\n• Acoustic Noise Suppression:\n  Integrating real-time DSP/RNNoise for rugged environments\n\n• Authenticated Encryption:\n  Upgrading framing with ChaCha20-Poly1305 / AES-GCM"
    p.font.size = Pt(12)
    p.font.color.rgb = COLOR_WHITE

    # Bottom Closing Banner
    add_card(s10, Inches(0.8), Inches(6.2), Inches(11.6), Inches(0.7), bg_color=RGBColor(24, 33, 47))
    tb_cb = s10.shapes.add_textbox(Inches(1.0), Inches(6.3), Inches(11.2), Inches(0.5))
    tf_cb = tb_cb.text_frame
    p = tf_cb.paragraphs[0]
    p.text = "CLOSING POSITION: iTantra demonstrates that edge AI can replace raw audio streaming with low-bitrate semantic voice transmission over local peer networks with zero cloud dependency."
    p.font.size = Pt(11)
    p.font.bold = True
    p.font.color.rgb = COLOR_ACCENT

    set_notes(s10, """PRESENTER NOTES (Slide 10):
1. What to say:
"In conclusion, iTantra successfully proves that edge AI can replace raw audio streaming with low-bitrate semantic voice transmission without cloud reliance. Our future work will focus on integrating Wi-Fi mesh routing, real-time noise suppression, additional Indian languages, and authenticated encryption."

2. Evidence supporting it:
Modular architecture designed for pluggable transport and synthesis backends.

3. Likely judge question:
"How hard is it to add a new Indian language like Tamil or Bengali?"

4. Safe answer:
"Because the Whisper Tiny tokenizer already supports multilingual tokens, adding a new language requires only training/exporting a Piper VITS ONNX model for that language and registering its token map in Module 3."

5. Claim to avoid:
Do not claim that Tamil, Telugu, or Bengali are already functional in the current build. """)

    output_path = "scratch/iTantra-SIH-2026-Final-Presentation.pptx"
    prs.save(output_path)
    print(f"Presentation saved successfully to {output_path}")

if __name__ == "__main__":
    create_presentation()
