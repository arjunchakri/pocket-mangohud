package com.arjunchakri.pockethud

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
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
    private lateinit var batteryText: TextView
    private lateinit var timeText: TextView
    private lateinit var detailText: TextView
    private lateinit var sessionText: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var previousLevel = -1
    private var previousTime = 0L
    private var fallbackHours: Double? = null
    private var drainPercentPerHour: Double? = null
    private val sessionStarted = System.currentTimeMillis()

    private val updater = object : Runnable {
        override fun run() { updateReadout(); handler.postDelayed(this, 10_000L) }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int = 16) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), 0xFF46515F.toInt())
    }

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        if (!android.provider.Settings.canDrawOverlays(this)) { stopSelf(); return }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createOverlay()
        handler.post(updater)
    }

    private fun startAsForeground() {
        val channelId = "pocket_hud"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(channelId, "Pocket HUD", NotificationManager.IMPORTANCE_LOW)
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channelId)
            else @Suppress("DEPRECATION") Notification.Builder(this)
        val notification: Notification = builder.setContentTitle("Pocket HUD is active")
            .setContentText("Floating gaming battery dashboard")
            .setSmallIcon(android.R.drawable.ic_menu_info_details).setOngoing(true).build()
        startForeground(41, notification)
    }

    private fun createOverlay() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(0xF21B2028.toInt(), 17)
            elevation = dp(8).toFloat()
            setOnTouchListener(DragTouchListener())
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "POCKET HUD"; textSize = 10f; letterSpacing = .16f
            setTextColor(0xFF9AAABD.toInt()); typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this).apply {
            text = "● LIVE"; textSize = 9f; letterSpacing = .08f
            setTextColor(0xFF83C9A5.toInt()); typeface = Typeface.DEFAULT_BOLD
        })
        container.addView(header)

        batteryText = TextView(this).apply {
            text = "--%"; textSize = 29f
            setTextColor(Color.WHITE); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, dp(7), 0, 0)
        }
        container.addView(batteryText)
        container.addView(TextView(this).apply {
            text = "ESTIMATED PLAYTIME"; textSize = 9f; letterSpacing = .12f
            setTextColor(0xFF9AAABD.toInt()); typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(7), 0, 0)
        })
        timeText = TextView(this).apply {
            text = "Estimating…"; textSize = 19f
            setTextColor(0xFF8BC5E2.toInt()); typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(2), 0, dp(8))
        }
        container.addView(timeText)
        val divider = View(this).apply { setBackgroundColor(0xFF3B4552.toInt()) }
        container.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))
        detailText = TextView(this).apply {
            textSize = 10f; setTextColor(0xFFB4BFCC.toInt())
            setPadding(0, dp(8), 0, 0)
        }
        container.addView(detailText)
        sessionText = TextView(this).apply {
            textSize = 9f; setTextColor(0xFF8290A1.toInt())
            setPadding(0, dp(5), 0, 0)
        }
        container.addView(sessionText)

        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(16); y = dp(150) }
        panel = container
        windowManager.addView(container, params)
    }

    private fun updateReadout() {
        val battery = getSystemService(BATTERY_SERVICE) as BatteryManager
        val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val currentUa = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val chargeUah = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val batteryIntent = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val plugged = battery.isCharging
        val now = System.currentTimeMillis()

        if (level in 1..100) {
            if (previousLevel >= 0 && previousTime > 0 && level < previousLevel) {
                val elapsedHours = (now - previousTime) / 3_600_000.0
                val drop = previousLevel - level
                if (elapsedHours > 0 && drop > 0) {
                    drainPercentPerHour = drop / elapsedHours
                    fallbackHours = (level / (drop / elapsedHours)).coerceAtMost(72.0)
                }
            }
            if (previousLevel < 0 || level < previousLevel) { previousLevel = level; previousTime = now }
        }

        val directHours = if (!plugged && currentUa != Int.MIN_VALUE && chargeUah > 0 && abs(currentUa.toLong()) > 20L) {
            (chargeUah.toDouble() / abs(currentUa.toLong())).takeIf { it.isFinite() && it in 0.0..72.0 }
        } else null
        val estimate = if (plugged) null else directHours ?: fallbackHours
        val timeTextValue = when {
            plugged -> "Charging"
            estimate == null -> "Learning usage…"
            else -> {
                val minutes = (estimate * 60).toInt()
                String.format(Locale.getDefault(), "%dh %02dm left", minutes / 60, minutes % 60)
            }
        }
        batteryText.text = if (level in 0..100) "$level%" else "--%"
        timeText.text = timeTextValue
        val drain = drainPercentPerHour?.let { String.format(Locale.getDefault(), "%.1f%%/h", it) } ?: "learning"
        val temp = if (tempTenths > 0) String.format(Locale.getDefault(), "%.1f°C", tempTenths / 10f) else "—"
        val voltage = if (voltageMv > 0) String.format(Locale.getDefault(), "%.2fV", voltageMv / 1000f) else "—"
        detailText.text = "DRAIN  $drain     TEMP  $temp\nVOLTAGE  $voltage"
        val elapsed = ((now - sessionStarted) / 60_000L)
        sessionText.text = String.format(Locale.getDefault(), "SESSION  %dh %02dm  ·  DRAG TO MOVE", elapsed / 60, elapsed % 60)
    }

    private inner class DragTouchListener : View.OnTouchListener {
        private var startX = 0; private var startY = 0
        private var touchX = 0f; private var touchY = 0f
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val params = panel?.layoutParams as? WindowManager.LayoutParams ?: return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y; touchX = event.rawX; touchY = event.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(panel, params)
                    return true
                }
            }
            return true
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(updater)
        panel?.let { if (::windowManager.isInitialized) windowManager.removeView(it) }
        panel = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
