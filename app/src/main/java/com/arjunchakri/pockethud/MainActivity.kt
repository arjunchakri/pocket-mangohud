package com.arjunchakri.pockethud

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 36, 28, 28)
        }
        root.addView(TextView(this).apply { text = "Pocket HUD"; textSize = 28f })
        root.addView(TextView(this).apply {
            text = "A tiny floating battery and remaining-playtime estimate. No emulator, game runtime, or root access."
            textSize = 16f
            setPadding(0, 12, 0, 24)
        })
        status = TextView(this).apply { textSize = 14f; setPadding(0, 0, 0, 20) }
        root.addView(status)
        root.addView(Button(this).apply { text = "Enable floating HUD"; setOnClickListener { enableHud() } })
        root.addView(Button(this).apply {
            text = "Stop HUD"
            setOnClickListener { stopService(Intent(this@MainActivity, OverlayService::class.java)); refreshStatus() }
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
    override fun onResume() { super.onResume(); refreshStatus() }
    private fun refreshStatus() {
        status.text = if (Settings.canDrawOverlays(this)) "Overlay permission: granted"
        else "Overlay permission: required. Android will open Settings when you enable the HUD."
    }
}
