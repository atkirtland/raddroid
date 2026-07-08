package com.raddroid.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class RadicaleService : Service() {

    companion object {
        const val ACTION_START = "com.raddroid.app.action.START"
        const val ACTION_STOP = "com.raddroid.app.action.STOP"
        const val EXTRA_STORAGE_DIR = "storage_dir"
        const val EXTRA_CONFIG_PATH = "config_path"
        const val EXTRA_PORT = "port"
        private const val CHANNEL_ID = "radicale_service"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "RadicaleService"

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var lastError: String = ""
            private set
    }

    private lateinit var pyModule: PyObject

    override fun onCreate() {
        super.onCreate()
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(applicationContext))
        }
        pyModule = Python.getInstance().getModule("raddroid_server")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopServer()
            stopSelf()
            return START_NOT_STICKY
        }

        val storageDir = intent?.getStringExtra(EXTRA_STORAGE_DIR)
        val configPath = intent?.getStringExtra(EXTRA_CONFIG_PATH)
        val port = intent?.getIntExtra(EXTRA_PORT, 5232) ?: 5232

        if (storageDir == null || configPath == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification(port)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        Thread {
            try {
                pyModule.callAttr("start", configPath, storageDir)
                isRunning = pyModule.callAttr("is_running").toBoolean()
                lastError = pyModule.callAttr("last_error").toString()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Radicale", e)
                lastError = e.message ?: e.toString()
                isRunning = false
            }
        }.start()

        return START_STICKY
    }

    private fun stopServer() {
        pyModule.callAttr("stop")
        isRunning = false
    }

    override fun onDestroy() {
        if (::pyModule.isInitialized) {
            stopServer()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(port: Int): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID, "Radicale server", NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        val openIntent = packageManager.getLaunchIntentForPackage(packageName)
        val flags = PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(this, 0, openIntent, flags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Radicale is running")
            .setContentText("Serving CalDAV/CardDAV on 127.0.0.1:$port")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
