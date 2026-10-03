package com.example.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import com.example.WakeActivity
import com.example.data.ShakePreferences
import com.example.util.AudioHelper
import com.example.util.VibratorHelper
import kotlin.math.abs

class FloatingControlsService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingLayout: LinearLayout? = null
    private var isExpanded = false
    private lateinit var prefs: ShakePreferences

    override fun onCreate() {
        super.onCreate()
        prefs = ShakePreferences.getInstance(this)

        if (!hasOverlayPermission(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createFloatingWidget()
        _isFloatingActive.value = true
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingWidget() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 300
        }

        val alpha = prefs.getSettings().floatingControlsAlpha
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            this.alpha = alpha
        }

        // Expanded Panel Container
        val expandedPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            val bg = GradientDrawable().apply {
                setColor(0xEE121824.toInt())
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), 0xFF00E5FF.toInt())
            }
            background = bg
            setPadding(dp(8), dp(10), dp(8), dp(10))
        }

        // Vol Up Button
        val btnVolUp = createIconButton(android.R.drawable.ic_input_add, "Vol +") {
            AudioHelper.volumeUp(this)
            VibratorHelper.vibrateClick(this)
        }

        // Vol Down Button
        val btnVolDown = createIconButton(android.R.drawable.ic_delete, "Vol -") {
            AudioHelper.volumeDown(this)
            VibratorHelper.vibrateClick(this)
        }

        // Mute Button
        val btnMute = createIconButton(android.R.drawable.ic_lock_silent_mode, "Mute") {
            AudioHelper.toggleMute(this)
            VibratorHelper.vibrateClick(this)
        }

        // Wake / Screen Button
        val btnWake = createIconButton(android.R.drawable.ic_lock_power_off, "Wake") {
            VibratorHelper.vibrateWake(this)
            val wakeIntent = Intent(this, WakeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(wakeIntent)
        }

        expandedPanel.addView(btnVolUp)
        expandedPanel.addView(createSpacer(dp(8)))
        expandedPanel.addView(btnVolDown)
        expandedPanel.addView(createSpacer(dp(8)))
        expandedPanel.addView(btnMute)
        expandedPanel.addView(createSpacer(dp(8)))
        expandedPanel.addView(btnWake)

        // Floating Main Pill / Toggle Button
        val mainPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val pillBg = GradientDrawable().apply {
                setColor(0xFF00E5FF.toInt())
                cornerRadius = dp(24).toFloat()
            }
            background = pillBg
            val pad = dp(12)
            setPadding(pad, pad, pad, pad)
            elevation = dp(6).toFloat()
        }

        val pillIcon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_lock_power_off)
            setColorFilter(0xFF002329.toInt())
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
        }
        mainPill.addView(pillIcon)

        root.addView(expandedPanel)
        root.addView(createSpacer(dp(6)))
        root.addView(mainPill)

        // Drag and Click handling
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = false

        mainPill.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        isClick = false
                    }
                    params.x = initialX + dx
                    params.y = initialY + dy
                    try {
                        windowManager?.updateViewLayout(root, params)
                    } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        isExpanded = !isExpanded
                        expandedPanel.visibility = if (isExpanded) View.VISIBLE else View.GONE
                        VibratorHelper.vibrateClick(this)
                    } else {
                        // Snap to nearest screen edge
                        val screenWidth = resources.displayMetrics.widthPixels
                        val middle = screenWidth / 2
                        params.x = if (params.x + root.width / 2 < middle) dp(12) else screenWidth - root.width - dp(12)
                        try {
                            windowManager?.updateViewLayout(root, params)
                        } catch (_: Exception) {}
                    }
                    true
                }
                else -> false
            }
        }

        floatingLayout = root
        try {
            windowManager?.addView(root, params)
        } catch (_: Exception) {}
    }

    private fun createIconButton(iconRes: Int, contentDesc: String, onClick: () -> Unit): ImageView {
        val density = resources.displayMetrics.density
        val size = (44 * density).toInt()
        val pad = (10 * density).toInt()

        return ImageView(this).apply {
            setImageResource(iconRes)
            contentDescription = contentDesc
            setColorFilter(Color.WHITE)
            val bg = GradientDrawable().apply {
                setColor(0xFF1E2838.toInt())
                cornerRadius = size / 2f
            }
            background = bg
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(size, size)
            setOnClickListener { onClick() }
        }
    }

    private fun createSpacer(height: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _isFloatingActive.value = false
        floatingLayout?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private val _isFloatingActive = kotlinx.coroutines.flow.MutableStateFlow(false)
        val isFloatingActive = _isFloatingActive

        fun hasOverlayPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }

        fun start(context: Context) {
            if (hasOverlayPermission(context)) {
                val intent = Intent(context, FloatingControlsService::class.java)
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingControlsService::class.java)
            context.stopService(intent)
        }
    }
}
