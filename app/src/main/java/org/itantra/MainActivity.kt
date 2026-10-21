package org.itantra

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.content.res.ColorStateList
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.itantra.core.audio.AudioCaptureConfig
import org.itantra.core.communication.connection.bindTo
import org.itantra.core.composition.AndroidTransceiverComposition
import org.itantra.core.coordinator.TransceiverError
import org.itantra.core.coordinator.TransceiverListener
import org.itantra.core.coordinator.TransceiverState
import org.itantra.core.domain.SpeechMessage
import org.itantra.core.stt.AsrSmokeTestRunner
import org.itantra.core.tts.TtsAudio
import org.itantra.core.vad.VadConfig
import org.itantra.ui.TransceiverUiFormatter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android Activity Control Surface for the iTantra tactical speech transceiver.
 *
 * Owns a single [AndroidTransceiverComposition] instance governing M1 (Capture & STT),
 * M2 (Transport & Adapter), and M3 (TTS & AudioTrack Playback) through the top-level
 * [org.itantra.core.coordinator.TransceiverCoordinator].
 */
class MainActivity : AppCompatActivity() {

    private val audioConfig = AudioCaptureConfig()
    private var vadConfig = VadConfig()

    private var composition: AndroidTransceiverComposition? = null
    private val isInitializing = AtomicBoolean(false)
    private val compositionGeneration = java.util.concurrent.atomic.AtomicInteger(0)

    enum class NetworkRole { SERVER, CLIENT }
    private var selectedRole: NetworkRole = NetworkRole.SERVER
    private var peerHost: String = ""

    enum class ThemeMode { SYSTEM, LIGHT, DARK }
    private var currentThemeMode: ThemeMode = ThemeMode.SYSTEM

    val stateHolder = org.itantra.ui.TransceiverUiStateHolder()

    private lateinit var tvStatus: TextView
    private lateinit var tvConnectionState: TextView
    private lateinit var viewConnectionDot: View
    private lateinit var btnThemeToggle: Button
    private lateinit var btnModeServer: Button
    private lateinit var btnModeClient: Button
    private lateinit var layoutClientIpRow: View
    private lateinit var etPeerIp: android.widget.EditText
    private lateinit var btnConnectPeer: Button
    private lateinit var tvServerRoleHint: TextView
    private lateinit var btnLangEn: Button
    private lateinit var btnLangHi: Button
    private lateinit var tvYouTranscript: TextView
    private lateinit var tvYouLangBadge: TextView
    private lateinit var tvRemoteTranscript: TextView
    private lateinit var tvRemoteLangBadge: TextView
    private lateinit var tvRemotePlaybackStatus: TextView
    private lateinit var btnPtt: FrameLayout
    private lateinit var tvPttLabel: TextView
    private lateinit var tvLastError: TextView

    // Diagnostic & compatibility views
    private lateinit var tvVadState: TextView
    private lateinit var tvVadDetails: TextView
    private lateinit var tvTelemetryInfo: TextView
    private lateinit var tvAsrTranscript: TextView
    private lateinit var tvAsrMetrics: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnRelease: Button

    private var currentLanguage: String = "en"
    private var isRunning: Boolean = false

    private var outboundMessagesCount = 0
    private var inboundMessagesCount = 0

    private fun showSuccessBanner(message: String) {
        tvLastError.setBackgroundResource(R.drawable.bg_success_banner)
        tvLastError.setTextColor(ContextCompat.getColor(this, R.color.status_green))
        tvLastError.text = message
        tvLastError.visibility = View.VISIBLE
    }

    private fun showErrorBanner(message: String) {
        tvLastError.setBackgroundResource(R.drawable.bg_error_banner)
        tvLastError.setTextColor(ContextCompat.getColor(this, R.color.status_red))
        tvLastError.text = message
        tvLastError.visibility = View.VISIBLE
    }

    private fun hideBanner() {
        tvLastError.visibility = View.GONE
    }

    private fun updateReceiverRoleHint() {
        val ip = TransceiverUiFormatter.getActiveLocalIpv4Address()
        tvServerRoleHint.text = TransceiverUiFormatter.formatReceiverListeningHint(
            ip = ip,
            port = org.itantra.core.communication.transport.TcpTransportConstants.DEFAULT_PORT
        )
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            hideBanner()
            startTransceiver()
        } else {
            tvStatus.text = "PERMISSION DENIED"
            showErrorBanner("Microphone permission is required for audio capture. Please grant permission in App Settings.")
            updateButtonStates(isRunning = false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val savedTheme = getSharedPreferences("app_theme_prefs", MODE_PRIVATE)
            .getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        currentThemeMode = try {
            ThemeMode.valueOf(savedTheme)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
        val targetNightMode = when (currentThemeMode) {
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        if (AppCompatDelegate.getDefaultNightMode() != targetNightMode) {
            AppCompatDelegate.setDefaultNightMode(targetNightMode)
        }

        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        updateButtonStates(isRunning = false)
        updateReceiverRoleHint()

        handleSmokeTestIntent(intent)
    }

    private fun applyThemeMode(mode: ThemeMode, persist: Boolean = true) {
        currentThemeMode = mode
        if (persist) {
            getSharedPreferences("app_theme_prefs", MODE_PRIVATE)
                .edit()
                .putString("theme_mode", mode.name)
                .apply()
        }
        val nightMode = when (mode) {
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
        updateThemeToggleButton()
    }

    private fun updateThemeToggleButton() {
        if (!::btnThemeToggle.isInitialized) return
        btnThemeToggle.text = when (currentThemeMode) {
            ThemeMode.SYSTEM -> getString(R.string.theme_system)
            ThemeMode.LIGHT -> getString(R.string.theme_light)
            ThemeMode.DARK -> getString(R.string.theme_dark)
        }
    }

    override fun onResume() {
        super.onResume()
        if (selectedRole == NetworkRole.SERVER) {
            updateReceiverRoleHint()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSmokeTestIntent(intent)
    }

    private fun handleSmokeTestIntent(intent: Intent?) {
        if (intent?.hasExtra("minSilenceDurationMs") == true) {
            val silenceMs = intent.getIntExtra("minSilenceDurationMs", 400)
            Log.i("MainActivity", "[EXPERIMENT] Updating minSilenceDurationMs to $silenceMs ms")
            vadConfig = vadConfig.copy(minSilenceDurationMs = silenceMs)
            val comp = composition
            composition = null
            if (comp != null && !comp.isReleased) {
                CoroutineScope(Dispatchers.IO).launch {
                    comp.release()
                }
            }
        }
        if (intent?.hasExtra("targetLanguage") == true) {
            val lang = intent.getStringExtra("targetLanguage") ?: "en"
            updateLanguageSelection(lang)
        }

        val action = intent?.getStringExtra("action") ?: intent?.action
        when (action) {
            "START_PIPELINE", "org.itantra.action.START_PIPELINE" -> {
                if (hasAudioPermission()) {
                    startTransceiver()
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            "STOP_PIPELINE", "org.itantra.action.STOP_PIPELINE" -> {
                stopTransceiver()
            }
            "RUN_ASR_SMOKE_TEST", "org.itantra.action.RUN_ASR_SMOKE_TEST" -> {
                Thread {
                    val report = AsrSmokeTestRunner.runFullSmokeTest(applicationContext, iterations = 2)
                    runOnUiThread {
                        if (report.errorMessage == null && report.runs.isNotEmpty()) {
                            val run1 = report.runs[0]
                            tvStatus.text = "ASR SMOKE TEST: PASS"
                            val txt = "Run 1: '${run1.transcript}' (${run1.inferenceTimeMs}ms, RTF ${"%.2f".format(run1.rtf)})\n" +
                                    if (report.runs.size > 1) "Run 2: '${report.runs[1].transcript}' (${report.runs[1].inferenceTimeMs}ms, RTF ${"%.2f".format(report.runs[1].rtf)})" else ""
                            showSuccessBanner(txt)
                        } else {
                            tvStatus.text = "ASR SMOKE TEST: FAIL"
                            showErrorBanner("ASR Error: ${report.errorMessage}")
                        }
                    }
                }.start()
            }
            "RUN_ASR_ACCURACY_BENCHMARK", "org.itantra.action.RUN_ASR_ACCURACY_BENCHMARK",
            "RUN_ASR_SUITE_BENCHMARK", "org.itantra.action.RUN_ASR_SUITE_BENCHMARK" -> {
                Thread {
                    kotlinx.coroutines.runBlocking {
                        val report = org.itantra.core.stt.benchmark.AsrAccuracyBenchmarkRunner.runSuite(applicationContext)
                        runOnUiThread {
                            if (report != null) {
                                tvStatus.text = "ASR BENCHMARK: PASS"
                                showSuccessBanner("Suite: ${report.suiteName}\nCases: ${report.totalCases} (Exact: ${report.exactMatchCount})\nCorpus WER: ${"%.2f".format(report.corpusWer * 100)}%\nCorpus CER: ${"%.2f".format(report.corpusCer * 100)}%\nMean RTF: ${"%.2f".format(report.meanRtf)}")
                            } else {
                                tvStatus.text = "ASR BENCHMARK: FAIL"
                                showErrorBanner("ASR benchmark suite failed")
                            }
                        }
                    }
                }.start()
            }
            "RUN_ASR_HINDI_BENCHMARK", "org.itantra.action.RUN_ASR_HINDI_BENCHMARK" -> {
                Thread {
                    kotlinx.coroutines.runBlocking {
                        val report = org.itantra.core.stt.benchmark.AsrAccuracyBenchmarkRunner.runIndicSuite(applicationContext)
                        runOnUiThread {
                            if (report != null) {
                                tvStatus.text = "HINDI BENCHMARK: DONE"
                                showSuccessBanner("EXPERIMENTAL HINDI RESULT — FIRST RUN\nSuite: ${report.suiteName}\nCases: ${report.totalCases} (Exact: ${report.exactMatchCount})\nCorpus WER: ${"%.2f".format(report.corpusWer * 100)}%\nCorpus CER: ${"%.2f".format(report.corpusCer * 100)}%\nMean RTF: ${"%.2f".format(report.meanRtf)}")
                            } else {
                                tvStatus.text = "HINDI BENCHMARK: FAIL"
                                showErrorBanner("Hindi benchmark failed")
                            }
                        }
                    }
                }.start()
            }
            "RUN_ASR_HINDI_DIAGNOSTIC", "org.itantra.action.RUN_ASR_HINDI_DIAGNOSTIC" -> {
                Thread {
                    kotlinx.coroutines.runBlocking {
                        val pair = org.itantra.core.stt.benchmark.AsrAccuracyBenchmarkRunner.runHindiLanguageDiagnostic(applicationContext)
                        runOnUiThread {
                            if (pair != null) {
                                tvStatus.text = "HINDI DIAGNOSTIC: DONE"
                                showSuccessBanner("DIAGNOSTIC A/B RESULT\nCase A (hi): WER=${"%.2f".format(pair.first.wer * 100)}% Raw='${pair.first.rawHypothesis}'\nCase B (null): WER=${"%.2f".format(pair.second.wer * 100)}% Raw='${pair.second.rawHypothesis}'")
                            } else {
                                tvStatus.text = "HINDI DIAGNOSTIC: FAIL"
                                showErrorBanner("Hindi diagnostic failed")
                            }
                        }
                    }
                }.start()
            }
            "RUN_VOSK_HINDI_BENCHMARK", "org.itantra.action.RUN_VOSK_HINDI_BENCHMARK" -> {
                Thread {
                    kotlinx.coroutines.runBlocking {
                        val report = org.itantra.core.stt.benchmark.VoskBenchmarkRunner.runVoskHindiSuite(applicationContext)
                        runOnUiThread {
                            if (report != null) {
                                tvStatus.text = "VOSK HINDI: DONE"
                                showSuccessBanner("VOSK HINDI 5-CASE BENCHMARK\nCases: ${report.totalCases} (Exact: ${report.exactMatchCount})\nCorpus WER: ${"%.2f".format(report.corpusWer * 100)}%\nCorpus CER: ${"%.2f".format(report.corpusCer * 100)}%\nTotal Inference: ${report.totalInferenceTimeMs}ms (Mean RTF: ${"%.2f".format(report.meanRtf)})")
                            } else {
                                tvStatus.text = "VOSK HINDI: FAIL"
                                showErrorBanner("Vosk Hindi benchmark failed")
                            }
                        }
                    }
                }.start()
            }
            "RUN_CONTROLLED_HINDI_STT_TEST", "org.itantra.action.RUN_CONTROLLED_HINDI_STT_TEST" -> {
                Thread {
                    kotlinx.coroutines.runBlocking {
                        val report = org.itantra.core.stt.diagnostic.ControlledHindiSttRunner.runEvaluation(applicationContext)
                        runOnUiThread {
                            if (report != null) {
                                tvStatus.text = "CONTROLLED HINDI STT: DONE"
                                showSuccessBanner("CONTROLLED HINDI TEST RESULT\nTotal: ${report.totalTrials} (Exact: ${report.exactMatchCount}, Devanagari: ${report.devanagariCount})\nMean WER: ${"%.2f".format(report.meanWer * 100)}%\nMean CER: ${"%.2f".format(report.meanCer * 100)}%\nMean Latency: ${"%.1f".format(report.meanInferenceTimeMs)}ms (RTF: ${"%.2f".format(report.meanRtf)})")
                            } else {
                                tvStatus.text = "CONTROLLED HINDI STT: FAIL"
                                showErrorBanner("Controlled Hindi STT test failed")
                            }
                        }
                    }
                }.start()
            }
            "RUN_PHYSICAL_TCP_SERVER", "org.itantra.action.RUN_PHYSICAL_TCP_SERVER" -> {
                val port = intent?.getIntExtra("serverPort", org.itantra.core.communication.transport.TcpTransportConstants.DEFAULT_PORT)
                    ?: org.itantra.core.communication.transport.TcpTransportConstants.DEFAULT_PORT
                Thread {
                    val started = org.itantra.core.communication.validation.PhysicalTcpValidationRunner.startServer(applicationContext, port)
                    runOnUiThread {
                        if (started) {
                            tvStatus.text = "TCP SERVER: LISTENING ($port)"
                            showSuccessBanner("M2 TCP Server active on port $port. Ready for physical client.")
                        } else {
                            tvStatus.text = "TCP SERVER: FAILED"
                            showErrorBanner("Failed to start TCP Server on port $port.")
                        }
                    }
                }.start()
            }
            "RUN_PHYSICAL_TCP_CLIENT", "org.itantra.action.RUN_PHYSICAL_TCP_CLIENT" -> {
                val host = intent?.getStringExtra("serverHost") ?: "172.20.29.81"
                val port = intent?.getIntExtra("serverPort", org.itantra.core.communication.transport.TcpTransportConstants.DEFAULT_PORT)
                    ?: org.itantra.core.communication.transport.TcpTransportConstants.DEFAULT_PORT
                Thread {
                    runOnUiThread {
                        tvStatus.text = "TCP CLIENT: RUNNING..."
                    }
                    val result = kotlinx.coroutines.runBlocking {
                        org.itantra.core.communication.validation.PhysicalTcpValidationRunner.runClientSuite(applicationContext, host, port)
                    }
                    runOnUiThread {
                        if (result.connectionSuccess && result.test1EnglishSuccess && result.test2UnicodeSuccess && result.test3OrderedSuccess && result.test4ReconnectSuccess) {
                            tvStatus.text = "TCP VALIDATION: PASS"
                            showSuccessBanner("Target: $host:$port\nAll 4 Tests Passed (English, Unicode, Ordered, Reconnect)\nSent ${result.sentMessages.size} messages successfully.")
                        } else {
                            tvStatus.text = "TCP VALIDATION: FAIL"
                            showErrorBanner("Error: ${result.errorMessage ?: "Validation suite failed"}")
                        }
                    }
                }.start()
            }
            "STOP_PHYSICAL_TCP_SERVER", "org.itantra.action.STOP_PHYSICAL_TCP_SERVER" -> {
                Thread {
                    org.itantra.core.communication.validation.PhysicalTcpValidationRunner.stopServer()
                    runOnUiThread {
                        tvStatus.text = "TCP SERVER: STOPPED"
                        hideBanner()
                    }
                }.start()
            }
            "RUN_PHYSICAL_TTS_TEST", "org.itantra.action.RUN_PHYSICAL_TTS_TEST" -> {
                Thread {
                    runOnUiThread {
                        tvStatus.text = "PHYSICAL TTS: RUNNING..."
                    }
                    val report = org.itantra.core.tts.diagnostic.TtsDiagnosticRunner.runFullPhysicalValidationSuite(applicationContext)
                    runOnUiThread {
                        if (report.allPassed) {
                            tvStatus.text = "PHYSICAL TTS: PASS"
                            val r1 = report.repeatedRuns[0]
                            val r2 = report.repeatedRuns[1]
                            val r3 = report.repeatedRuns[2]
                            val r4 = report.secondSentenceRun
                            val txt = "Device: ${report.deviceModel}\n" +
                                    "Run 1: ${r1.synthesisLatencyMs}ms synth, ${r1.playbackDurationMs}ms play (RTF ${"%.2f".format(r1.rtf)})\n" +
                                    "Run 2: ${r2.synthesisLatencyMs}ms synth, ${r2.playbackDurationMs}ms play (RTF ${"%.2f".format(r2.rtf)})\n" +
                                    "Run 3: ${r3.synthesisLatencyMs}ms synth, ${r3.playbackDurationMs}ms play (RTF ${"%.2f".format(r3.rtf)})\n" +
                                    "Second Text: ${r4.synthesisLatencyMs}ms synth, ${r4.playbackDurationMs}ms play (RTF ${"%.2f".format(r4.rtf)})"
                            showSuccessBanner(txt)
                        } else {
                            tvStatus.text = "PHYSICAL TTS: FAIL"
                            showErrorBanner("Error: ${report.errorMessage ?: "One or more TTS runs failed"}")
                        }
                    }
                }.start()
            }
            "RUN_HINDI_TTS_SCRATCH_SPIKE", "org.itantra.action.RUN_HINDI_TTS_SCRATCH_SPIKE" -> {
                val modelDir = intent?.getStringExtra("modelDir") ?: "/data/local/tmp/vits-piper-hi_IN-priyamvada-medium"
                val enablePlayback = intent?.getBooleanExtra("enablePlayback", true) ?: true
                Thread {
                    runOnUiThread {
                        tvStatus.text = "HINDI TTS SPIKE: RUNNING..."
                    }
                    val report = org.itantra.core.tts.diagnostic.HindiTtsScratchSpikeRunner.runSpike(
                        applicationContext,
                        modelDir = modelDir,
                        enablePlayback = enablePlayback
                    )
                    runOnUiThread {
                        if (report.allPassed) {
                            tvStatus.text = "HINDI TTS SPIKE: PASS"
                            val summary = StringBuilder("HINDI TTS SPIKE (${report.deviceModel}):\nInit: ${report.initLatencyMs}ms\n")
                            for (r in report.promptResults) {
                                summary.append("P${r.promptIndex}: ${r.synthesisTimeMs}ms (${"%.2f".format(r.audioDurationSeconds)}s, RTF ${"%.3f".format(r.rtf)}) -> Finite=${r.samplesFinite}, Play=${r.playbackSuccess}\n")
                            }
                            showSuccessBanner(summary.toString().trim())
                        } else {
                            tvStatus.text = "HINDI TTS SPIKE: FAIL"
                            showErrorBanner("Error: ${report.errorMessage ?: "Hindi TTS spike failed"}")
                        }
                    }
                }.start()
            }
            "RUN_PHYSICAL_HINDI_TTS_VALIDATION", "org.itantra.action.RUN_PHYSICAL_HINDI_TTS_VALIDATION" -> {
                val enablePlayback = intent?.getBooleanExtra("enablePlayback", true) ?: true
                Thread {
                    runOnUiThread {
                        tvStatus.text = "HINDI PROD TTS: RUNNING..."
                    }
                    val report = org.itantra.core.tts.diagnostic.HindiTtsPhysicalValidationRunner.runSuite(
                        applicationContext,
                        enablePlayback = enablePlayback
                    )
                    runOnUiThread {
                        if (report.allPassed) {
                            tvStatus.text = "HINDI PROD TTS: PASS"
                            val sb = StringBuilder("HINDI PROD TTS (${report.deviceModel}):\nInit: ${report.initLatencyMs}ms\n")
                            for (r in report.testAResults) {
                                sb.append("${r.promptId}: ${r.synthesisTimeMs}ms (${"%.2f".format(r.audioDurationSeconds)}s, RTF ${"%.3f".format(r.rtf)}) -> Play=${r.playbackSuccess}\n")
                            }
                            sb.append("Aliases: ${report.testBAliasesPassed}, English: ${report.testCEnglishPassed}, Unsupported: ${report.testDUnsupportedPassed}, Lifecycle: ${report.testFLifecyclePassed}")
                            showSuccessBanner(sb.toString().trim())
                        } else {
                            tvStatus.text = "HINDI PROD TTS: FAIL"
                            showErrorBanner("Error: ${report.errorMessage ?: "Hindi production TTS validation failed"}")
                        }
                    }
                }.start()
            }
            "RUN_PHYSICAL_M2_M3_RECEIVER", "org.itantra.action.RUN_PHYSICAL_M2_M3_RECEIVER" -> {
                val port = intent?.getIntExtra("serverPort", org.itantra.core.communication.transport.TcpTransportConstants.DEFAULT_PORT)
                    ?: org.itantra.core.communication.transport.TcpTransportConstants.DEFAULT_PORT
                Thread {
                    runOnUiThread {
                        tvStatus.text = "M2-M3 RECEIVER: STARTING..."
                        hideBanner()
                    }
                    val started = org.itantra.core.communication.validation.PhysicalM2M3ValidationRunner.startReceiver(applicationContext, port)
                    runOnUiThread {
                        if (started) {
                            tvStatus.text = "Receiver Ready — Listening ($port)"
                            hideBanner()
                            tvRemoteTranscript.text = "Waiting for incoming speech from Device A…"
                        } else {
                            tvStatus.text = "M2-M3 RECEIVER: FAILED"
                            showErrorBanner("Failed to start M2-M3 Receiver on port $port.")
                        }
                    }
                }.start()
            }
            "RUN_PHYSICAL_M2_M3_SENDER", "org.itantra.action.RUN_PHYSICAL_M2_M3_SENDER" -> {
                val host = intent?.getStringExtra("serverHost") ?: "172.20.29.81"
                val port = intent?.getIntExtra("serverPort", org.itantra.core.communication.transport.TcpTransportConstants.DEFAULT_PORT)
                    ?: org.itantra.core.communication.transport.TcpTransportConstants.DEFAULT_PORT
                Thread {
                    runOnUiThread {
                        tvStatus.text = "M2-M3 SENDER: RUNNING..."
                    }
                    val result = kotlinx.coroutines.runBlocking {
                        org.itantra.core.communication.validation.PhysicalM2M3ValidationRunner.runSenderSuite(applicationContext, host, port)
                    }
                    runOnUiThread {
                        if (result.connectionSuccess && result.msg1Sent && result.msg2Sent) {
                            tvStatus.text = "M2-M3 SENDER: PASS"
                            showSuccessBanner("Target: $host:$port\nSent ${result.sentCount} SpeechMessages successfully.")
                        } else {
                            tvStatus.text = "M2-M3 SENDER: FAIL"
                            showErrorBanner("Error: ${result.errorMessage ?: "Sender suite failed"}")
                        }
                    }
                }.start()
            }
            "STOP_PHYSICAL_M2_M3_RECEIVER", "org.itantra.action.STOP_PHYSICAL_M2_M3_RECEIVER" -> {
                Thread {
                    org.itantra.core.communication.validation.PhysicalM2M3ValidationRunner.stopReceiver()
                    runOnUiThread {
                        tvStatus.text = "M2-M3 RECEIVER: STOPPED"
                    }
                }.start()
            }
            "START_E2E_TRANSMITTER", "org.itantra.action.START_E2E_TRANSMITTER" -> {
                val host = intent?.getStringExtra("serverHost") ?: "172.20.29.81"
                val port = intent?.getIntExtra("serverPort", org.itantra.core.communication.transport.TcpTransportConstants.DEFAULT_PORT)
                    ?: org.itantra.core.communication.transport.TcpTransportConstants.DEFAULT_PORT
                val targetLang = intent?.getStringExtra("targetLanguage") ?: "hi"
                startE2eTransmitter(host, port, targetLang)
            }
            "RUN_M1_LIVE_MIC_FORENSIC", "org.itantra.action.RUN_M1_LIVE_MIC_FORENSIC" -> {
                val timeoutMs = intent?.getLongExtra("timeoutMs", 15000L) ?: 15000L
                Thread {
                    runOnUiThread {
                        tvStatus.text = "M1 FORENSIC: LISTENING..."
                    }
                    try {
                        val report = kotlinx.coroutines.runBlocking {
                            org.itantra.core.stt.diagnostic.LiveMicHindiForensicRunner.runForensicTest(
                                applicationContext,
                                timeoutMs = timeoutMs
                            )
                        }
                        runOnUiThread {
                            tvStatus.text = "M1 FORENSIC: COMPLETE"
                            tvLastError.visibility = View.VISIBLE
                            val sb = StringBuilder("M1 FORENSIC RESULT (${report.deviceModel}):\n")
                            sb.append("WAV: ${report.captureMetrics.wavFileSize}B | SHA: ${report.captureMetrics.sha256.take(12)}...\n")
                            sb.append("RMS: ${"%.1f".format(report.captureMetrics.rmsDbfs)} dBFS | Peak: ${report.captureMetrics.peakAbsoluteSample} (${"%.1f".format(report.captureMetrics.peakDbfs)} dBFS)\n")
                            sb.append("Zeros: ${"%.2f".format(report.captureMetrics.zeroSamplePercentage)}% | Clips: ${report.captureMetrics.clippingSampleCount}\n")
                            sb.append("LIVE ASR: \"${report.liveWhisperResult.rawHypothesis}\" (WER=${"%.1f".format(report.liveWhisperResult.wer * 100)}%, RTF=${"%.2f".format(report.liveWhisperResult.rtf)})\n")
                            sb.append("REPLAY ASR: \"${report.directReplayResult.rawHypothesis}\" (WER=${"%.1f".format(report.directReplayResult.wer * 100)}%, RTF=${"%.2f".format(report.directReplayResult.rtf)})\n")
                            sb.append("Case: ${report.comparisonCase}")
                            tvLastError.text = sb.toString().trim()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            tvStatus.text = "M1 FORENSIC: FAIL"
                            tvLastError.visibility = View.VISIBLE
                            tvLastError.text = "Error: ${e.message}"
                        }
                    }
                }.start()
            }
            "RUN_M1_LIVE_MIC_GAIN_AB", "org.itantra.action.RUN_M1_LIVE_MIC_GAIN_AB" -> {
                Thread {
                    runOnUiThread {
                        tvStatus.text = "M1 GAIN A/B: RUNNING..."
                    }
                    try {
                        val report = kotlinx.coroutines.runBlocking {
                            org.itantra.core.stt.diagnostic.LiveMicGainAbRunner.runGainAbExperiment(applicationContext)
                        }
                        runOnUiThread {
                            tvStatus.text = "M1 GAIN A/B: COMPLETE"
                            tvLastError.visibility = View.VISIBLE
                            val sb = StringBuilder("M1 GAIN A/B (${report.deviceModel}):\n")
                            for (v in report.variants) {
                                sb.append("${v.gainLabel}: RMS=${"%.1f".format(v.rmsDbfs)}dBFS, WER=${"%.1f".format(v.wer * 100)}%, CER=${"%.1f".format(v.cer * 100)}% -> \"${v.rawHypothesis}\"\n")
                            }
                            sb.append("Classification: ${report.classification}")
                            tvLastError.text = sb.toString().trim()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            tvStatus.text = "M1 GAIN A/B: FAIL"
                            tvLastError.visibility = View.VISIBLE
                            tvLastError.text = "Error: ${e.message}"
                        }
                    }
                }.start()
            }
            "RUN_VOSK_LIVE_COMPARISON", "org.itantra.action.RUN_VOSK_LIVE_COMPARISON" -> {
                Thread {
                    runOnUiThread {
                        tvStatus.text = "VOSK LIVE COMPARISON: RUNNING..."
                    }
                    try {
                        val report = kotlinx.coroutines.runBlocking {
                            org.itantra.core.stt.diagnostic.VoskLiveHindiComparisonRunner.runComparison(applicationContext)
                        }
                        runOnUiThread {
                            tvStatus.text = "VOSK LIVE COMPARISON: COMPLETE"
                            tvLastError.visibility = View.VISIBLE
                            val sb = StringBuilder("VOSK LIVE COMPARISON (${report.deviceModel}):\n")
                            sb.append("Raw Hyp: \"${report.rawHypothesis}\"\n")
                            sb.append("WER: ${"%.1f".format(report.wer * 100)}%, CER: ${"%.1f".format(report.cer * 100)}%\n")
                            sb.append("Alignment: S=${report.substitutions}, D=${report.deletions}, I=${report.insertions}, H=${report.hits}, N=${report.referenceWordCount}\n")
                            sb.append("Time: ${report.inferenceTimeMs}ms (RTF: ${"%.3f".format(report.rtf)})\n")
                            sb.append("Class: ${report.classification}")
                            tvLastError.text = sb.toString().trim()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            tvStatus.text = "VOSK LIVE COMPARISON: FAIL"
                            tvLastError.visibility = View.VISIBLE
                            tvLastError.text = "Error: ${e.message}"
                        }
                    }
                }.start()
            }
            "RUN_MIC_CHARACTERIZATION", "org.itantra.action.RUN_MIC_CHARACTERIZATION" -> {
                val testType = intent?.getStringExtra("testType") ?: "ALL"
                val durationMs = intent?.getLongExtra("durationMs", 8000L) ?: 8000L
                Thread {
                    runOnUiThread {
                        tvStatus.text = "MIC CHAR ($testType): RUNNING..."
                    }
                    try {
                        when (testType.uppercase()) {
                            "EN" -> {
                                val res = kotlinx.coroutines.runBlocking {
                                    org.itantra.core.audio.diagnostic.MicAcousticCharacterizationRunner.captureAndAnalyze(
                                        context = applicationContext,
                                        testLabel = "Test A — English",
                                        outputFileName = "mic_characterization_en.wav",
                                        referenceText = org.itantra.core.audio.diagnostic.MicAcousticCharacterizationRunner.ENGLISH_REFERENCE,
                                        language = "en",
                                        maxDurationMs = durationMs
                                    )
                                }
                                runOnUiThread {
                                    tvStatus.text = "MIC CHAR EN: COMPLETE"
                                    tvLastError.visibility = View.VISIBLE
                                    tvLastError.text = "Test A (EN):\nRMS=${"%.1f".format(res.signalStats.rmsDbfs)}dBFS, Peak=${res.signalStats.peakAbsolute}\nWER=${"%.1f".format((res.asrResult?.wer ?: 0.0) * 100)}%\nHyp=\"${res.asrResult?.rawHypothesis}\""
                                }
                            }
                            "HI" -> {
                                val res = kotlinx.coroutines.runBlocking {
                                    org.itantra.core.audio.diagnostic.MicAcousticCharacterizationRunner.captureAndAnalyze(
                                        context = applicationContext,
                                        testLabel = "Test B — Hindi",
                                        outputFileName = "mic_characterization_hi.wav",
                                        referenceText = org.itantra.core.audio.diagnostic.MicAcousticCharacterizationRunner.HINDI_REFERENCE,
                                        language = "hi",
                                        maxDurationMs = durationMs
                                    )
                                }
                                runOnUiThread {
                                    tvStatus.text = "MIC CHAR HI: COMPLETE"
                                    tvLastError.visibility = View.VISIBLE
                                    tvLastError.text = "Test B (HI):\nRMS=${"%.1f".format(res.signalStats.rmsDbfs)}dBFS, Peak=${res.signalStats.peakAbsolute}\nWER=${"%.1f".format((res.asrResult?.wer ?: 0.0) * 100)}%\nHyp=\"${res.asrResult?.rawHypothesis}\""
                                }
                            }
                            "PLAYBACK_HI" -> {
                                val res = kotlinx.coroutines.runBlocking {
                                    org.itantra.core.audio.diagnostic.MicAcousticCharacterizationRunner.captureAndAnalyze(
                                        context = applicationContext,
                                        testLabel = "Test C — Playback Hindi",
                                        outputFileName = "mic_characterization_playback_hi.wav",
                                        referenceText = "कुछ अणुओं में अस्थिर केंद्रक होता है जिसका मतलब यह है कि उनमें थोड़े या बिना किसी झटके से टूटने की प्रवृत्ति होती है",
                                        language = "hi",
                                        maxDurationMs = 12000L,
                                        playbackAssetBeforeCapture = "speech_test_hi_001.wav"
                                    )
                                }
                                runOnUiThread {
                                    tvStatus.text = "MIC CHAR PLAYBACK: COMPLETE"
                                    tvLastError.visibility = View.VISIBLE
                                    tvLastError.text = "Test C (PLAYBACK):\nRMS=${"%.1f".format(res.signalStats.rmsDbfs)}dBFS, Peak=${res.signalStats.peakAbsolute}\nWER=${"%.1f".format((res.asrResult?.wer ?: 0.0) * 100)}%\nHyp=\"${res.asrResult?.rawHypothesis}\""
                                }
                            }
                            else -> {
                                val report = kotlinx.coroutines.runBlocking {
                                    org.itantra.core.audio.diagnostic.MicAcousticCharacterizationRunner.runFullCharacterizationSuite(applicationContext)
                                }
                                runOnUiThread {
                                    tvStatus.text = "MIC CHAR SUITE: COMPLETE"
                                    tvLastError.visibility = View.VISIBLE
                                    val sb = StringBuilder("MIC CHAR SUITE (${report.deviceModel}):\n")
                                    report.testA_English?.let {
                                        sb.append("EN: RMS=${"%.1f".format(it.signalStats.rmsDbfs)}dBFS, WER=${"%.1f".format((it.asrResult?.wer ?: 0.0) * 100)}% -> \"${it.asrResult?.rawHypothesis}\"\n")
                                    }
                                    report.testB_Hindi?.let {
                                        sb.append("HI: RMS=${"%.1f".format(it.signalStats.rmsDbfs)}dBFS, WER=${"%.1f".format((it.asrResult?.wer ?: 0.0) * 100)}% -> \"${it.asrResult?.rawHypothesis}\"\n")
                                    }
                                    report.testC_PlaybackHindi?.let {
                                        sb.append("PLAYBACK: RMS=${"%.1f".format(it.signalStats.rmsDbfs)}dBFS, WER=${"%.1f".format((it.asrResult?.wer ?: 0.0) * 100)}% -> \"${it.asrResult?.rawHypothesis}\"\n")
                                    }
                                    tvLastError.text = sb.toString().trim()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            tvStatus.text = "MIC CHAR: FAIL"
                            tvLastError.visibility = View.VISIBLE
                            tvLastError.text = "Error: ${e.message}"
                        }
                    }
                }.start()
            }
            "RUN_RAW_MIC_CAPTURE_CONTROL", "org.itantra.action.RUN_RAW_MIC_CAPTURE_CONTROL" -> {
                val testType = intent?.getStringExtra("testType") ?: "ALL"
                Thread {
                    runOnUiThread {
                        tvStatus.text = "RAW MIC CONTROL ($testType): RUNNING..."
                    }
                    try {
                        when (testType.uppercase()) {
                            "EN" -> {
                                val res = kotlinx.coroutines.runBlocking {
                                    org.itantra.core.audio.diagnostic.RawMicCaptureControlRunner.captureFixed8Seconds(
                                        context = applicationContext,
                                        testLabel = "Raw Control — English",
                                        outputFileName = "raw_mic_en_control.wav",
                                        referenceText = org.itantra.core.audio.diagnostic.RawMicCaptureControlRunner.ENGLISH_REFERENCE,
                                        language = "en"
                                    )
                                }
                                runOnUiThread {
                                    tvStatus.text = "RAW MIC EN: COMPLETE"
                                    tvLastError.visibility = View.VISIBLE
                                    tvLastError.text = "Raw EN Control:\nSamples=${res.metrics.sampleCount} (${res.metrics.durationSeconds}s)\nRMS=${"%.1f".format(res.metrics.rmsDbfs)}dBFS, Peak=${res.metrics.peakAbsoluteSample}\nWER=${"%.1f".format((res.asrResult?.wer ?: 0.0) * 100)}%\nHyp=\"${res.asrResult?.rawHypothesis}\""
                                }
                            }
                            "HI" -> {
                                val res = kotlinx.coroutines.runBlocking {
                                    org.itantra.core.audio.diagnostic.RawMicCaptureControlRunner.captureFixed8Seconds(
                                        context = applicationContext,
                                        testLabel = "Raw Control — Hindi",
                                        outputFileName = "raw_mic_hi_control.wav",
                                        referenceText = org.itantra.core.audio.diagnostic.RawMicCaptureControlRunner.HINDI_REFERENCE,
                                        language = "hi"
                                    )
                                }
                                runOnUiThread {
                                    tvStatus.text = "RAW MIC HI: COMPLETE"
                                    tvLastError.visibility = View.VISIBLE
                                    tvLastError.text = "Raw HI Control:\nSamples=${res.metrics.sampleCount} (${res.metrics.durationSeconds}s)\nRMS=${"%.1f".format(res.metrics.rmsDbfs)}dBFS, Peak=${res.metrics.peakAbsoluteSample}\nWER=${"%.1f".format((res.asrResult?.wer ?: 0.0) * 100)}%\nHyp=\"${res.asrResult?.rawHypothesis}\""
                                }
                            }
                            else -> {
                                val resEn = kotlinx.coroutines.runBlocking {
                                    org.itantra.core.audio.diagnostic.RawMicCaptureControlRunner.captureFixed8Seconds(
                                        context = applicationContext,
                                        testLabel = "Raw Control — English",
                                        outputFileName = "raw_mic_en_control.wav",
                                        referenceText = org.itantra.core.audio.diagnostic.RawMicCaptureControlRunner.ENGLISH_REFERENCE,
                                        language = "en"
                                    )
                                }
                                Thread.sleep(1000)
                                val resHi = kotlinx.coroutines.runBlocking {
                                    org.itantra.core.audio.diagnostic.RawMicCaptureControlRunner.captureFixed8Seconds(
                                        context = applicationContext,
                                        testLabel = "Raw Control — Hindi",
                                        outputFileName = "raw_mic_hi_control.wav",
                                        referenceText = org.itantra.core.audio.diagnostic.RawMicCaptureControlRunner.HINDI_REFERENCE,
                                        language = "hi"
                                    )
                                }
                                runOnUiThread {
                                    tvStatus.text = "RAW MIC CONTROL: COMPLETE"
                                    tvLastError.visibility = View.VISIBLE
                                    val sb = StringBuilder("RAW MIC CONTROL RESULT:\n")
                                    sb.append("EN: ${resEn.metrics.sampleCount} samples (${resEn.metrics.durationSeconds}s), RMS=${"%.1f".format(resEn.metrics.rmsDbfs)}dBFS, WER=${"%.1f".format((resEn.asrResult?.wer ?: 0.0) * 100)}% -> \"${resEn.asrResult?.rawHypothesis}\"\n")
                                    sb.append("HI: ${resHi.metrics.sampleCount} samples (${resHi.metrics.durationSeconds}s), RMS=${"%.1f".format(resHi.metrics.rmsDbfs)}dBFS, WER=${"%.1f".format((resHi.asrResult?.wer ?: 0.0) * 100)}% -> \"${resHi.asrResult?.rawHypothesis}\"")
                                    tvLastError.text = sb.toString().trim()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            tvStatus.text = "RAW MIC CONTROL: FAIL"
                            tvLastError.visibility = View.VISIBLE
                            tvLastError.text = "Error: ${e.message}"
                        }
                    }
                }.start()
            }
            "RUN_MIC_ROUTING_CONTROL", "org.itantra.action.RUN_MIC_ROUTING_CONTROL" -> {
                val testType = intent?.getStringExtra("testType") ?: "ALL"
                val sourceParam = intent?.getStringExtra("audioSource")?.uppercase() ?: "VOICE_RECOGNITION"
                val chosenSource = if (sourceParam == "MIC") android.media.MediaRecorder.AudioSource.MIC else android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION

                Thread {
                    runOnUiThread {
                        tvStatus.text = "ROUTING CONTROL ($testType): RUNNING..."
                    }
                    try {
                        when (testType.uppercase()) {
                            "EN" -> {
                                val res = kotlinx.coroutines.runBlocking {
                                    org.itantra.core.audio.diagnostic.MicRoutingControlRunner.captureAndEvaluate(
                                        context = applicationContext,
                                        testLabel = "Source Control — English ($sourceParam)",
                                        outputFileName = "raw_mic_source_control_en.wav",
                                        referenceText = org.itantra.core.audio.diagnostic.MicRoutingControlRunner.ENGLISH_REFERENCE,
                                        language = "en",
                                        audioSource = chosenSource
                                    )
                                }
                                runOnUiThread {
                                    tvStatus.text = "SOURCE CONTROL EN: COMPLETE"
                                    tvLastError.visibility = View.VISIBLE
                                    tvLastError.text = "Routing Snapshot:\nMode=${res.routingSnapshot.audioMode}, Source=${res.routingSnapshot.audioRecordSource}\n" +
                                            "EN Audio: ${res.metrics.sampleCount} samples (${res.metrics.durationSeconds}s), RMS=${"%.1f".format(res.metrics.rmsDbfs)}dBFS, Peak=${res.metrics.peakAbsoluteSample}\n" +
                                            "WER=${"%.1f".format((res.asrResult?.wer ?: 0.0) * 100)}%, CER=${"%.1f".format((res.asrResult?.cer ?: 0.0) * 100)}%\nHyp=\"${res.asrResult?.rawHypothesis}\""
                                }
                            }
                            "HI" -> {
                                val res = kotlinx.coroutines.runBlocking {
                                    org.itantra.core.audio.diagnostic.MicRoutingControlRunner.captureAndEvaluate(
                                        context = applicationContext,
                                        testLabel = "Source Control — Hindi ($sourceParam)",
                                        outputFileName = "raw_mic_source_control_hi.wav",
                                        referenceText = org.itantra.core.audio.diagnostic.MicRoutingControlRunner.HINDI_REFERENCE,
                                        language = "hi",
                                        audioSource = chosenSource
                                    )
                                }
                                runOnUiThread {
                                    tvStatus.text = "SOURCE CONTROL HI: COMPLETE"
                                    tvLastError.visibility = View.VISIBLE
                                    tvLastError.text = "Routing Snapshot:\nMode=${res.routingSnapshot.audioMode}, Source=${res.routingSnapshot.audioRecordSource}\n" +
                                            "HI Audio: ${res.metrics.sampleCount} samples (${res.metrics.durationSeconds}s), RMS=${"%.1f".format(res.metrics.rmsDbfs)}dBFS, Peak=${res.metrics.peakAbsoluteSample}\n" +
                                            "WER=${"%.1f".format((res.asrResult?.wer ?: 0.0) * 100)}%, CER=${"%.1f".format((res.asrResult?.cer ?: 0.0) * 100)}%\nHyp=\"${res.asrResult?.rawHypothesis}\""
                                }
                            }
                            "SOURCE_AB" -> {
                                // A/B Comparison between VOICE_RECOGNITION and standard MIC
                                val resVr = kotlinx.coroutines.runBlocking {
                                    org.itantra.core.audio.diagnostic.MicRoutingControlRunner.captureAndEvaluate(
                                        context = applicationContext,
                                        testLabel = "Source A — VOICE_RECOGNITION",
                                        outputFileName = "raw_mic_ab_vr.wav",
                                        referenceText = org.itantra.core.audio.diagnostic.MicRoutingControlRunner.ENGLISH_REFERENCE,
                                        language = "en",
                                        audioSource = android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION
                                    )
                                }
                                Thread.sleep(1000)
                                val resMic = kotlinx.coroutines.runBlocking {
                                    org.itantra.core.audio.diagnostic.MicRoutingControlRunner.captureAndEvaluate(
                                        context = applicationContext,
                                        testLabel = "Source B — MIC",
                                        outputFileName = "raw_mic_ab_mic.wav",
                                        referenceText = org.itantra.core.audio.diagnostic.MicRoutingControlRunner.ENGLISH_REFERENCE,
                                        language = "en",
                                        audioSource = android.media.MediaRecorder.AudioSource.MIC
                                    )
                                }
                                runOnUiThread {
                                    tvStatus.text = "SOURCE A/B: COMPLETE"
                                    tvLastError.visibility = View.VISIBLE
                                    val sb = StringBuilder("SOURCE A/B RESULT:\n")
                                    sb.append("VR (AudioSource 6): RMS=${"%.1f".format(resVr.metrics.rmsDbfs)}dBFS, WER=${"%.1f".format((resVr.asrResult?.wer ?: 0.0) * 100)}% -> \"${resVr.asrResult?.rawHypothesis}\"\n")
                                    sb.append("MIC (AudioSource 1): RMS=${"%.1f".format(resMic.metrics.rmsDbfs)}dBFS, WER=${"%.1f".format((resMic.asrResult?.wer ?: 0.0) * 100)}% -> \"${resMic.asrResult?.rawHypothesis}\"")
                                    tvLastError.text = sb.toString().trim()
                                }
                            }
                            else -> {
                                val resEn = kotlinx.coroutines.runBlocking {
                                    org.itantra.core.audio.diagnostic.MicRoutingControlRunner.captureAndEvaluate(
                                        context = applicationContext,
                                        testLabel = "Source Control — English",
                                        outputFileName = "raw_mic_source_control_en.wav",
                                        referenceText = org.itantra.core.audio.diagnostic.MicRoutingControlRunner.ENGLISH_REFERENCE,
                                        language = "en",
                                        audioSource = android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION
                                    )
                                }
                                Thread.sleep(1000)
                                val resHi = kotlinx.coroutines.runBlocking {
                                    org.itantra.core.audio.diagnostic.MicRoutingControlRunner.captureAndEvaluate(
                                        context = applicationContext,
                                        testLabel = "Source Control — Hindi",
                                        outputFileName = "raw_mic_source_control_hi.wav",
                                        referenceText = org.itantra.core.audio.diagnostic.MicRoutingControlRunner.HINDI_REFERENCE,
                                        language = "hi",
                                        audioSource = android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION
                                    )
                                }
                                runOnUiThread {
                                    tvStatus.text = "SOURCE CONTROL SUITE: COMPLETE"
                                    tvLastError.visibility = View.VISIBLE
                                    val sb = StringBuilder("SOURCE CONTROL SUITE:\n")
                                    sb.append("EN: ${resEn.metrics.sampleCount} samples (${resEn.metrics.durationSeconds}s), RMS=${"%.1f".format(resEn.metrics.rmsDbfs)}dBFS, WER=${"%.1f".format((resEn.asrResult?.wer ?: 0.0) * 100)}% -> \"${resEn.asrResult?.rawHypothesis}\"\n")
                                    sb.append("HI: ${resHi.metrics.sampleCount} samples (${resHi.metrics.durationSeconds}s), RMS=${"%.1f".format(resHi.metrics.rmsDbfs)}dBFS, WER=${"%.1f".format((resHi.asrResult?.wer ?: 0.0) * 100)}% -> \"${resHi.asrResult?.rawHypothesis}\"")
                                    tvLastError.text = sb.toString().trim()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            tvStatus.text = "ROUTING CONTROL: FAIL"
                            tvLastError.visibility = View.VISIBLE
                            tvLastError.text = "Error: ${e.message}"
                        }
                    }
                }.start()
            }
            "RUN_ASR_SEG_CONTROL", "org.itantra.action.RUN_ASR_SEG_CONTROL" -> {
                Thread {
                    runOnUiThread {
                        tvStatus.text = "ASR SEG CONTROL: RUNNING..."
                    }
                    try {
                        val report = kotlinx.coroutines.runBlocking {
                            org.itantra.core.stt.diagnostic.AsrSegmentationControlRunner.runFullExperiment(applicationContext)
                        }
                        runOnUiThread {
                            tvStatus.text = "ASR SEG CONTROL: COMPLETE"
                            tvLastError.visibility = View.VISIBLE
                            val sb = StringBuilder("ASR SEGMENTATION CONTROL:\n")
                            sb.append("Speech Boundary: ${report.speechBoundary.speechStartMs}ms - ${report.speechBoundary.speechEndMs}ms (${report.speechBoundary.speechDurationMs}ms)\n")
                            for (r in report.segmentationResults) {
                                sb.append("${r.label}: ${r.durationMs}ms, WER=${"%.1f".format(r.wer * 100)}%, CER=${"%.1f".format(r.cer * 100)}% -> \"${r.rawHypothesis}\"\n")
                            }
                            if (report.hindiAudit.explicitHindiResult != null) {
                                sb.append("HI (Explicit): WER=${"%.1f".format(report.hindiAudit.explicitHindiResult.wer * 100)}% -> \"${report.hindiAudit.explicitHindiResult.rawHypothesis}\"\n")
                            }
                            tvLastError.text = sb.toString().trim()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            tvStatus.text = "ASR SEG CONTROL: FAIL"
                            tvLastError.visibility = View.VISIBLE
                            tvLastError.text = "Error: ${e.message}"
                        }
                    }
                }.start()
            }
            "RUN_ASR_SILENCE_CONTROL", "org.itantra.action.RUN_ASR_SILENCE_CONTROL" -> {
                Thread {
                    runOnUiThread {
                        tvStatus.text = "SILENCE CONTROL: RUNNING..."
                    }
                    try {
                        val report = kotlinx.coroutines.runBlocking {
                            org.itantra.core.stt.diagnostic.AsrSilenceControlRunner.runExperiment(applicationContext)
                        }
                        runOnUiThread {
                            tvStatus.text = "SILENCE CONTROL: COMPLETE"
                            tvLastError.visibility = View.VISIBLE
                            val sb = StringBuilder("SILENCE CONTROL (10 VARIANTS):\n")
                            sb.append("Base: ${report.baseMetrics.sampleCount} samples, RMS=${"%.1f".format(report.baseMetrics.rmsDbfs)}dBFS\n")
                            for (r in report.variantResults) {
                                sb.append("${r.variantId}: L=${r.leadingSilenceMs}ms, T=${r.trailingSilenceMs}ms -> ${r.inferenceTimeMs}ms (RTF ${"%.2f".format(r.rtf)}), WER=${"%.0f".format(r.wer * 100)}% -> \"${r.rawHypothesis}\"\n")
                            }
                            tvLastError.text = sb.toString().trim()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            tvStatus.text = "SILENCE CONTROL: FAIL"
                            tvLastError.visibility = View.VISIBLE
                            tvLastError.text = "Error: ${e.message}"
                        }
                    }
                }.start()
            }
            "RUN_ACOUSTIC_DOMAIN_COMPARISON", "org.itantra.action.RUN_ACOUSTIC_DOMAIN_COMPARISON" -> {
                Thread {
                    runOnUiThread {
                        tvStatus.text = "ACOUSTIC COMP: RUNNING..."
                    }
                    try {
                        val report = kotlinx.coroutines.runBlocking {
                            org.itantra.core.audio.diagnostic.AcousticDomainComparisonRunner.runComparison(applicationContext)
                        }
                        runOnUiThread {
                            tvStatus.text = "ACOUSTIC COMP: COMPLETE"
                            tvLastError.visibility = View.VISIBLE
                            val g = report.goodProfile
                            val f = report.failedProfile
                            val sb = StringBuilder("ACOUSTIC DOMAIN COMPARISON:\n")
                            sb.append("GOOD (${g.whisperResult.fileName}): WER=${"%.1f".format(g.whisperResult.wer * 100)}%, RMS=${"%.1f".format(g.sourceVerification.rmsDbfs)}dBFS -> \"${g.whisperResult.rawHypothesis}\"\n")
                            sb.append("FAILED (${f.whisperResult.fileName}): WER=${"%.1f".format(f.whisperResult.wer * 100)}%, RMS=${"%.1f".format(f.sourceVerification.rmsDbfs)}dBFS -> \"${f.whisperResult.rawHypothesis}\"\n")
                            sb.append("Spectral Dist: Cosine=${"%.4f".format(report.spectralCosineDistance)}, Euclidean=${"%.4f".format(report.spectralEuclideanDistance)}")
                            tvLastError.text = sb.toString().trim()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            tvStatus.text = "ACOUSTIC COMP: FAIL"
                            tvLastError.visibility = View.VISIBLE
                            tvLastError.text = "Error: ${e.message}"
                        }
                    }
                }.start()
            }
            "RUN_MIC_INTEGRITY_TEST", "org.itantra.action.RUN_MIC_INTEGRITY_TEST" -> {
                val durationMs = intent?.getLongExtra("durationMs", 7000L) ?: 7000L
                Thread {
                    runOnUiThread {
                        tvStatus.text = "MIC INTEGRITY: RECORDING..."
                    }
                    try {
                        val stats = kotlinx.coroutines.runBlocking {
                            org.itantra.core.audio.diagnostic.MicAudioIntegrityRunner.recordAndAnalyze(applicationContext, durationMs)
                        }
                        runOnUiThread {
                            tvStatus.text = "MIC INTEGRITY: COMPLETE"
                            tvLastError.visibility = View.VISIBLE
                            tvLastError.text = "Samples: ${stats.sampleCount} (${stats.durationMs}ms)\n" +
                                    "RMS: ${"%.1f".format(stats.rmsDbfs)} dBFS (Peak: ${stats.peakAbsoluteAmplitude})\n" +
                                    "Zeros: ${"%.2f".format(stats.zeroSamplePercentage)}% | Clips: Min=${stats.clippingMinCount}, Max=${stats.clippingMaxCount}\n" +
                                    "WAV Size: ${stats.wavFileSize}B | SHA: ${stats.wavSha256.take(12)}..."
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            tvStatus.text = "MIC INTEGRITY: FAIL"
                            tvLastError.visibility = View.VISIBLE
                            tvLastError.text = "Error: ${e.message}"
                        }
                    }
                }.start()
            }
            "RUN_CAPTURED_AUDIO_ASR_TEST", "org.itantra.action.RUN_CAPTURED_AUDIO_ASR_TEST" -> {
                val repetitions = intent?.getIntExtra("repetitions", 3) ?: 3
                val wavFile = intent?.getStringExtra("wavFile") ?: "mic_integrity_test.wav"
                Thread {
                    runOnUiThread {
                        tvStatus.text = "CAPTURED ASR: RUNNING..."
                    }
                    try {
                        val report = kotlinx.coroutines.runBlocking {
                            org.itantra.core.stt.diagnostic.CapturedAudioAsrIsolationRunner.runIsolationTest(
                                applicationContext,
                                wavFileName = wavFile,
                                repetitions = repetitions
                            )
                        }
                        runOnUiThread {
                            tvStatus.text = "CAPTURED ASR: COMPLETE"
                            tvLastError.visibility = View.VISIBLE
                            val summary = StringBuilder("CAPTURED AUDIO ASR ISOLATION (3 RUNS):\n")
                            for (r in report.runs) {
                                summary.append("Run ${r.runIndex}: WER=${"%.1f".format(r.wer * 100)}%, CER=${"%.1f".format(r.cer * 100)}%, RTF=${"%.2f".format(r.rtf)} -> \"${r.rawHypothesis}\"\n")
                            }
                            tvLastError.text = summary.toString().trim()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            tvStatus.text = "CAPTURED ASR: FAIL"
                            tvLastError.visibility = View.VISIBLE
                            tvLastError.text = "Error: ${e.message}"
                        }
                    }
                }.start()
            }
            "RUN_PROD_CAPTURE_ISOLATION_TEST", "org.itantra.action.RUN_PROD_CAPTURE_ISOLATION_TEST" -> {
                val durationMs = intent?.getLongExtra("durationMs", 8000L) ?: 8000L
                val repetitions = intent?.getIntExtra("repetitions", 3) ?: 3
                Thread {
                    runOnUiThread {
                        tvStatus.text = "PROD CAPTURE: RECORDING..."
                    }
                    try {
                        val result = kotlinx.coroutines.runBlocking {
                            org.itantra.core.audio.diagnostic.ProductionCaptureSourceIsolationRunner.recordAndEvaluate(
                                applicationContext,
                                captureDurationMs = durationMs,
                                repetitions = repetitions
                            )
                        }
                        runOnUiThread {
                            tvStatus.text = "PROD CAPTURE: COMPLETE"
                            tvLastError.visibility = View.VISIBLE
                            val summary = StringBuilder("PROD CAPTURE ISOLATION (3 PASSES):\n")
                            for (r in result.asrRuns) {
                                summary.append("Pass ${r.runIndex}: WER=${"%.1f".format(r.wer * 100)}%, CER=${"%.1f".format(r.cer * 100)}%, RTF=${"%.2f".format(r.rtf)} -> \"${r.rawHypothesis}\"\n")
                            }
                            tvLastError.text = summary.toString().trim()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            tvStatus.text = "PROD CAPTURE: FAIL"
                            tvLastError.visibility = View.VISIBLE
                            tvLastError.text = "Error: ${e.message}"
                        }
                    }
                }.start()
            }
            "RUN_DEVICE_MIC_VS_REF_COMPARISON", "org.itantra.action.RUN_DEVICE_MIC_VS_REF_COMPARISON" -> {
                val repetitions = intent?.getIntExtra("repetitions", 3) ?: 3
                Thread {
                    runOnUiThread {
                        tvStatus.text = "MIC VS REF: RUNNING..."
                    }
                    try {
                        val report = kotlinx.coroutines.runBlocking {
                            org.itantra.core.stt.diagnostic.DeviceMicVsReferenceAudioRunner.runComparison(
                                applicationContext,
                                repetitions = repetitions
                            )
                        }
                        runOnUiThread {
                            tvStatus.text = "MIC VS REF: COMPLETE"
                            tvLastError.visibility = View.VISIBLE
                            val summary = StringBuilder("MIC VS REF COMPARISON SUMMARY:\n")
                            summary.append("Cond A (Ref): WER=${"%.1f".format(report.conditionA.meanWer * 100)}%, CER=${"%.1f".format(report.conditionA.meanCer * 100)}%, Exact=${report.conditionA.exactMatchCount}/$repetitions\n")
                            summary.append("Cond B (Mic): WER=${"%.1f".format(report.conditionB.meanWer * 100)}%, CER=${"%.1f".format(report.conditionB.meanCer * 100)}%, Exact=${report.conditionB.exactMatchCount}/$repetitions\n")
                            tvLastError.text = summary.toString().trim()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            tvStatus.text = "MIC VS REF: FAIL"
                            tvLastError.visibility = View.VISIBLE
                            tvLastError.text = "Error: ${e.message}"
                        }
                    }
                }.start()
            }
            "RUN_MIC_GAIN_DIAGNOSTIC", "org.itantra.action.RUN_MIC_GAIN_DIAGNOSTIC" -> {
                val repetitions = intent?.getIntExtra("repetitions", 3) ?: 3
                Thread {
                    runOnUiThread {
                        tvStatus.text = "GAIN DIAGNOSTIC: RUNNING..."
                    }
                    try {
                        val report = kotlinx.coroutines.runBlocking {
                            org.itantra.core.stt.diagnostic.MicGainDiagnosticRunner.runGainDiagnostic(
                                applicationContext,
                                repetitions = repetitions
                            )
                        }
                        runOnUiThread {
                            tvStatus.text = "GAIN DIAGNOSTIC: COMPLETE"
                            tvLastError.visibility = View.VISIBLE
                            val summary = StringBuilder("MIC GAIN DIAGNOSTIC SUMMARY:\n")
                            for (v in report.variants) {
                                summary.append("${v.stats.variantName}: WER=${"%.1f".format(v.meanWer * 100)}%, CER=${"%.1f".format(v.meanCer * 100)}%, Exact=${v.exactMatchCount}/$repetitions -> \"${v.runs.firstOrNull()?.rawHypothesis}\"\n")
                            }
                            tvLastError.text = summary.toString().trim()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            tvStatus.text = "GAIN DIAGNOSTIC: FAIL"
                            tvLastError.visibility = View.VISIBLE
                            tvLastError.text = "Error: ${e.message}"
                        }
                    }
                }.start()
            }
            "RUN_LEVEL_MATCHED_REF_DIAGNOSTIC", "org.itantra.action.RUN_LEVEL_MATCHED_REF_DIAGNOSTIC" -> {
                val repetitions = intent?.getIntExtra("repetitions", 3) ?: 3
                Thread {
                    runOnUiThread {
                        tvStatus.text = "LEVEL MATCH: RUNNING..."
                    }
                    try {
                        val report = kotlinx.coroutines.runBlocking {
                            org.itantra.core.stt.diagnostic.LevelMatchedReferenceDiagnosticRunner.runLevelMatchDiagnostic(
                                applicationContext,
                                repetitions = repetitions
                            )
                        }
                        runOnUiThread {
                            tvStatus.text = "LEVEL MATCH: COMPLETE"
                            tvLastError.visibility = View.VISIBLE
                            val summary = StringBuilder("LEVEL MATCHED REFERENCE DIAGNOSTIC:\n")
                            val cA = report.conditionAOriginalRef
                            val cB = report.conditionBLevelMatchedRef
                            val cC = report.conditionCDeviceMic
                            summary.append("A (Orig Ref): WER=${"%.1f".format(cA.meanWer * 100)}%, CER=${"%.1f".format(cA.meanCer * 100)}%, Exact=${cA.exactMatchCount}/$repetitions\n")
                            summary.append("B (Level-Matched Ref): WER=${"%.1f".format(cB.meanWer * 100)}%, CER=${"%.1f".format(cB.meanCer * 100)}%, Exact=${cB.exactMatchCount}/$repetitions\n")
                            summary.append("C (Device Mic): WER=${"%.1f".format(cC.meanWer * 100)}%, CER=${"%.1f".format(cC.meanCer * 100)}%, Exact=${cC.exactMatchCount}/$repetitions\n")
                            tvLastError.text = summary.toString().trim()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            tvStatus.text = "LEVEL MATCH: FAIL"
                            tvLastError.visibility = View.VISIBLE
                            tvLastError.text = "Error: ${e.message}"
                        }
                    }
                }.start()
            }
            "RUN_SAME_SENTENCE_MIC_DIAGNOSTIC", "org.itantra.action.RUN_SAME_SENTENCE_MIC_DIAGNOSTIC" -> {
                val durationMs = intent?.getLongExtra("durationMs", 8000L) ?: 8000L
                val repetitions = intent?.getIntExtra("repetitions", 3) ?: 3
                Thread {
                    runOnUiThread {
                        tvStatus.text = "SAME SENTENCE: RECORDING..."
                    }
                    try {
                        val report = kotlinx.coroutines.runBlocking {
                            org.itantra.core.stt.diagnostic.SameSentenceLiveMicDiagnosticRunner.recordAndEvaluate(
                                applicationContext,
                                captureDurationMs = durationMs,
                                repetitions = repetitions
                            )
                        }
                        runOnUiThread {
                            tvStatus.text = "SAME SENTENCE: COMPLETE"
                            tvLastError.visibility = View.VISIBLE
                            val summary = StringBuilder("SAME-SENTENCE DIAGNOSTIC RESULT:\n")
                            val cA = report.fixtureReport
                            val cB = report.micReport
                            summary.append("A (Fixture): WER=${"%.1f".format(cA.meanWer * 100)}%, CER=${"%.1f".format(cA.meanCer * 100)}%, Exact=${cA.exactMatchCount}/$repetitions\n")
                            summary.append("B (Live Mic): WER=${"%.1f".format(cB.meanWer * 100)}%, CER=${"%.1f".format(cB.meanCer * 100)}%, Exact=${cB.exactMatchCount}/$repetitions\n")
                            tvLastError.text = summary.toString().trim()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            tvStatus.text = "SAME SENTENCE: FAIL"
                            tvLastError.visibility = View.VISIBLE
                            tvLastError.text = "Error: ${e.message}"
                        }
                    }
                }.start()
            }
            "RUN_AUDIOSOURCE_AB_ISOLATION", "org.itantra.action.RUN_AUDIOSOURCE_AB_ISOLATION" -> {
                val durationMs = intent?.getLongExtra("durationMs", 8000L) ?: 8000L
                val repetitions = intent?.getIntExtra("repetitions", 3) ?: 3
                Thread {
                    runOnUiThread {
                        tvStatus.text = "AUDIOSOURCE A/B: RECORDING..."
                    }
                    try {
                        val report = kotlinx.coroutines.runBlocking {
                            org.itantra.core.stt.diagnostic.AudioSourceAbIsolationRunner.runFullAbIsolationTest(
                                applicationContext,
                                captureDurationMs = durationMs,
                                repetitions = repetitions
                            )
                        }
                        runOnUiThread {
                            tvStatus.text = "AUDIOSOURCE A/B: COMPLETE"
                            tvLastError.visibility = View.VISIBLE
                            val summary = StringBuilder("AUDIOSOURCE A/B RESULTS:\n")
                            val rA = report.reportA
                            val rB = report.reportB
                            summary.append("A (VOICE_REC): WER=${"%.1f".format(rA.meanWer * 100)}%, CER=${"%.1f".format(rA.meanCer * 100)}%, Exact=${rA.exactMatchCount}/$repetitions\n")
                            summary.append("B (MIC):       WER=${"%.1f".format(rB.meanWer * 100)}%, CER=${"%.1f".format(rB.meanCer * 100)}%, Exact=${rB.exactMatchCount}/$repetitions\n")
                            tvLastError.text = summary.toString().trim()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            tvStatus.text = "AUDIOSOURCE A/B: FAIL"
                            tvLastError.visibility = View.VISIBLE
                            tvLastError.text = "Error: ${e.message}"
                        }
                    }
                }.start()
            }
            "RUN_AUDIOSOURCE_PRODUCTION_EFFECT", "org.itantra.action.RUN_AUDIOSOURCE_PRODUCTION_EFFECT" -> {
                val durationMs = intent?.getLongExtra("durationMs", 8000L) ?: 8000L
                val repetitions = intent?.getIntExtra("repetitions", 3) ?: 3
                Thread {
                    runOnUiThread {
                        tvStatus.text = "AUDIOSOURCE EFFECT: RECORDING..."
                    }
                    try {
                        val report = kotlinx.coroutines.runBlocking {
                            org.itantra.core.stt.diagnostic.AudioSourceProductionEffectRunner.runProductionEffectExperiment(
                                applicationContext,
                                captureDurationMs = durationMs,
                                repetitions = repetitions
                            )
                        }
                        runOnUiThread {
                            tvStatus.text = "AUDIOSOURCE EFFECT: COMPLETE"
                            tvLastError.visibility = View.VISIBLE
                            val summary = StringBuilder("AUDIOSOURCE PRODUCTION EFFECT RESULTS:\n")
                            val rA = report.conditionA
                            val rB = report.conditionB
                            summary.append("A (VOICE_REC): WER=${"%.1f".format(rA.meanWer * 100)}%, CER=${"%.1f".format(rA.meanCer * 100)}%, Exact=${rA.exactMatchCount}/$repetitions\n")
                            summary.append("B (MIC):       WER=${"%.1f".format(rB.meanWer * 100)}%, CER=${"%.1f".format(rB.meanCer * 100)}%, Exact=${rB.exactMatchCount}/$repetitions\n")
                            tvLastError.text = summary.toString().trim()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            tvStatus.text = "AUDIOSOURCE EFFECT: FAIL"
                            tvLastError.visibility = View.VISIBLE
                            tvLastError.text = "Error: ${e.message}"
                        }
                    }
                }.start()
            }
            "RUN_MIC_LOOPBACK_REF_COMPARISON", "org.itantra.action.RUN_MIC_LOOPBACK_REF_COMPARISON" -> {
                val durationMs = intent?.getLongExtra("durationMs", 8000L) ?: 8000L
                val repetitions = intent?.getIntExtra("repetitions", 3) ?: 3
                Thread {
                    runOnUiThread {
                        tvStatus.text = "LOOPBACK REF: RECORDING..."
                    }
                    try {
                        val report = kotlinx.coroutines.runBlocking {
                            org.itantra.core.stt.diagnostic.MicLoopbackReferenceComparisonRunner.runComparison(
                                applicationContext,
                                captureDurationMs = durationMs,
                                repetitions = repetitions
                            )
                        }
                        runOnUiThread {
                            tvStatus.text = "LOOPBACK REF: COMPLETE"
                            tvLastError.visibility = View.VISIBLE
                            val summary = StringBuilder("MIC LOOPBACK REF COMPARISON:\n")
                            val rA = report.referenceReport
                            val rB = report.micReport
                            summary.append("Ref: Mean WER=${"%.1f".format(rA.meanWer * 100)}%, CER=${"%.1f".format(rA.meanCer * 100)}%, Exact=${rA.exactMatchCount}/$repetitions\n")
                            summary.append("Mic: Mean WER=${"%.1f".format(rB.meanWer * 100)}%, CER=${"%.1f".format(rB.meanCer * 100)}%, Exact=${rB.exactMatchCount}/$repetitions\n")
                            tvLastError.text = summary.toString().trim()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            tvStatus.text = "LOOPBACK REF: FAIL"
                            tvLastError.visibility = View.VISIBLE
                            tvLastError.text = "Error: ${e.message}"
                        }
                    }
                }.start()
            }
            "RUN_ACOUSTIC_PLAYBACK_LOOPBACK", "org.itantra.action.RUN_ACOUSTIC_PLAYBACK_LOOPBACK" -> {
                val durationMs = intent?.getLongExtra("durationMs", 8000L) ?: 8000L
                val repIndex = intent?.getIntExtra("repIndex", 1) ?: 1
                val isBaseline = intent?.getBooleanExtra("isBaseline", false) ?: false
                Thread {
                    try {
                        if (isBaseline) {
                            runOnUiThread { tvStatus.text = "ACOUSTIC BASELINE: RUNNING..." }
                            val engine = org.itantra.core.stt.SherpaWhisperAsrEngine(context = applicationContext, defaultLanguage = "en")
                            try {
                                val (stats, runs) = kotlinx.coroutines.runBlocking {
                                    org.itantra.core.stt.diagnostic.AcousticPlaybackMicLoopbackRunner.evaluateBaseline(applicationContext, engine, 3)
                                }
                                runOnUiThread {
                                    tvStatus.text = "ACOUSTIC BASELINE: COMPLETE"
                                    tvLastError.visibility = View.VISIBLE
                                    val summary = StringBuilder("BASELINE REFERENCE (3 RUNS):\n")
                                    for (r in runs) {
                                        summary.append("Run ${r.runIndex}: WER=${"%.1f".format(r.wer * 100)}%, CER=${"%.1f".format(r.cer * 100)}%, Time=${r.inferenceTimeMs}ms -> \"${r.rawHypothesis}\"\n")
                                    }
                                    tvLastError.text = summary.toString().trim()
                                }
                            } finally {
                                engine.close()
                            }
                        } else if (intent?.hasExtra("evalWav") == true) {
                            val evalWav = intent.getStringExtra("evalWav") ?: "acoustic_loopback_rep_2.wav"
                            runOnUiThread { tvStatus.text = "ACOUSTIC EVAL ($evalWav): RUNNING..." }
                            val engine = org.itantra.core.stt.SherpaWhisperAsrEngine(context = applicationContext, defaultLanguage = "en")
                            try {
                                val result = kotlinx.coroutines.runBlocking {
                                    org.itantra.core.stt.diagnostic.AcousticPlaybackMicLoopbackRunner.evaluateWavFile(applicationContext, engine, evalWav, repIndex)
                                }
                                runOnUiThread {
                                    tvStatus.text = "ACOUSTIC EVAL ($evalWav): COMPLETE"
                                    tvLastError.visibility = View.VISIBLE
                                    val r = result.asrResult
                                    val stats = result.stats
                                    tvLastError.text = "ACOUSTIC LOOPBACK EVAL ($evalWav) RESULT:\n" +
                                            "Raw: \"${r.rawHypothesis}\"\n" +
                                            "WER: ${"%.1f".format(r.wer * 100)}%, CER: ${"%.1f".format(r.cer * 100)}%\n" +
                                            "RMS: ${"%.1f".format(stats.rmsDbfs)} dBFS (Peak: ${stats.peakAbsolute})\n" +
                                            "Time: ${r.inferenceTimeMs}ms (RTF ${"%.4f".format(r.rtf)})"
                                }
                            } finally {
                                engine.close()
                            }
                        } else {
                            runOnUiThread { tvStatus.text = "ACOUSTIC LOOPBACK (REP $repIndex): RECORDING..." }
                            val (samples, stats) = kotlinx.coroutines.runBlocking {
                                org.itantra.core.stt.diagnostic.AcousticPlaybackMicLoopbackRunner.recordSingleLoopback(applicationContext, repIndex, durationMs)
                            }
                            val engine = org.itantra.core.stt.SherpaWhisperAsrEngine(context = applicationContext, defaultLanguage = "en")
                            try {
                                val result = kotlinx.coroutines.runBlocking {
                                    org.itantra.core.stt.diagnostic.AcousticPlaybackMicLoopbackRunner.evaluateSingleCapture(engine, samples, 16000, repIndex, stats)
                                }
                                runOnUiThread {
                                    tvStatus.text = "ACOUSTIC LOOPBACK (REP $repIndex): COMPLETE"
                                    tvLastError.visibility = View.VISIBLE
                                    val r = result.asrResult
                                    tvLastError.text = "ACOUSTIC LOOPBACK REP $repIndex RESULT:\n" +
                                            "Raw: \"${r.rawHypothesis}\"\n" +
                                            "WER: ${"%.1f".format(r.wer * 100)}%, CER: ${"%.1f".format(r.cer * 100)}%\n" +
                                            "RMS: ${"%.1f".format(stats.rmsDbfs)} dBFS (Peak: ${stats.peakAbsolute})\n" +
                                            "Time: ${r.inferenceTimeMs}ms (RTF ${"%.4f".format(r.rtf)})"
                                }
                            } finally {
                                engine.close()
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            tvStatus.text = "ACOUSTIC LOOPBACK: FAIL"
                            tvLastError.visibility = View.VISIBLE
                            tvLastError.text = "Error: ${e.message}"
                        }
                    }
                }.start()
            }
            else -> {
                initComposition(autoStartOnReady = false)
            }
        }
    }

    private fun startE2eTransmitter(serverHost: String, serverPort: Int, targetLanguage: String = "hi") {
        tvLastError.visibility = View.GONE
        tvAsrTranscript.text = "[E2E Active ($targetLanguage) - Connecting to $serverHost:$serverPort...]"
        tvStatus.text = "CONNECTING E2E..."

        Thread {
            try {
                val comp = AndroidTransceiverComposition(
                    context = applicationContext,
                    localEndpoint = org.itantra.core.communication.transport.TransportEndpoint("device-a-transmitter"),
                    defaultDestination = org.itantra.core.communication.transport.TransportEndpoint("device-b-receiver"),
                    connectionConfig = org.itantra.core.communication.connection.ConnectionConfig.Client(
                        host = serverHost,
                        port = serverPort,
                        timeoutMs = 8000
                    ),
                    audioConfig = audioConfig,
                    vadConfig = vadConfig,
                    targetLanguage = targetLanguage,
                    listener = createTransceiverListener()
                )

                Log.i("PHYSICAL_E2E", "[E2E-A] Initializing M1+M2+M3 transceiver composition on Device A...")
                val initRes = comp.initialize()
                if (initRes.isFailure) {
                    val err = "Failed to initialize composition: ${initRes.exceptionOrNull()?.message}"
                    Log.e("PHYSICAL_E2E", "[E2E-A-FAIL] $err")
                    runOnUiThread {
                        tvStatus.text = "INIT ERROR"
                        tvLastError.text = err
                        tvLastError.visibility = View.VISIBLE
                    }
                    return@Thread
                }

                Log.i("PHYSICAL_E2E", "[E2E-A] Connecting TCP client to Device B at $serverHost:$serverPort...")
                val connectRes = kotlinx.coroutines.runBlocking { comp.connect() }
                if (connectRes.isFailure) {
                    val err = "Failed to connect to receiver: ${connectRes.exceptionOrNull()?.message}"
                    Log.e("PHYSICAL_E2E", "[E2E-A-FAIL] $err")
                    runOnUiThread {
                        tvStatus.text = "CONNECT FAILED"
                        tvLastError.text = err
                        tvLastError.visibility = View.VISIBLE
                    }
                    return@Thread
                }

                Log.i("PHYSICAL_E2E", "[E2E-A] Starting live speech capture & transcription pipeline...")
                val startRes = comp.start()
                if (startRes.isFailure) {
                    val err = "Failed to start transceiver coordinator: ${startRes.exceptionOrNull()?.message}"
                    Log.e("PHYSICAL_E2E", "[E2E-A-FAIL] $err")
                    runOnUiThread {
                        tvStatus.text = "START FAILED"
                        tvLastError.text = err
                        tvLastError.visibility = View.VISIBLE
                    }
                    return@Thread
                }

                composition = comp
                Log.i("PHYSICAL_E2E", "[E2E-A-READY] Transceiver connected and listening. Speak into Device A microphone!")
                runOnUiThread {
                    tvStatus.text = "E2E LIVE (Connected to $serverHost:$serverPort)"
                    tvVadState.text = "LISTENING FOR SPEECH..."
                    tvVadState.setTextColor(Color.parseColor("#22C55E"))
                    updateButtonStates(isRunning = true)
                }
            } catch (e: Exception) {
                Log.e("PHYSICAL_E2E", "[E2E-A-EXCEPTION] Error starting E2E transmitter", e)
                runOnUiThread {
                    tvStatus.text = "ERROR"
                    tvLastError.text = "Exception: ${e.message}"
                    tvLastError.visibility = View.VISIBLE
                }
            }
        }.start()
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tv_status)
        tvConnectionState = findViewById(R.id.tv_connection_state)
        viewConnectionDot = findViewById(R.id.view_connection_dot)
        btnThemeToggle = findViewById(R.id.btn_theme_toggle)
        btnModeServer = findViewById(R.id.btn_mode_server)
        btnModeClient = findViewById(R.id.btn_mode_client)
        layoutClientIpRow = findViewById(R.id.layout_client_ip_row)
        etPeerIp = findViewById(R.id.et_peer_ip)
        btnConnectPeer = findViewById(R.id.btn_connect_peer)
        tvServerRoleHint = findViewById(R.id.tv_server_role_hint)
        btnLangEn = findViewById(R.id.btn_lang_en)
        btnLangHi = findViewById(R.id.btn_lang_hi)
        tvYouTranscript = findViewById(R.id.tv_you_transcript)
        tvYouLangBadge = findViewById(R.id.tv_you_lang_badge)
        tvRemoteTranscript = findViewById(R.id.tv_remote_transcript)
        tvRemoteLangBadge = findViewById(R.id.tv_remote_lang_badge)
        tvRemotePlaybackStatus = findViewById(R.id.tv_remote_playback_status)
        btnPtt = findViewById(R.id.btn_ptt)
        tvPttLabel = findViewById(R.id.tv_ptt_label)
        tvLastError = findViewById(R.id.tv_last_error)

        updateThemeToggleButton()

        // Diagnostic & compatibility bindings
        tvVadState = findViewById(R.id.tv_vad_state)
        tvVadDetails = findViewById(R.id.tv_vad_details)
        tvTelemetryInfo = findViewById(R.id.tv_telemetry_info)
        tvAsrTranscript = findViewById(R.id.tv_asr_transcript)
        tvAsrMetrics = findViewById(R.id.tv_asr_metrics)
        btnStart = findViewById(R.id.btn_start_capture)
        btnStop = findViewById(R.id.btn_stop_capture)
        btnRelease = findViewById(R.id.btn_release_capture)
    }

    private fun setupListeners() {
        btnThemeToggle.setOnClickListener {
            val nextMode = when (currentThemeMode) {
                ThemeMode.SYSTEM -> ThemeMode.LIGHT
                ThemeMode.LIGHT -> ThemeMode.DARK
                ThemeMode.DARK -> ThemeMode.SYSTEM
            }
            applyThemeMode(nextMode, persist = true)
        }

        btnModeServer.setOnClickListener {
            updateNetworkRoleSelection(NetworkRole.SERVER)
        }

        btnModeClient.setOnClickListener {
            updateNetworkRoleSelection(NetworkRole.CLIENT)
        }

        btnConnectPeer.setOnClickListener {
            val ip = etPeerIp.text.toString().trim()
            if (ip.isBlank()) {
                showErrorBanner("Please enter peer receiver IP address")
                return@setOnClickListener
            }
            // Auto dismiss soft keyboard
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.hideSoftInputFromWindow(etPeerIp.windowToken, 0)

            selectedRole = NetworkRole.CLIENT
            peerHost = ip
            hideBanner()
            Log.i("PHYSICAL_E2E", "ROLE_SELECTED: TRANSMITTER")
            Log.i("PHYSICAL_E2E", "CLIENT_CONFIG: host=$ip, port=${org.itantra.core.communication.transport.TcpTransportConstants.DEFAULT_PORT}")
            val cfg = org.itantra.core.communication.connection.ConnectionConfig.Client(
                host = ip,
                port = org.itantra.core.communication.transport.TcpTransportConstants.DEFAULT_PORT,
                timeoutMs = 8000
            )
            reconnectWithConfig(cfg)
        }

        btnPtt.setOnClickListener {
            if (this.isRunning) {
                stopTransceiver()
            } else {
                if (hasAudioPermission()) {
                    startTransceiver()
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }

        btnLangEn.setOnClickListener {
            updateLanguageSelection("en")
        }

        btnLangHi.setOnClickListener {
            updateLanguageSelection("hi")
        }

        btnStart.setOnClickListener {
            if (hasAudioPermission()) {
                startTransceiver()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        btnStop.setOnClickListener {
            stopTransceiver()
        }

        btnRelease.setOnClickListener {
            releaseTransceiver()
        }
    }

    private fun updateNetworkRoleSelection(role: NetworkRole) {
        selectedRole = role
        stateHolder.onRoleChanged(role.name, role == NetworkRole.SERVER)
        if (role == NetworkRole.SERVER) {
            Log.i("PHYSICAL_E2E", "ROLE_SELECTED: RECEIVER")
            btnModeServer.setBackgroundResource(R.drawable.bg_chip_selected)
            btnModeServer.setTextColor(ContextCompat.getColor(this, R.color.button_selected_text))
            btnModeClient.setBackgroundResource(R.drawable.bg_chip_unselected)
            btnModeClient.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            layoutClientIpRow.visibility = View.GONE
            updateReceiverRoleHint()
            tvServerRoleHint.visibility = View.VISIBLE
            val cfg = org.itantra.core.communication.connection.ConnectionConfig.Server(
                port = org.itantra.core.communication.transport.TcpTransportConstants.DEFAULT_PORT
            )
            reconnectWithConfig(cfg)
        } else {
            Log.i("PHYSICAL_E2E", "ROLE_SELECTED: TRANSMITTER")
            btnModeClient.setBackgroundResource(R.drawable.bg_chip_selected)
            btnModeClient.setTextColor(ContextCompat.getColor(this, R.color.button_selected_text))
            btnModeServer.setBackgroundResource(R.drawable.bg_chip_unselected)
            btnModeServer.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            layoutClientIpRow.visibility = View.VISIBLE
            tvServerRoleHint.visibility = View.GONE
            val ip = etPeerIp.text.toString().trim()
            if (ip.isNotBlank()) {
                peerHost = ip
                Log.i("PHYSICAL_E2E", "CLIENT_CONFIG: host=$ip, port=${org.itantra.core.communication.transport.TcpTransportConstants.DEFAULT_PORT}")
                val cfg = org.itantra.core.communication.connection.ConnectionConfig.Client(
                    host = ip,
                    port = org.itantra.core.communication.transport.TcpTransportConstants.DEFAULT_PORT,
                    timeoutMs = 8000
                )
                reconnectWithConfig(cfg)
            } else {
                // Stop and release previous server transport so port 42173 is immediately released
                val oldComp = composition
                composition = null
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        oldComp?.release()
                    } catch (_: Exception) {}
                    isInitializing.set(false)
                }
                updateNetworkIndicator(org.itantra.core.communication.connection.ConnectionState.DISCONNECTED)
            }
        }
    }

    private fun reconnectWithConfig(config: org.itantra.core.communication.connection.ConnectionConfig) {
        val currentGen = compositionGeneration.incrementAndGet()
        val oldComp = composition
        composition = null
        val wasRunning = this.isRunning
        if (wasRunning) {
            stopTransceiver()
        }
        updateNetworkIndicator(org.itantra.core.communication.connection.ConnectionState.CONNECTING)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                oldComp?.release()
            } catch (e: Exception) {
                Log.w("MainActivity", "Error releasing previous composition", e)
            }
            runOnUiThread {
                initCompositionWithConfig(config, autoStartOnReady = wasRunning, expectedGen = currentGen)
            }
        }
    }

    private fun updateLanguageSelection(lang: String) {
        if (currentLanguage == lang && composition != null) return
        currentLanguage = lang
        stateHolder.onLanguageChanged(lang)
        if (lang == "hi") {
            btnLangHi.setBackgroundResource(R.drawable.bg_chip_selected)
            btnLangHi.setTextColor(ContextCompat.getColor(this, R.color.button_selected_text))
            btnLangEn.setBackgroundResource(R.drawable.bg_chip_unselected)
            btnLangEn.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            tvYouLangBadge.text = "HI"
        } else {
            btnLangEn.setBackgroundResource(R.drawable.bg_chip_selected)
            btnLangEn.setTextColor(ContextCompat.getColor(this, R.color.button_selected_text))
            btnLangHi.setBackgroundResource(R.drawable.bg_chip_unselected)
            btnLangHi.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            tvYouLangBadge.text = "EN"
        }

        val comp = composition
        if (comp != null && !comp.isReleased) {
            val wasRunning = this.isRunning
            stopTransceiver()
            val cfg = activeConnectionConfig()
            reconnectWithConfig(cfg)
        }
    }

    private fun activeConnectionConfig(): org.itantra.core.communication.connection.ConnectionConfig {
        val enteredIp = etPeerIp.text.toString().trim()
        val hostToUse = if (enteredIp.isNotBlank()) enteredIp else peerHost
        return if (selectedRole == NetworkRole.CLIENT && hostToUse.isNotBlank()) {
            org.itantra.core.communication.connection.ConnectionConfig.Client(
                host = hostToUse,
                port = org.itantra.core.communication.transport.TcpTransportConstants.DEFAULT_PORT,
                timeoutMs = 8000
            )
        } else {
            org.itantra.core.communication.connection.ConnectionConfig.Server(
                port = org.itantra.core.communication.transport.TcpTransportConstants.DEFAULT_PORT
            )
        }
    }

    private fun updateNetworkIndicator(state: org.itantra.core.communication.connection.ConnectionState) {
        runOnUiThread {
            val isServer = (selectedRole == NetworkRole.SERVER)
            stateHolder.onConnectionStateChanged(state, isServer = isServer)
            tvConnectionState.text = TransceiverUiFormatter.formatConnectionState(state, isServer = isServer)
            when (state) {
                org.itantra.core.communication.connection.ConnectionState.CONNECTED -> {
                    viewConnectionDot.backgroundTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(this@MainActivity, if (isServer) R.color.status_amber else R.color.status_green)
                    )
                }
                org.itantra.core.communication.connection.ConnectionState.CONNECTING -> {
                    viewConnectionDot.backgroundTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(this@MainActivity, R.color.status_amber)
                    )
                }
                org.itantra.core.communication.connection.ConnectionState.DISCONNECTED,
                org.itantra.core.communication.connection.ConnectionState.DISCONNECTING -> {
                    viewConnectionDot.backgroundTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(this@MainActivity, R.color.status_slate)
                    )
                }
                org.itantra.core.communication.connection.ConnectionState.FAILED -> {
                    viewConnectionDot.backgroundTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(this@MainActivity, R.color.status_red)
                    )
                }
            }
        }
    }

    private fun createTransceiverListener(): TransceiverListener {
        return object : TransceiverListener {
            override fun onStateChanged(state: TransceiverState) {
                stateHolder.onTransceiverStateChanged(state)
                runOnUiThread {
                    tvStatus.text = TransceiverUiFormatter.formatStatusBanner(state)
                    tvPttLabel.text = TransceiverUiFormatter.formatPttLabel(state)

                    when (state) {
                        TransceiverState.RUNNING -> {
                            btnPtt.setBackgroundResource(R.drawable.bg_ptt_active)
                            tvVadState.text = "ACTIVE / LISTENING"
                            tvVadState.setTextColor(Color.parseColor("#22C55E"))
                            updateButtonStates(isRunning = true)
                        }
                        TransceiverState.STARTING -> {
                            btnPtt.setBackgroundResource(R.drawable.bg_ptt_idle)
                            tvVadState.text = "STARTING..."
                            tvVadState.setTextColor(Color.parseColor("#EAB308"))
                            updateButtonStates(isRunning = true)
                        }
                        TransceiverState.STOPPING -> {
                            btnPtt.setBackgroundResource(R.drawable.bg_ptt_idle)
                            tvVadState.text = "STOPPING..."
                            tvVadState.setTextColor(Color.parseColor("#EAB308"))
                            updateButtonStates(isRunning = false)
                        }
                        TransceiverState.STOPPED -> {
                            btnPtt.setBackgroundResource(R.drawable.bg_ptt_idle)
                            tvVadState.text = "STOPPED"
                            tvVadState.setTextColor(Color.parseColor("#94A3B8"))
                            updateButtonStates(isRunning = false)
                        }
                        TransceiverState.RELEASED -> {
                            btnPtt.setBackgroundResource(R.drawable.bg_ptt_idle)
                            tvVadState.text = "RELEASED"
                            tvVadState.setTextColor(Color.parseColor("#94A3B8"))
                            updateButtonStates(isRunning = false)
                        }
                        TransceiverState.FAILED -> {
                            btnPtt.setBackgroundResource(R.drawable.bg_ptt_idle)
                            tvVadState.text = "FAILED"
                            tvVadState.setTextColor(Color.parseColor("#EF4444"))
                            updateButtonStates(isRunning = false)
                        }
                    }
                }
            }

            override fun onOutboundSpeechMessage(message: SpeechMessage) {
                outboundMessagesCount++
                stateHolder.onOutboundSpeechMessage(message)
                Log.i("PHYSICAL_E2E", "[E2E-LOCAL-OUTBOUND] Outbound SpeechMessage #$outboundMessagesCount: text='${message.text}', lang='${message.language}', ts=${message.timestamp}, conf=${message.confidence}")
                Log.i("PHYSICAL_E2E", "[E2E-A-OUTBOUND] Outbound SpeechMessage #$outboundMessagesCount: text='${message.text}', lang='${message.language}'")
                runOnUiThread {
                    tvYouTranscript.text = TransceiverUiFormatter.formatOutboundTranscript(message)
                    tvYouLangBadge.text = message.language.uppercase()
                    tvStatus.text = "Speech recognized & sent"
                    tvAsrTranscript.text = TransceiverUiFormatter.formatOutboundTranscript(message)
                    tvAsrMetrics.text = TransceiverUiFormatter.formatOutboundMetrics(message)
                    tvVadDetails.text = "Outbound Messages: $outboundMessagesCount\nInbound Messages: $inboundMessagesCount"
                }
            }

            override fun onInboundSpeechMessage(message: SpeechMessage) {
                inboundMessagesCount++
                stateHolder.onInboundSpeechMessage(message)
                Log.i("PHYSICAL_E2E", "[E2E-REMOTE-INBOUND] Inbound SpeechMessage #$inboundMessagesCount: text='${message.text}', lang='${message.language}', ts=${message.timestamp}")
                Log.i("PHYSICAL_E2E", "[E2E-B-INBOUND] Inbound SpeechMessage #$inboundMessagesCount: text='${message.text}', lang='${message.language}'")
                runOnUiThread {
                    val cleanText = if (message.text.isNotBlank()) "\"${message.text.trim()}\"" else "[Empty]"
                    tvRemoteTranscript.text = cleanText
                    tvRemoteLangBadge.text = message.language.uppercase()
                    tvRemotePlaybackStatus.visibility = View.GONE
                    tvStatus.text = "Received incoming speech"
                    tvAsrTranscript.text = TransceiverUiFormatter.formatInboundTranscript(message)
                    tvAsrMetrics.text = TransceiverUiFormatter.formatInboundMetrics(message)
                    tvVadDetails.text = "Outbound Messages: $outboundMessagesCount\nInbound Messages: $inboundMessagesCount"
                }
            }

            override fun onInboundPlaybackCompleted(message: SpeechMessage, audio: TtsAudio) {
                stateHolder.onInboundPlaybackCompleted(message, audio)
                Log.i("PHYSICAL_E2E", "[E2E-B-PLAYBACK] Inbound playback completed: text='${message.text}', samples=${audio.sampleCount} @ ${audio.sampleRate}Hz")
                runOnUiThread {
                    tvRemotePlaybackStatus.text = TransceiverUiFormatter.formatPlaybackConfirmation(audio)
                    tvRemotePlaybackStatus.visibility = View.VISIBLE
                    tvStatus.text = "TTS playback complete"
                    tvTelemetryInfo.text = TransceiverUiFormatter.formatPlaybackMetrics(audio)
                }
            }

            override fun onError(error: TransceiverError) {
                stateHolder.onError(error)
                if (TransceiverUiFormatter.isReceiverOnlyUnboundTransportError(error)) {
                    runOnUiThread {
                        tvStatus.text = "Receiver Ready — Waiting for Peer"
                        hideBanner()
                    }
                    return
                }
                val (category, detail) = TransceiverUiFormatter.formatError(error)
                val userFriendly = TransceiverUiFormatter.formatUserFriendlyError(error, peerHost)
                Log.e("MainActivity", "TransceiverError [$category]: $detail", error.cause)
                runOnUiThread {
                    tvStatus.text = "Error ($category)"
                    showErrorBanner(userFriendly)
                    updateButtonStates(isRunning = false)
                }
            }
        }
    }

    private fun initComposition(autoStartOnReady: Boolean = false) {
        val gen = compositionGeneration.incrementAndGet()
        initCompositionWithConfig(activeConnectionConfig(), autoStartOnReady, expectedGen = gen)
    }

    private fun initCompositionWithConfig(
        config: org.itantra.core.communication.connection.ConnectionConfig,
        autoStartOnReady: Boolean = false,
        expectedGen: Int = compositionGeneration.get()
    ) {
        val existing = composition
        if (existing != null && !existing.isReleased) {
            if (autoStartOnReady) startTransceiver()
            return
        }
        val gen = expectedGen
        tvStatus.text = "Initializing neural models…"
        updateNetworkIndicator(org.itantra.core.communication.connection.ConnectionState.CONNECTING)

        Thread {
            try {
                if (compositionGeneration.get() != gen) return@Thread

                Log.i("MainActivity", "Starting composition initialization in ${config::class.simpleName} mode (gen=$gen)...")
                val comp = AndroidTransceiverComposition(
                    context = applicationContext,
                    connectionConfig = config,
                    audioConfig = audioConfig,
                    vadConfig = vadConfig,
                    targetLanguage = currentLanguage,
                    listener = createTransceiverListener()
                )

                if (compositionGeneration.get() != gen) {
                    kotlinx.coroutines.runBlocking { comp.release() }
                    return@Thread
                }

                if (config is org.itantra.core.communication.connection.ConnectionConfig.Client) {
                    Log.i("PHYSICAL_E2E", "ROLE_SELECTED: TRANSMITTER")
                    Log.i("PHYSICAL_E2E", "CLIENT_CONFIG: host=${config.host}, port=${config.port}")
                } else {
                    Log.i("PHYSICAL_E2E", "ROLE_SELECTED: RECEIVER")
                }

                // Attach truthful connection state listener while preserving adapter binding
                comp.connectionManager.bindTo(comp.transportAdapter, object : org.itantra.core.communication.connection.ConnectionListener {
                    override fun onStateChanged(state: org.itantra.core.communication.connection.ConnectionState) {
                        if (compositionGeneration.get() == gen) {
                            updateNetworkIndicator(state)
                        }
                    }

                    override fun onConnected(transport: org.itantra.core.communication.transport.MessageTransport) {
                        if (compositionGeneration.get() == gen) {
                            if (config is org.itantra.core.communication.connection.ConnectionConfig.Client) {
                                Log.i("PHYSICAL_E2E", "SOCKET_CONNECTED: Client mode")
                            } else {
                                Log.i("PHYSICAL_E2E", "SOCKET_CONNECTED: Server mode")
                            }
                            updateNetworkIndicator(org.itantra.core.communication.connection.ConnectionState.CONNECTED)
                        }
                    }

                    override fun onDisconnected() {
                        if (compositionGeneration.get() == gen) {
                            updateNetworkIndicator(org.itantra.core.communication.connection.ConnectionState.DISCONNECTED)
                        }
                    }

                    override fun onError(message: String, cause: Throwable?) {
                        Log.e("MainActivity", "ConnectionListener.onError: $message", cause)
                        if (compositionGeneration.get() == gen) {
                            updateNetworkIndicator(org.itantra.core.communication.connection.ConnectionState.FAILED)
                            val userFriendly = TransceiverUiFormatter.formatUserFriendlyConnectionError(message, peerHost)
                            runOnUiThread {
                                showErrorBanner(userFriendly)
                            }
                        }
                    }
                })

                val initRes = comp.initialize()
                if (compositionGeneration.get() != gen) {
                    kotlinx.coroutines.runBlocking { comp.release() }
                    return@Thread
                }

                if (initRes.isFailure) {
                    runOnUiThread {
                        if (compositionGeneration.get() == gen) {
                            tvStatus.text = "Init Error"
                            val errMsg = initRes.exceptionOrNull()?.message ?: "Initialization failed"
                            Log.e("MainActivity", "Composition Init Error: $errMsg", initRes.exceptionOrNull())
                            showErrorBanner(TransceiverUiFormatter.formatUserFriendlyConnectionError(errMsg, peerHost))
                            updateNetworkIndicator(org.itantra.core.communication.connection.ConnectionState.FAILED)
                        }
                    }
                    return@Thread
                }

                // Connect / bind network transport
                if (config is org.itantra.core.communication.connection.ConnectionConfig.Client) {
                    Log.i("PHYSICAL_E2E", "CLIENT_CONNECT")
                }
                val connectRes = kotlinx.coroutines.runBlocking { comp.connect() }
                if (compositionGeneration.get() != gen) {
                    kotlinx.coroutines.runBlocking { comp.release() }
                    return@Thread
                }

                if (connectRes.isFailure) {
                    val errMsg = connectRes.exceptionOrNull()?.message ?: "Network transport connection failed"
                    Log.w("MainActivity", "Initial transport connection warning: $errMsg")
                    updateNetworkIndicator(org.itantra.core.communication.connection.ConnectionState.FAILED)
                } else {
                    updateNetworkIndicator(org.itantra.core.communication.connection.ConnectionState.CONNECTED)
                }

                runOnUiThread {
                    if (compositionGeneration.get() == gen) {
                        composition = comp
                        tvStatus.text = "Ready"
                        Log.i("MainActivity", "Transceiver composition initialized & transport connected successfully (gen=$gen).")
                        if (autoStartOnReady) {
                            startTransceiver()
                        }
                    } else {
                        CoroutineScope(Dispatchers.IO).launch { comp.release() }
                    }
                }
            } catch (ex: Exception) {
                if (compositionGeneration.get() == gen) {
                    Log.e("MainActivity", "Composition Exception: ${ex.message}", ex)
                    runOnUiThread {
                        tvStatus.text = "Init Error"
                        showErrorBanner(TransceiverUiFormatter.formatUserFriendlyConnectionError(ex.message ?: "Initialization error", peerHost))
                        updateNetworkIndicator(org.itantra.core.communication.connection.ConnectionState.FAILED)
                    }
                }
            }
        }.start()
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startTransceiver() {
        hideBanner()
        tvStatus.text = "Listening for speech…"
        tvAsrTranscript.text = "[Transceiver active - Listening for speech...]"
        tvAsrMetrics.text = "Outbound: ready | Inbound: ready"
        outboundMessagesCount = 0
        inboundMessagesCount = 0

        val comp = composition
        if (comp == null || comp.isReleased) {
            initComposition(autoStartOnReady = true)
            return
        }

        if (!comp.connectionManager.isConnected) {
            CoroutineScope(Dispatchers.IO).launch {
                if (comp.connectionConfig is org.itantra.core.communication.connection.ConnectionConfig.Client) {
                    Log.i("PHYSICAL_E2E", "CLIENT_CONNECT")
                }
                comp.connect()
            }
        }

        val result = comp.start()
        if (result.isFailure) {
            val error = result.exceptionOrNull()?.message ?: "Transceiver start failed"
            Log.e("MainActivity", "Transceiver start failed: $error", result.exceptionOrNull())
            tvStatus.text = "Start Failed"
            showErrorBanner(error)
            updateButtonStates(isRunning = false)
        }
    }

    private fun stopTransceiver() {
        composition?.stop()
        tvVadState.text = "STOPPED"
        tvVadState.setTextColor(Color.parseColor("#94A3B8"))
        updateButtonStates(isRunning = false)
    }

    private fun releaseTransceiver() {
        val comp = composition
        composition = null
        tvStatus.text = "Releasing transceiver…"
        updateNetworkIndicator(org.itantra.core.communication.connection.ConnectionState.DISCONNECTED)
        Thread {
            runBlocking {
                comp?.release()
            }
            runOnUiThread {
                tvStatus.text = "Offline"
                tvVadState.text = "UNLOADED"
                tvVadState.setTextColor(Color.parseColor("#94A3B8"))
                updateButtonStates(isRunning = false)
            }
        }.start()
    }

    private fun updateButtonStates(isRunning: Boolean) {
        this.isRunning = isRunning
        btnStart.isEnabled = !isRunning
        btnStop.isEnabled = isRunning
        btnRelease.isEnabled = !isRunning
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            composition?.stop()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val comp = composition
        composition = null
        if (comp != null && !comp.isReleased) {
            CoroutineScope(Dispatchers.IO).launch {
                comp.release()
            }
        }
    }
}
