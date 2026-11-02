package com.arjunchakri.pockethud

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
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
    private lateinit var readout: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var previousLevel = -1
    private var previousTime = 0L
    private var fallbackHours: Double? = null

    private val updater = object : Runnable {
        override fun run() { updateReadout(); handler.postDelayed(this, 10_000L) }
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
            .setContentText("Floating battery runtime estimate")
            .setSmallIcon(android.R.drawable.ic_menu_info_details).setOngoing(true).build()
        startForeground(41, notification)
    }

    private fun createOverlay() {
        readout = TextView(this).apply {
            text = "BATTERY  --%\nPLAYTIME  estimating…"
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(16, 11, 16, 11)
            setBackgroundColor(0xDD171A20.toInt())
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(readout)
            setOnTouchListener(DragTouchListener())
        }
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 24; y = 180 }
        panel = container
        windowManager.addView(container, params)
    }

    private fun updateReadout() {
        val battery = getSystemService(BATTERY_SERVICE) as BatteryManager
        val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val currentUa = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val chargeUah = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val plugged = battery.isCharging
        val now = System.currentTimeMillis()

        if (level in 1..100) {
            if (previousLevel >= 0 && previousTime > 0 && level < previousLevel) {
                val elapsedHours = (now - previousTime) / 3_600_000.0
                val drop = previousLevel - level
                if (elapsedHours > 0 && drop > 0) fallbackHours = (level / (drop / elapsedHours)).coerceAtMost(72.0)
            }
            if (previousLevel < 0 || level < previousLevel) { previousLevel = level; previousTime = now }
        }

        val directHours = if (!plugged && currentUa != Int.MIN_VALUE && chargeUah > 0 && abs(currentUa.toLong()) > 20L) {
            (chargeUah.toDouble() / abs(currentUa.toLong())).takeIf { it.isFinite() && it in 0.0..72.0 }
        } else null
        val estimate = if (plugged) null else directHours ?: fallbackHours
        val timeText = when {
            plugged -> "charging"
            estimate == null -> "estimating…"
            else -> {
                val minutes = (estimate * 60).toInt()
                String.format(Locale.getDefault(), "%dh %02dm left", minutes / 60, minutes % 60)
            }
        }
        readout.text = "BATTERY  ${if (level in 0..100) "$level%" else "--"}\nPLAYTIME  $timeText"
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
