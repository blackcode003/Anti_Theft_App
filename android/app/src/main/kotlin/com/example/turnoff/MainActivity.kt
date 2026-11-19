package com.example.turnoff

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, AdminReceiver::class.java)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "screen_control")
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "isAdmin" -> result.success(dpm.isAdminActive(admin))

                    "requestAdmin" -> {
                        val i = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                        i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                        i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "Needed to turn off the screen by voice.")
                        startActivity(i)
                        result.success(null)
                    }

                    "lockScreen" -> {
                        if (dpm.isAdminActive(admin)) { dpm.lockNow(); result.success(true) }
                        else result.success(false)
                    }

                    "startListening" -> {
                        val i = Intent(this, VoiceService::class.java)
                        i.action = VoiceService.ACTION_START
                        val list = call.argument<List<String>>("triggers")
                        if (list != null) i.putStringArrayListExtra("triggers", ArrayList(list))
                        ContextCompat.startForegroundService(this, i)
                        result.success(null)
                    }

                    "stopListening" -> {
                        val i = Intent(this, VoiceService::class.java)
                        i.action = VoiceService.ACTION_STOP
                        startService(i)
                        result.success(null)
                    }

                    "isListening" -> result.success(VoiceService.running)

                    "openBatterySettings" -> {
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        result.success(null)
                    }

                    else -> result.notImplemented()
                }
            }
    }
}
