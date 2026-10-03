package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.WakeActivity
import com.example.data.ShakePreferences
import com.example.receiver.NotificationActionReceiver
import com.example.util.VibratorHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

class ShakeWakeService : Service(), SensorEventListener {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private lateinit var sensorManager: SensorManager
    private lateinit var powerManager: PowerManager
    private lateinit var prefs: ShakePreferences

    private var accelerometer: Sensor? = null
    private var proximitySensor: Sensor? = null
    private var cpuWakeLock: PowerManager.WakeLock? = null
    private var screenWakeLock: PowerManager.WakeLock? = null

    private var isScreenOn = true
    private var isPocketNear = false

    // Shake detection state
    private var lastShakeTimestamp = 0L
    private var shakeCount = 0
    private var lastDirectionX = 0f
    private var lastDirectionY = 0f
    private var lastDirectionZ = 0f
    private var lastDirectionChangeTime = 0L

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> isScreenOn = true
                Intent.ACTION_SCREEN_OFF -> isScreenOn = false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = ShakePreferences.getInstance(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        createNotificationChannel()
        registerScreenStateReceiver()
        acquireCpuWakeLock()
        registerSensors()

        _isServiceRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            startForeground(NOTIFICATION_ID, notification, fgsType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    private fun registerScreenStateReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenStateReceiver, filter)
        isScreenOn = powerManager.isInteractive
    }

    private fun acquireCpuWakeLock() {
        try {
            cpuWakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ShakeWake:CpuSensorWakeLock"
            )
            cpuWakeLock?.setReferenceCounted(false)
            cpuWakeLock?.acquire()
        } catch (_: Exception) {}
    }

    private fun registerSensors() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        proximitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                val distance = event.values[0]
                val maxRange = event.sensor.maximumRange
                isPocketNear = distance < maxRange && distance < 4.0f
            }

            Sensor.TYPE_ACCELEROMETER -> {
                handleAccelerometerData(event.values[0], event.values[1], event.values[2])
            }
        }
    }

    private fun handleAccelerometerData(x: Float, y: Float, z: Float) {
        val magnitude = sqrt(x * x + y * y + z * z)
        val netAcceleration = abs(magnitude - SensorManager.GRAVITY_EARTH)
        val currentTime = System.currentTimeMillis()

        // Broadcast real-time live sensor data for the in-app calibrator
        _liveSensorData.value = LiveSensorReading(
            netAcceleration = netAcceleration,
            rawMagnitude = magnitude,
            x = x,
            y = y,
            z = z,
            timestamp = currentTime
        )

        val settings = prefs.getSettings()
        if (!settings.isEnabled) return

        val threshold = settings.threshold
        val cooldownMs = (settings.cooldownSeconds * 1000).toLong()

        // Detect acceleration above threshold
        if (netAcceleration >= threshold) {
            // Check direction change to confirm physical shaking rather than uniform linear acceleration
            val dotProduct = (x * lastDirectionX) + (y * lastDirectionY) + (z * lastDirectionZ)
            val isReversal = dotProduct < 0

            if (currentTime - lastDirectionChangeTime > 500) {
                // Reset counter if too much time passed between shakes
                shakeCount = 1
                lastDirectionChangeTime = currentTime
            } else if (isReversal || settings.shakesRequired == 1) {
                shakeCount++
                lastDirectionChangeTime = currentTime
            }

            lastDirectionX = x
            lastDirectionY = y
            lastDirectionZ = z

            // Check if required shakes reached and cooldown elapsed
            if (shakeCount >= settings.shakesRequired && (currentTime - lastShakeTimestamp > cooldownMs)) {
                shakeCount = 0
                lastShakeTimestamp = currentTime

                // Check Pocket Mode
                if (settings.pocketMode && isPocketNear) {
                    return
                }

                // Check Screen-off only setting
                if (settings.screenOffOnly && isScreenOn) {
                    return
                }

                triggerWakeScreen(settings.vibrateOnWake)
            }
        }
    }

    private fun triggerWakeScreen(vibrate: Boolean) {
        prefs.recordWakeEvent()

        if (vibrate) {
            VibratorHelper.vibrateWake(this)
        }

        serviceScope.launch {
            _wakeEvents.emit(System.currentTimeMillis())
        }

        // 1. Acquire bright screen wake lock
        try {
            @Suppress("DEPRECATION")
            screenWakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                "ShakeWake:ScreenWakeLock"
            )
            screenWakeLock?.acquire(3000)
        } catch (_: Exception) {}

        // 2. Launch WakeActivity with clear flags to bring display on
        val wakeIntent = Intent(this, WakeActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
        }
        startActivity(wakeIntent)
    }

    private fun buildForegroundNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Vol Down Action
        val volDownIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_VOL_DOWN
        }
        val volDownPendingIntent = PendingIntent.getBroadcast(
            this,
            1,
            volDownIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Vol Up Action
        val volUpIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_VOL_UP
        }
        val volUpPendingIntent = PendingIntent.getBroadcast(
            this,
            2,
            volUpIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Wake Test Action
        val wakeIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_WAKE_SCREEN
        }
        val wakePendingIntent = PendingIntent.getBroadcast(
            this,
            3,
            wakeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val settings = prefs.getSettings()
        val sensitivityDesc = "Threshold: ${settings.threshold.toInt()} m/s² • Pocket Mode: ${if (settings.pocketMode) "ON" else "OFF"}"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ShakeWake is Active")
            .setContentText(sensitivityDesc)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (settings.showNotificationControls) {
            builder.addAction(R.drawable.ic_launcher_foreground, "Vol -", volDownPendingIntent)
            builder.addAction(R.drawable.ic_launcher_foreground, "Vol +", volUpPendingIntent)
            builder.addAction(R.drawable.ic_launcher_foreground, "Wake", wakePendingIntent)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ShakeWake Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows ongoing status and virtual volume buttons for ShakeWake"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        _isServiceRunning.value = false
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (_: Exception) {}

        sensorManager.unregisterListener(this)

        try {
            if (cpuWakeLock?.isHeld == true) {
                cpuWakeLock?.release()
            }
            if (screenWakeLock?.isHeld == true) {
                screenWakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    companion object {
        const val CHANNEL_ID = "shake_wake_service_channel"
        const val NOTIFICATION_ID = 1001

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()

        private val _liveSensorData = MutableStateFlow(LiveSensorReading())
        val liveSensorData = _liveSensorData.asStateFlow()

        private val _wakeEvents = MutableSharedFlow<Long>(extraBufferCapacity = 5)
        val wakeEvents = _wakeEvents.asSharedFlow()

        fun start(context: Context) {
            val intent = Intent(context, ShakeWakeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ShakeWakeService::class.java)
            context.stopService(intent)
        }
    }
}

data class LiveSensorReading(
    val netAcceleration: Float = 0f,
    val rawMagnitude: Float = 9.8f,
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 9.8f,
    val timestamp: Long = 0L
)
