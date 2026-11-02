package com.arjunchakri.pockethud

import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var panel: View? = null
    private lateinit var batteryValue: TextView
    private lateinit var remainingValue: TextView
    private lateinit var powerValue: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var neonAnimator: ValueAnimator? = null

    private var previousLevel = -1
    private var previousAt = 0L
    private var drainPerHour: Double? = null

    private val tick = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, 5000)
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onCreate() {
        super.onCreate()
        foreground()
        if (!android.provider.Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        makePanel()
        handler.post(tick)
    }

    private fun foreground() {
        val channelId = "pocket_hud"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Pocket HUD", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        startForeground(
            41,
            builder.setContentTitle("Pocket HUD is active")
                .setContentText("Battery, remaining time and power")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setOngoing(true)
                .build()
        )
    }

    private fun makePanel() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(3), dp(6), dp(3))
            background = GradientDrawable().apply {
                setColor(Color.argb(179, 0, 0, 0))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), 0x6650DFA0)
            }
            elevation = dp(2).toFloat()
            setOnTouchListener(Drag())
            contentDescription = "Pocket HUD. Battery, power and remaining time. Drag to move."
        }

        batteryValue = compactValue("▣  —%", 0xFF65D6A0.toInt())
        root.addView(batteryValue)
        addSeparator(root)

        powerValue = compactValue("—W", Color.WHITE)
        root.addView(powerValue)
        addSeparator(root)

        remainingValue = compactValue("—", 0xFFD6DCE5.toInt())
        root.addView(remainingValue)

        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            -2, -2, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(10)
            y = dp(110)
        }
        panel = root
        windowManager.addView(root, params)
        startNeonReactor(root)
    }

    // Minimal Neon Reactor: a slow, low-intensity color drift on the 1dp outline only.
    private fun startNeonReactor(root: LinearLayout) {
        val background = root.background as? GradientDrawable ?: return
        val green = Color.rgb(72, 190, 135)
        val cyan = Color.rgb(65, 165, 195)
        neonAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 6500L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                val red = (Color.red(green) + (Color.red(cyan) - Color.red(green)) * fraction).toInt()
                val greenChannel = (Color.green(green) + (Color.green(cyan) - Color.green(green)) * fraction).toInt()
                val blue = (Color.blue(green) + (Color.blue(cyan) - Color.blue(green)) * fraction).toInt()
                background.setStroke(dp(1), Color.argb(185, red, greenChannel, blue))
            }
            start()
        }
    }

    private fun compactValue(initial: String, color: Int) = TextView(this).apply {
        text = initial
        textSize = 10f
        setTextColor(color)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        includeFontPadding = false
        setPadding(dp(2), dp(1), dp(2), dp(1))
    }

    private fun addSeparator(parent: LinearLayout) {
        parent.addView(TextView(this).apply {
            text = "·"
            textSize = 11f
            setTextColor(0xFF777F8A.toInt())
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(dp(3), 0, dp(3), 0)
        })
    }

    private fun update() {
        val now = System.currentTimeMillis()
        val batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val current = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val chargeCounter = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1

        batteryValue.text = if (level in 0..100) "▣ $level%" else "▣ —%"

        val watts = if (current != Int.MIN_VALUE && voltageMv > 0) {
            abs(current.toLong()) * voltageMv / 1_000_000_000.0
        } else null
        powerValue.text = watts?.let {
            String.format(Locale.getDefault(), "%.1fW", it)
        } ?: "—W"

        if (level in 1..100) {
            if (previousLevel >= 0 && previousAt > 0 && level < previousLevel) {
                val elapsedHours = (now - previousAt) / 3_600_000.0
                if (elapsedHours > 0) drainPerHour = (previousLevel - level) / elapsedHours
            }
            if (previousLevel < 0 || level < previousLevel) {
                previousLevel = level
                previousAt = now
            }
        }

        val discharging = !batteryManager.isCharging
        val directHours = if (
            discharging && current != Int.MIN_VALUE &&
            chargeCounter > 0 && abs(current.toLong()) > 20
        ) {
            (chargeCounter.toDouble() / abs(current.toLong()))
                .takeIf { it.isFinite() && it in 0.0..72.0 }
        } else null

        val remainingHours = directHours ?: if (discharging) {
            drainPerHour?.let { rate -> if (rate > 0) level / rate else null }
        } else null

        remainingValue.text = if (remainingHours == null) {
            "—"
        } else {
            val totalMinutes = (remainingHours * 60).toInt()
            String.format(Locale.getDefault(), "%dh%02d", totalMinutes / 60, totalMinutes % 60)
        }
    }

    private inner class Drag : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val params = panel?.layoutParams as? WindowManager.LayoutParams ?: return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(panel, params)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return true
            }
            return true
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        neonAnimator?.cancel()
        neonAnimator = null
        panel?.let { if (::windowManager.isInitialized) windowManager.removeView(it) }
        panel = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
