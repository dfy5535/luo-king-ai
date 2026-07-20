package com.luoking.agent.services

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
import com.luoking.agent.managers.ConfigManager
import com.luoking.agent.models.CoordinateManager
import com.luoking.agent.models.SessionState
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/**
 * 截图服务 — 唯一职责：截图→JPEG→Base64→回调
 * 不能点击、不能联网、不能决策
 */
class CaptureService : Service() {
    companion object {
        private const val TAG = "CaptureService"
        private const val CHANNEL_ID = "luo_king_capture"
        private var instance: CaptureService? = null
        private var onScreenshot: ((String?) -> Unit)? = null
        fun setCallback(cb: (String?) -> Unit) { onScreenshot = cb }
        fun requestScreenshot() { instance?.captureFrame() }
        fun isRunning(): Boolean = instance != null
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val rc = intent?.getIntExtra("result_code", -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>("data")
        if (rc != -1 && data != null) setupMediaProjection(rc, data)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { instance = null; teardown(); super.onDestroy() }

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "截图服务", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
        startForeground(1, Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("洛克王国AI助手").setContentText("截图运行中")
            .setSmallIcon(android.R.drawable.ic_menu_camera).setOngoing(true).build())
    }

    private fun setupMediaProjection(rc: Int, data: Intent) {
        val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = pm.getMediaProjection(rc, data)
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { teardown() }
        }, handler)

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(metrics)
        CoordinateManager.screenWidth = metrics.widthPixels
        CoordinateManager.screenHeight = metrics.heightPixels

        imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay("luo_king_capture",
            metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader?.surface, null, handler)
        imageReader?.setOnImageAvailableListener({ captureFrame() }, handler)
    }

    private fun captureFrame() {
        executor.execute {
            try {
                val reader = imageReader ?: return@execute
                val image = reader.acquireLatestImage() ?: return@execute
                val buffer = image.planes[0].buffer; buffer.rewind()
                val bmp = Bitmap.createBitmap(CoordinateManager.screenWidth, CoordinateManager.screenHeight, Bitmap.Config.ARGB_8888)
                bmp.copyPixelsFromBuffer(buffer); image.close()
                val os = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, ConfigManager.jpegQuality, os); bmp.recycle()
                val b64 = android.util.Base64.encodeToString(os.toByteArray(), android.util.Base64.NO_WRAP)
                SessionState.totalScreenshots++
                onScreenshot?.invoke(b64)
            } catch (e: Exception) { Log.e(TAG, "截图失败", e); onScreenshot?.invoke(null) }
        }
    }

    private fun teardown() {
        virtualDisplay?.release(); virtualDisplay = null
        imageReader?.close(); imageReader = null
        mediaProjection?.stop(); mediaProjection = null
    }
}