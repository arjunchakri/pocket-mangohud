package com.arjunchakri.pockethud

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun card(color: Int, radius: Int = 18) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(36), dp(24), dp(24))
            setBackgroundColor(Color.rgb(23, 26, 32))
        }
        root.addView(TextView(this).apply {
            text = "POCKET HUD"
            textSize = 12f; letterSpacing = .18f
            setTextColor(Color.rgb(145, 160, 177)); typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Your gaming\ncompanion."
            textSize = 32f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, dp(12), 0, dp(8))
        })
        root.addView(TextView(this).apply {
            text = "A lightweight floating overlay for battery life and playtime. Built for gaming on Android."
            textSize = 15f; setTextColor(Color.rgb(174, 184, 197))
            setPadding(0, 0, 0, dp(24))
        })
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = card(Color.rgb(35, 41, 51))
        }
        panel.addView(TextView(this).apply {
            text = "QUICK SETUP"; textSize = 11f; letterSpacing = .14f
            setTextColor(Color.rgb(126, 190, 220)); typeface = Typeface.DEFAULT_BOLD
        })
        status = TextView(this).apply {
            textSize = 14f; setTextColor(Color.rgb(218, 225, 234))
            setPadding(0, dp(12), 0, dp(16))
        }
        panel.addView(status)
        panel.addView(Button(this).apply {
            text = "Enable floating HUD"
            isAllCaps = false; textSize = 15f
            setTextColor(Color.WHITE)
            background = card(Color.rgb(26, 116, 160), 12)
            setOnClickListener { enableHud() }
        })
        panel.addView(Button(this).apply {
            text = "Stop HUD"; isAllCaps = false
            setTextColor(Color.rgb(220, 226, 235))
            background = card(Color.rgb(53, 61, 73), 12)
            setOnClickListener {
                stopService(Intent(this@MainActivity, OverlayService::class.java))
                refreshStatus()
            }
        })
        root.addView(panel)
        root.addView(TextView(this).apply {
            text = "Tip: drag the floating panel to move it out of the way while playing."
            textSize = 13f; setTextColor(Color.rgb(126, 137, 152))
            setPadding(2, dp(18), 2, 0)
        })
        setContentView(root)
        refreshStatus()
    }

    private fun enableHud() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        status.text = "HUD started. You can return to your game."
    }

    override fun onResume() { super.onResume(); if (::status.isInitialized) refreshStatus() }

    private fun refreshStatus() {
        status.text = if (Settings.canDrawOverlays(this))
            "●  Overlay permission granted\nYou're ready to start the HUD."
        else
            "○  Overlay permission required\nAndroid will open Settings so you can allow Pocket HUD to appear over games."
    }
}
