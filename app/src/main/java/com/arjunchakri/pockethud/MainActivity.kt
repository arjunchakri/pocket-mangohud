package com.arjunchakri.pockethud
import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
class MainActivity:Activity(){
 private lateinit var status:TextView
 private val prefs by lazy{getSharedPreferences("hud_options",Context.MODE_PRIVATE)}
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 private fun card(c:Int)=GradientDrawable().apply{setColor(c);cornerRadius=dp(16).toFloat()}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  val root=LinearLayout(this).apply{orientation=1;setPadding(dp(22),dp(32),dp(22),dp(28));setBackgroundColor(0xFF171A20.toInt())}
  root.addView(TextView(this).apply{text="POCKET HUD";textSize=12f;letterSpacing=.18f;setTextColor(0xFF91A0B1.toInt());typeface=Typeface.DEFAULT_BOLD})
  root.addView(TextView(this).apply{text="Your gaming\ncompanion.";textSize=30f;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD;setPadding(0,dp(10),0,dp(6))})
  root.addView(TextView(this).apply{text="Track which game drains your battery, how quickly, and what your phone is doing.";textSize=14f;setTextColor(0xFFADB8C5.toInt());setPadding(0,0,0,dp(18))})
  val setup=LinearLayout(this).apply{orientation=1;setPadding(dp(16),dp(16),dp(16),dp(16));background=card(0xFF232933.toInt())}
  setup.addView(TextView(this).apply{text="PERMISSIONS";textSize=11f;letterSpacing=.12f;setTextColor(0xFF83BEDD.toInt());typeface=Typeface.DEFAULT_BOLD})
  status=TextView(this).apply{textSize=13f;setTextColor(0xFFD8E1EA.toInt());setPadding(0,dp(10),0,dp(8))};setup.addView(status)
  setup.addView(Button(this).apply{text="Allow floating overlay";isAllCaps=false;setOnClickListener{if(!Settings.canDrawOverlays(this@MainActivity))startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))else startHud()}})
  setup.addView(Button(this).apply{text="Allow app/game detection";isAllCaps=false;setOnClickListener{startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))}})
  setup.addView(Button(this).apply{text="Start HUD";isAllCaps=false;setOnClickListener{startHud()}})
  setup.addView(Button(this).apply{text="Stop HUD";isAllCaps=false;setOnClickListener{stopService(Intent(this@MainActivity,OverlayService::class.java));refresh()}})
  root.addView(setup)
  root.addView(TextView(this).apply{text="OPTIONAL HUD METRICS";textSize=11f;letterSpacing=.12f;typeface=Typeface.DEFAULT_BOLD;setTextColor(0xFF83BEDD.toInt());setPadding(2,dp(20),0,dp(6))})
  listOf("show_ram" to "RAM usage","show_cpu" to "CPU usage","show_refresh" to "Screen refresh rate","show_temp" to "Battery temperature and voltage","show_fps" to "FPS (availability depends on Android/game)","show_power" to "Estimated battery power draw").forEach{(key,label)->root.addView(CheckBox(this).apply{text=label;isChecked=prefs.getBoolean(key,key=="show_temp");setTextColor(0xFFD8E1EA.toInt());setOnCheckedChangeListener{_,checked->prefs.edit().putBoolean(key,checked).apply()}})}
  root.addView(TextView(this).apply{text="App detection uses Android Usage Access. Per-app battery drain is estimated from battery percentage change during the foreground session.";textSize=12f;setTextColor(0xFF8996A6.toInt());setPadding(2,dp(16),2,0)})
  setContentView(root);refresh()
 }
 private fun startHud(){if(!Settings.canDrawOverlays(this)){startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")));return};val i=Intent(this,OverlayService::class.java);if(Build.VERSION.SDK_INT>=26)startForegroundService(i)else startService(i);status.text="HUD started. Switch to your game."}
 private fun hasUsageAccess():Boolean{val ops=getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager;@Suppress("DEPRECATION") val m=ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,android.os.Process.myUid(),packageName);return m==AppOpsManager.MODE_ALLOWED}
 override fun onResume(){super.onResume();if(::status.isInitialized)refresh()}
 private fun refresh(){status.text=(if(Settings.canDrawOverlays(this))"● Overlay permission granted"else"○ Overlay permission needed")+"\n"+(if(hasUsageAccess())"● App detection permission granted"else"○ App detection permission needed")}
}