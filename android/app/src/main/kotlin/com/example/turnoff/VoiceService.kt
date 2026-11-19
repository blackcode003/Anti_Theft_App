package com.example.turnoff

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Foreground service that keeps listening for the trigger phrases. */
class VoiceService : Service() {

    companion object {
        const val ACTION_START = "com.example.turnoff.START"
        const val ACTION_STOP = "com.example.turnoff.STOP"
        private const val CHANNEL_ID = "voice_listen"
        private const val NOTIF_ID = 1
        @Volatile var running = false
    }

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private var triggers = listOf("turn off", "screen off", "lock screen", "sleep")
    private lateinit var audio: AudioManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(AUDIO_SERVICE) as AudioManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }

        intent?.getStringArrayListExtra("triggers")?.let { list ->
            if (list.isNotEmpty()) triggers = list.map { it.lowercase() }
        }

        showForeground()

        if (!active) {
            active = true
            running = true
            muteBeep(true)
            handler.post { startRecognizer() }
        }
        return START_NOT_STICKY
    }

    // ---------- notification / foreground ----------

    private fun showForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Voice listening", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, VoiceService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)

        val n = builder
            .setContentTitle("Listening for voice command")
            .setContentText("Say: " + triggers.joinToString(" / "))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stopIntent).build())
            .build()

        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    // ---------- speech recognition loop ----------

    private fun startRecognizer() {
        if (!active) return
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also {
            it.setRecognitionListener(listener)
        }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        recognizer?.startListening(i)
    }

    private fun restart(delayMs: Long = 300) {
        if (!active) return
        handler.postDelayed({ startRecognizer() }, delayMs)
    }

    private fun handleWords(list: List<String>?): Boolean {
        val text = list?.joinToString(" ")?.lowercase() ?: return false
        if (triggers.any { text.contains(it) }) {
            lockScreen()
            return true
        }
        return false
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResults(b: Bundle?) {
            if (handleWords(b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))) {
                recognizer?.cancel()
                restart(2000)
            }
        }

        override fun onResults(b: Bundle?) {
            if (handleWords(b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))) restart(2000)
            else restart()
        }

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> shutdown()
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> restart(1500)
                else -> restart(500) // no match / timeout / network etc.
            }
        }

        override fun onReadyForSpeech(p: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rms: Float) {}
        override fun onBufferReceived(buf: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(type: Int, p: Bundle?) {}
    }

    // ---------- helpers ----------

    private fun lockScreen() {
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, AdminReceiver::class.java)
        if (dpm.isAdminActive(admin)) dpm.lockNow()
    }

    /** Hides the little start/stop beep of the recognizer (best effort). */
    private fun muteBeep(mute: Boolean) {
        val dir = if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
        for (stream in intArrayOf(AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_SYSTEM)) {
            try { audio.adjustStreamVolume(stream, dir, 0) } catch (_: Exception) {}
        }
    }

    private fun shutdown() {
        active = false
        running = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        muteBeep(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (active) shutdown()
        super.onDestroy()
    }
}
