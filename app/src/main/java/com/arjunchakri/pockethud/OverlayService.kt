package com.arjunchakri.pockethud
import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.io.BufferedReader
import java.io.FileReader
import java.util.Locale
import kotlin.math.abs
class OverlayService:Service(){
 private lateinit var wm:WindowManager
 private var panel:View?=null
 private lateinit var appLine:TextView
 private lateinit var batteryLine:TextView
 private lateinit var timeLine:TextView
 private lateinit var sessionLine:TextView
 private lateinit var metricsLine:TextView
 private val handler=Handler(Looper.getMainLooper())
 private val prefs by lazy{getSharedPreferences("hud_options",Context.MODE_PRIVATE)}
 private var activePackage:String?=null
 private var activeName="Waiting for game"
 private var appStartMs=0L
 private var appStartLevel=-1
 private var previousLevel=-1
 private var previousAt=0L
 private var drainPerHour:Double?=null
 private var cpuPrevious:Pair<Long,Long>?=null
 private val tick=object:Runnable{override fun run(){update();handler.postDelayed(this,5000)}}
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 private fun bg(c:Int)=GradientDrawable().apply{setColor(c);cornerRadius=dp(15).toFloat();setStroke(dp(1),0xFF46515F.toInt())}
 override fun onCreate(){super.onCreate();foreground();if(!android.provider.Settings.canDrawOverlays(this)){stopSelf();return};wm=getSystemService(WINDOW_SERVICE) as WindowManager;makePanel();handler.post(tick)}
 private fun foreground(){val id="pocket_hud";val nm=getSystemService(NotificationManager::class.java);if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(NotificationChannel(id,"Pocket HUD",NotificationManager.IMPORTANCE_LOW));val b=if(Build.VERSION.SDK_INT>=26)Notification.Builder(this,id)else @Suppress("DEPRECATION") Notification.Builder(this);startForeground(41,b.setContentTitle("Pocket HUD is active").setContentText("Tracking gaming battery usage").setSmallIcon(android.R.drawable.ic_menu_info_details).setOngoing(true).build())}
 private fun makePanel(){
  val root=LinearLayout(this).apply{orientation=1;setPadding(dp(13),dp(11),dp(13),dp(11));background=bg(0xF21B2028.toInt());elevation=dp(8).toFloat();setOnTouchListener(Drag())}
  val head=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
  head.addView(TextView(this).apply{text="POCKET HUD";textSize=10f;letterSpacing=.15f;setTextColor(0xFF9AAABD.toInt());typeface=Typeface.DEFAULT_BOLD},LinearLayout.LayoutParams(0,-2,1f))
  head.addView(TextView(this).apply{text="● LIVE";textSize=9f;setTextColor(0xFF83C9A5.toInt())});root.addView(head)
  appLine=addText(root,"Detecting foreground app…",12f,0xFF8BC5E2.toInt(),true)
  batteryLine=addText(root,"--% battery",26f,Color.WHITE,true)
  timeLine=addText(root,"Playtime remaining: estimating",14f,0xFF8BC5E2.toInt(),true)
  val rule=View(this).apply{setBackgroundColor(0xFF3B4552.toInt())};root.addView(rule,LinearLayout.LayoutParams(-1,dp(1)))
  sessionLine=addText(root,"APP SESSION  waiting",11f,0xFFD2DBE5.toInt(),false)
  metricsLine=addText(root,"",10f,0xFFADB9C7.toInt(),false)
  val type=if(Build.VERSION.SDK_INT>=26)WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
  val p=WindowManager.LayoutParams(-2,-2,type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.START;x=dp(14);y=dp(140)}
  panel=root;wm.addView(root,p)
 }
 private fun addText(root:LinearLayout,value:String,size:Float,color:Int,bold:Boolean):TextView{val t=TextView(this).apply{text=value;textSize=size;setTextColor(color);if(bold)typeface=Typeface.DEFAULT_BOLD;setPadding(0,dp(5),0,dp(2))};root.addView(t);return t}
 private fun hasUsageAccess():Boolean{val ops=getSystemService(APP_OPS_SERVICE) as AppOpsManager;@Suppress("DEPRECATION") return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),packageName)==AppOpsManager.MODE_ALLOWED}
 private fun currentForegroundPackage():String?{if(!hasUsageAccess())return null;val usm=getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager;val ev=usm.queryEvents(System.currentTimeMillis()-15000,System.currentTimeMillis());val e=UsageEvents.Event();var foreground=activePackage;while(ev.hasNextEvent()){ev.getNextEvent(e);when(e.eventType){UsageEvents.Event.ACTIVITY_RESUMED->foreground=e.packageName;UsageEvents.Event.ACTIVITY_PAUSED,UsageEvents.Event.ACTIVITY_STOPPED->if(foreground==e.packageName)foreground=null}};return foreground?.takeIf{it!=packageName}}
 private fun update(){
  val now=System.currentTimeMillis();val bm=getSystemService(BATTERY_SERVICE) as BatteryManager;val level=bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);val current=bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);val charge=bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
  val bi=registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED));val temp=bi?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,-1)?:-1;val volt=bi?.getIntExtra(BatteryManager.EXTRA_VOLTAGE,-1)?:-1
  val foreground=currentForegroundPackage()
  if(foreground!=activePackage){activePackage=foreground;appStartMs=if(foreground!=null)now else 0L;appStartLevel=if(foreground!=null)level else -1;activeName=try{if(foreground==null)"No game detected"else packageManager.getApplicationLabel(packageManager.getApplicationInfo(foreground,0)).toString()}catch(_:Exception){foreground?:"No game detected"}}
  if(level in 1..100){if(previousLevel>=0&&previousAt>0&&level<previousLevel){val h=(now-previousAt)/3600000.0;if(h>0)drainPerHour=(previousLevel-level)/h};if(previousLevel<0||level<previousLevel){previousLevel=level;previousAt=now}}
  appLine.text=if(hasUsageAccess())"APP  $activeName"else"APP  enable Usage Access in settings"
  batteryLine.text=if(level in 0..100)"$level% battery"else"--% battery"
  val direct=if(!bm.isCharging&&current!=Int.MIN_VALUE&&charge>0&&abs(current.toLong())>20)(charge.toDouble()/abs(current.toLong())).takeIf{it.isFinite()&&it in 0.0..72.0}else null
  val hrs=direct?:drainPerHour?.let{if(it>0)level/it else null}
  timeLine.text=if(bm.isCharging)"Charging"else if(hrs==null)"Playtime remaining: learning…"else String.format(Locale.getDefault(),"Playtime remaining: %dh %02dm",(hrs*60).toInt()/60,(hrs*60).toInt()%60)
  val elapsed=if(appStartMs>0)(now-appStartMs)/60000.0 else 0.0
  val appDrain=if(appStartLevel>=0&&elapsed>=0.5&&level<=appStartLevel)(appStartLevel-level).toDouble()/elapsed else null
  val used=if(appStartLevel>=0&&level<=appStartLevel)appStartLevel-level else 0
  val rate=appDrain?.let{String.format(Locale.getDefault(),"%.3f%%/min",it)}?:"warming up"
  sessionLine.text=if(appStartMs>0)String.format(Locale.getDefault(),"APP SESSION  %dh %02dm  ·  -%d%%  ·  %s",(elapsed/60).toInt(),elapsed.toInt()%60,used,rate)else"APP SESSION  —"
  val lines=mutableListOf<String>()
  if(prefs.getBoolean("show_ram",false)){val mi=ActivityManager.MemoryInfo();(getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi);lines.add(String.format(Locale.getDefault(),"RAM %.1f/%.1f GB",(mi.totalMem-mi.availMem)/1073741824.0,mi.totalMem/1073741824.0))}
  if(prefs.getBoolean("show_cpu",false)){val cpu=cpuLoad();lines.add(if(cpu==null)"CPU —"else String.format(Locale.getDefault(),"CPU %.0f%%",cpu))}
  if(prefs.getBoolean("show_refresh",false)){val hz=if(Build.VERSION.SDK_INT>=23)(getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.refreshRate else 0f;lines.add(if(hz>0)"DISPLAY "+String.format(Locale.getDefault(),"%.0f Hz",hz)else"DISPLAY —")}
  if(prefs.getBoolean("show_temp",true)){lines.add("BAT TEMP "+if(temp>0)String.format(Locale.getDefault(),"%.1f°C",temp/10f)else"—");lines.add("VOLT "+if(volt>0)String.format(Locale.getDefault(),"%.2fV",volt/1000f)else"—")}
  if(prefs.getBoolean("show_power",false)){val w=if(current!=Int.MIN_VALUE&&volt>0)abs(current.toLong())*volt/1000000000.0 else null;lines.add(if(w==null)"POWER —"else String.format(Locale.getDefault(),"POWER %.2f W (battery)",w))}
  if(prefs.getBoolean("show_fps",false))lines.add("FPS — (not exposed to normal apps)")
  metricsLine.text=lines.joinToString("   ·   ")
 }
 private fun cpuLoad():Double?{return try{val a=BufferedReader(FileReader("/proc/stat")).use{it.readLine().trim().split(Regex("\\s+")).drop(1).map{v->v.toLong()}};val idle=a[3]+(a.getOrNull(4)?:0);val total=a.sum();val old=cpuPrevious;cpuPrevious=total to idle;if(old==null)null else{val dt=total-old.first;val di=idle-old.second;if(dt>0)100.0*(dt-di)/dt else null}}catch(_:Exception){null}}
 private inner class Drag:View.OnTouchListener{var sx=0;var sy=0;var tx=0f;var ty=0f;override fun onTouch(v:View,e:MotionEvent):Boolean{val p=panel?.layoutParams as? WindowManager.LayoutParams?:return false;when(e.action){MotionEvent.ACTION_DOWN->{sx=p.x;sy=p.y;tx=e.rawX;ty=e.rawY;return true};MotionEvent.ACTION_MOVE->{p.x=sx+(e.rawX-tx).toInt();p.y=sy+(e.rawY-ty).toInt();wm.updateViewLayout(panel,p);return true}};return true}}
 override fun onDestroy(){handler.removeCallbacks(tick);panel?.let{if(::wm.isInitialized)wm.removeView(it)};panel=null;super.onDestroy()}
 override fun onBind(intent:Intent?):IBinder?=null
}