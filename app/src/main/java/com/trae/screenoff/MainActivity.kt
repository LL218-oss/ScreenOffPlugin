package com.trae.screenoff

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private var silent = false

    private val notifPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("settings", MODE_PRIVATE)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)

        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        findViewById<Button>(R.id.btnA11y).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<RadioGroup>(R.id.rgStyle).setOnCheckedChangeListener { _, id ->
            if (silent) return@setOnCheckedChangeListener
            prefs.edit().putInt("style", styleOf(id)).apply()
        }

        val sw = findViewById<Switch>(R.id.swBall)
        sw.setOnCheckedChangeListener { _, checked ->
            if (silent) return@setOnCheckedChangeListener
            prefs.edit().putBoolean("ball_on", checked).apply()
            if (checked) {
                if (Settings.canDrawOverlays(this)) {
                    ContextCompat.startForegroundService(
                        this, Intent(this, FloatingService::class.java)
                    )
                } else {
                    toast("请先开启悬浮窗权限")
                    sw.isChecked = false
                }
            } else {
                stopService(Intent(this, FloatingService::class.java))
            }
        }

        findViewById<Button>(R.id.btnPreview).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                toast("请先开启悬浮窗权限")
                return@setOnClickListener
            }
            preview()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        silent = true
        val overlayOk = Settings.canDrawOverlays(this)
        findViewById<TextView>(R.id.tvOverlayStatus).text =
            if (overlayOk) "悬浮窗权限：已开启" else "悬浮窗权限：未开启"

        val a11yOk = accessibilityEnabled()
        findViewById<TextView>(R.id.tvA11yStatus).text =
            if (a11yOk) "无障碍锁屏服务：已开启" else "无障碍锁屏服务：未开启"

        when (prefs.getInt("style", 0)) {
            1 -> findViewById<RadioGroup>(R.id.rgStyle).check(R.id.rbCircle)
            2 -> findViewById<RadioGroup>(R.id.rgStyle).check(R.id.rbFade)
            else -> findViewById<RadioGroup>(R.id.rgStyle).check(R.id.rbCrt)
        }
        findViewById<Switch>(R.id.swBall).isChecked =
            overlayOk && prefs.getBoolean("ball_on", false)
        silent = false

        if (overlayOk && prefs.getBoolean("ball_on", false) && !FloatingService.isRunning) {
            ContextCompat.startForegroundService(this, Intent(this, FloatingService::class.java))
        }
    }

    private fun accessibilityEnabled(): Boolean {
        val s = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val cn = ComponentName(this, LockAccessibilityService::class.java)
        return s.split(':').any {
            it.equals(cn.flattenToString(), true) ||
                it.equals(cn.flattenToShortString(), true)
        }
    }

    private fun styleOf(id: Int) = when (id) {
        R.id.rbCircle -> 1
        R.id.rbFade -> 2
        else -> 0
    }

    private fun preview() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        var overlay: ScreenOffOverlay? = null
        overlay = ScreenOffOverlay(this, prefs.getInt("style", 0)) {
            runOnUiThread {
                overlay?.let { runCatching { wm.removeView(it) } }
            }
        }
        wm.addView(overlay!!, params)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
