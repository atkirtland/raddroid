package com.raddroid.app

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var folderInput: EditText
    private lateinit var statusText: TextView
    private lateinit var pathText: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshStatus()
            handler.postDelayed(this, 2000)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val legacyStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshStatus() }

    private val allFilesAccessLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("raddroid", MODE_PRIVATE)
        folderInput = findViewById(R.id.folderInput)
        statusText = findViewById(R.id.statusText)
        pathText = findViewById(R.id.pathText)

        folderInput.setText(prefs.getString("folder_name", "radicale"))

        findViewById<Button>(R.id.grantButton).setOnClickListener { requestStoragePermission() }
        findViewById<Button>(R.id.startButton).setOnClickListener { startServer() }
        findViewById<Button>(R.id.stopButton).setOnClickListener { stopServer() }
        findViewById<Button>(R.id.openBrowserButton).setOnClickListener { openBrowser() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
        prefs.edit().putString("folder_name", folderInput.text.toString()).apply()
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName")
            )
            allFilesAccessLauncher.launch(intent)
        } else {
            legacyStoragePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun resolvedBaseDir(): File {
        val name = folderInput.text.toString().trim().ifEmpty { "radicale" }
        return File(Environment.getExternalStorageDirectory(), name)
    }

    private fun ensureConfig(baseDir: File): File {
        baseDir.mkdirs()
        File(baseDir, "collections").mkdirs()
        val configFile = File(baseDir, "config-android")
        if (!configFile.exists()) {
            val template = assets.open("config-android").bufferedReader().use { it.readText() }
            val content = template.replace("__BASE_DIR__", baseDir.absolutePath)
            configFile.writeText(content)
        }
        return configFile
    }

    private fun startServer() {
        if (!hasStoragePermission()) {
            Toast.makeText(this, "Grant storage access first", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.edit().putString("folder_name", folderInput.text.toString()).apply()

        val baseDir = resolvedBaseDir()
        val configFile = ensureConfig(baseDir)

        val intent = Intent(this, RadicaleService::class.java).apply {
            action = RadicaleService.ACTION_START
            putExtra(RadicaleService.EXTRA_STORAGE_DIR, baseDir.absolutePath)
            putExtra(RadicaleService.EXTRA_CONFIG_PATH, configFile.absolutePath)
            putExtra(RadicaleService.EXTRA_PORT, 5232)
        }
        ContextCompat.startForegroundService(this, intent)
        pathText.text = "Storage: ${baseDir.absolutePath}\nConfig: ${configFile.absolutePath}"
        handler.postDelayed({ refreshStatus() }, 500)
    }

    private fun stopServer() {
        val intent = Intent(this, RadicaleService::class.java).apply {
            action = RadicaleService.ACTION_STOP
        }
        startService(intent)
        handler.postDelayed({ refreshStatus() }, 500)
    }

    private fun openBrowser() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:5232/")))
    }

    private fun refreshStatus() {
        statusText.text = if (RadicaleService.isRunning) {
            "Running on 127.0.0.1:5232"
        } else if (RadicaleService.lastError.isNotEmpty()) {
            "Stopped (error: ${RadicaleService.lastError.take(200)})"
        } else {
            "Stopped"
        }
    }
}
