package com.raddroid.app

import android.content.ClipData
import android.content.ClipboardManager
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
    private lateinit var configInput: EditText
    private lateinit var statusText: TextView
    private lateinit var pathText: TextView
    private lateinit var errorText: TextView
    private lateinit var logText: TextView

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
        configInput = findViewById(R.id.configInput)
        statusText = findViewById(R.id.statusText)
        pathText = findViewById(R.id.pathText)
        errorText = findViewById(R.id.errorText)
        logText = findViewById(R.id.logText)

        folderInput.setText(prefs.getString("folder_name", "radicale"))
        configInput.setText(prefs.getString("config_name", "config"))

        findViewById<Button>(R.id.grantButton).setOnClickListener { requestStoragePermission() }
        findViewById<Button>(R.id.startButton).setOnClickListener { startServer() }
        findViewById<Button>(R.id.stopButton).setOnClickListener { stopServer() }
        findViewById<Button>(R.id.openBrowserButton).setOnClickListener { openBrowser() }
        findViewById<Button>(R.id.copyErrorButton).setOnClickListener {
            copyToClipboard("RadDroid error", errorText.text.toString())
        }
        findViewById<Button>(R.id.copyLogButton).setOnClickListener {
            copyToClipboard("RadDroid log", logText.text.toString())
        }
        findViewById<Button>(R.id.clearLogButton).setOnClickListener { clearLog() }

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
        saveInputs()
    }

    private fun saveInputs() {
        prefs.edit()
            .putString("folder_name", folderInput.text.toString())
            .putString("config_name", configInput.text.toString())
            .apply()
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

    private fun resolvedConfigName(): String {
        val name = configInput.text.toString().trim().ifEmpty { "config" }
        return name.replace("/", "").replace("\\", "").ifEmpty { "config" }
    }

    private fun ensureConfig(baseDir: File, configName: String): File {
        baseDir.mkdirs()
        File(baseDir, "collections").mkdirs()
        val configFile = File(baseDir, configName)
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
        saveInputs()

        val baseDir = resolvedBaseDir()
        val configFile = ensureConfig(baseDir, resolvedConfigName())

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

    private fun clearLog() {
        val intent = Intent(this, RadicaleService::class.java).apply {
            action = RadicaleService.ACTION_CLEAR_LOG
        }
        startService(intent)
        handler.postDelayed({ refreshStatus() }, 300)
    }

    private fun refreshStatus() {
        val hasError = !RadicaleService.isRunning && RadicaleService.lastError.isNotEmpty()
        val newStatus = when {
            RadicaleService.isRunning -> "Running on 127.0.0.1:5232"
            hasError -> "Stopped (error below)"
            else -> "Stopped"
        }
        val newError = if (hasError) RadicaleService.lastError else ""
        val newLog = RadicaleService.logSnapshot

        // Re-assigning identical text resets any in-progress text selection, which makes
        // it impossible to select-and-copy while the 2-second poll is running.
        if (statusText.text.toString() != newStatus) statusText.text = newStatus
        if (errorText.text.toString() != newError) errorText.text = newError
        if (logText.text.toString() != newLog) logText.text = newLog
    }

    private fun copyToClipboard(label: String, text: String) {
        if (text.isEmpty()) {
            Toast.makeText(this, "Nothing to copy", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}
