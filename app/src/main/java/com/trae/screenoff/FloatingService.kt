package com.trae.screenoff

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * 前台服务：维护桌面上的悬浮小球。
 * 单击小球 -> 播放熄屏动画 -> 通过无障碍服务锁屏；
 * 按住小球可以拖动位置。
 */
class FloatingService : Service() {

    companion object {
        @Volatile
        var isRunning = false
            private set
        private const val CHANNEL_ID = "screenoff"
    }

    private lateinit var wm: WindowManager
    private var ball: View? = null
    private var animView: ScreenOffOverlay? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        ensureChannel()
        startForeground(1, buildNotification())
        showBall()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        isRunning = false
        removeBall()
        removeAnim()
        super.onDestroy()
    }

    private fun showBall() {
        if (ball != null) return
        val size = dp(56f).toInt()
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - size - dp(6f).toInt()
            y = dp(180f).toInt()
        }

        val container = FrameLayout(this).apply {
            background = ContextCompat.getDrawable(this@FloatingService, R.drawable.ball_bg)
        }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_moon)
            val pad = dp(14f).toInt()
            setPadding(pad, pad, pad, pad)
        }
        container.addView(icon)

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        container.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!moved && (Math.abs(dx) > 10 || Math.abs(dy) > 10)) moved = true
                    if (moved) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        wm.updateViewLayout(v, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        vibrate()
                        screenOff()
                    }
                    true
                }
                else -> false
            }
        }

        ball = container
        wm.addView(container, params)
    }

    private fun removeBall() {
        ball?.let { b -> runCatching { wm.removeView(b) } }
        ball = null
    }

    private fun removeAnim() {
        animView?.let { a -> runCatching { wm.removeView(a) } }
        animView = null
    }

    private fun screenOff() {
        removeBall()
        removeAnim()
        val style = getSharedPreferences("settings", MODE_PRIVATE).getInt("style", 0)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        val overlay = ScreenOffOverlay(this, style) {
            removeAnim()
            showBall()
            LockAccessibilityService.lock()
        }
        animView = overlay
        wm.addView(overlay, params)
    }

    private fun vibrate() {
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_moon)
            .setContentTitle("熄屏动画运行中")
            .setContentText("点击悬浮小球即可播放熄屏动画并锁屏")
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(CHANNEL_ID, "熄屏动画", NotificationManager.IMPORTANCE_MIN)
        ch.setSound(null, null)
        nm.createNotificationChannel(ch)
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}
