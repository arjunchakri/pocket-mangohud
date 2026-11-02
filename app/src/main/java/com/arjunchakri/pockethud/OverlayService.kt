package com.arjunchakri.pockethud

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
            setPadding(dp(6), dp(5), dp(8), dp(5))
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = dp(19).toFloat()
                setStroke(dp(1), 0xFF30343B.toInt())
            }
            elevation = dp(8).toFloat()
            setOnTouchListener(Drag())
            contentDescription = "Pocket HUD. Drag to move."
        }

        val batteryCircle = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.BLACK)
                setStroke(dp(2), 0xFF65D6A0.toInt())
            }
        }
        batteryCircle.layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
        batteryCircle.addView(TextView(this).apply {
            text = "▣"
            textSize = 8f
            setTextColor(0xFF65D6A0.toInt())
            gravity = Gravity.CENTER
        })
        batteryValue = TextView(this).apply {
            text = "—%"
            textSize = 12f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        batteryCircle.addView(batteryValue)
        root.addView(batteryCircle)

        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), 0, 0, 0)
        }
        val remainingRow = metricRow("◷", 0xFFD6DCE5.toInt())
        remainingValue = remainingRow.second
        details.addView(remainingRow.first)

        val powerRow = metricRow("ϟ", 0xFF65D6FF.toInt())
        powerValue = powerRow.second
        details.addView(powerRow.first)

        root.addView(details, LinearLayout.LayoutParams(dp(78), -2))

        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            -2,
            -2,
            type,
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
    }

    private fun metricRow(symbol: String, iconColor: Int): Pair<LinearLayout, TextView> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(TextView(this).apply {
            text = symbol
            textSize = 13f
            setTextColor(iconColor)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }, LinearLayout.LayoutParams(dp(16), dp(18)))
        val value = TextView(this).apply {
            text = "—"
            textSize = 10f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            setPadding(dp(2), 0, 0, 0)
        }
        row.addView(value)
        return row to value
    }

    private fun update() {
        val now = System.currentTimeMillis()
        val batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val current = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val chargeCounter = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1

        batteryValue.text = if (level in 0..100) "$level%" else "—%"

        val watts = if (current != Int.MIN_VALUE && voltageMv > 0) {
            abs(current.toLong()) * voltageMv / 1_000_000_000.0
        } else null
        powerValue.text = watts?.let {
            String.format(Locale.getDefault(), "%.1f W", it)
        } ?: "— W"

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
        panel?.let { if (::windowManager.isInitialized) windowManager.removeView(it) }
        panel = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
