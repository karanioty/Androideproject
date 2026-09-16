package com.example.linkguard

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class BubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private lateinit var params: WindowManager.LayoutParams

    companion object {
        const val CHANNEL_ID = "linkguard_bubble_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startAsForeground()
        createBubbleView()
        DetectionHistoryRepository.setBubbleRunning(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LinkGuard Bubble Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows floating bubble and active protection status"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startAsForeground() {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LinkGuard Active")
            .setContentText("Monitoring unknown links, QR codes, images, videos, and audios")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createBubbleView() {
        val density = resources.displayMetrics.density
        val bubbleSize = (64 * density).toInt()

        val frame = FrameLayout(this).apply {
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                colors = intArrayOf(Color.parseColor("#3B82F6"), Color.parseColor("#1D4ED8"))
                setStroke((2 * density).toInt(), Color.parseColor("#93C5FD"))
            }
            background = bg
            elevation = 16f
        }

        val iconText = TextView(this).apply {
            text = "🛡️"
            textSize = 28f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            val textParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
            layoutParams = textParams
        }

        frame.addView(iconText)

        params = WindowManager.LayoutParams(
            bubbleSize,
            bubbleSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 300
        }

        // Draggable touch listener with snap-to-edge and click detection
        frame.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (abs(dx) > 10 || abs(dy) > 10) {
                            isDragging = true
                        }
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager.updateViewLayout(frame, params)
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            // Single tap on bubble: Scan current page, show loading HUD, and show Low/Medium/Hard pop-up!
                            PageScanOverlayManager.startScan(this@BubbleService)
                        } else {
                            // Snap bubble to nearest screen edge (left or right)
                            val screenWidth = resources.displayMetrics.widthPixels
                            val snapX = if (params.x + bubbleSize / 2 < screenWidth / 2) {
                                20
                            } else {
                                screenWidth - bubbleSize - 20
                            }
                            params.x = snapX
                            windowManager.updateViewLayout(frame, params)
                        }
                        return true
                    }
                }
                return false
            }
        })

        try {
            windowManager.addView(frame, params)
            bubbleView = frame
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bubbleView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        bubbleView = null
        PageScanOverlayManager.dismissAll()
        DetectionHistoryRepository.setBubbleRunning(false)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}