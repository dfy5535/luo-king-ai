package com.luoking.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.luoking.agent.models.SessionState
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

class ScreenCaptureService : Service() {
    companion object {
        private const val TAG = "CaptureService"
        private const val CHANNEL_ID = "screen_capture_channel"
        private var instance: ScreenCaptureService? = null
        private var onScreenshot: ((String?) -> Unit)? = null
        fun setScreenshotCallback(cb: (String?) -> Unit) { onScreenshot = cb }
        fun requestScreenshot() { instance?.captureFrame() }
        fun isRunning(): Boolean = instance != null
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var screenWidth = 1080
    private var screenHeight = 1920
    private var screenDensity = 320

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForegroundService()
        Log.d(TAG, "屏幕捕获服务已创建")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("result_code", -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>("data")
        if (resultCode != -1 && data != null) {
            setupMediaProjection(resultCode, data)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        instance = null
        teardownMediaProjection()
        super.onDestroy()
    }

    private fun startForegroundService() {
        val channelId = CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "屏幕捕获", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("洛克王国AI助手")
            .setContentText("屏幕捕获运行中")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
        startForeground(1, notification)
    }

    private fun setupMediaProjection(resultCode: Int, data: Intent) {
        val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = pm.getMediaProjection(resultCode, data)
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { teardownMediaProjection() }
        }, handler)

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
        SessionState.screenWidth = screenWidth
        SessionState.screenHeight = screenHeight

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "luo_king_capture", screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, handler
        )
        imageReader?.setOnImageAvailableListener({ reader ->
            captureFrame()
        }, handler)
    }

    private fun captureFrame() {
        executor.execute {
            try {
                val reader = imageReader ?: return@execute
                val image = reader.acquireLatestImage() ?: return@execute
                val buffer = image.planes[0].buffer
                buffer.rewind()
                val bitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(buffer)
                image.close()

                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                bitmap.recycle()

                val base64 = android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.NO_WRAP)
                SessionState.totalScreenshots++
                onScreenshot?.invoke(base64)
            } catch (e: Exception) {
                Log.e(TAG, "截图失败", e)
                onScreenshot?.invoke(null)
            }
        }
    }

    private fun teardownMediaProjection() {
        virtualDisplay?.release(); virtualDisplay = null
        imageReader?.close(); imageReader = null
        mediaProjection?.stop(); mediaProjection = null
    }
}