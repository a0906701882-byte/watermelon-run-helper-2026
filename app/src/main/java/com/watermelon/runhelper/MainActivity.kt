package com.watermelon.runhelper

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val OVERLAY_PERMISSION_REQ_CODE = 1234

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 動態建立一個酷炫的賽博霓虹風格主畫面
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            setPadding(40, 40, 40, 40)
        }

        val tvTitle = TextView(this).apply {
            text = "🍉 西瓜電競雷達"
            setTextColor(Color.parseColor("#FFEA00"))
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 30)
        }
        rootLayout.addView(tvTitle)

        val btnStart = Button(this).apply {
            text = "啟動戰術雷達與中控台"
            setBackgroundColor(Color.parseColor("#FFEA00"))
            setTextColor(Color.parseColor("#0A0A0A"))
            setOnClickListener {
                checkOverlayPermissionAndStart()
            }
        }
        rootLayout.addView(btnStart)

        setContentView(rootLayout)
    }

    private fun checkOverlayPermissionAndStart() {
        // 檢查懸浮窗權限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE)
        } else {
            startServices()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                startServices()
            }
        }
    }

    private fun startServices() {
        // 啟動賽博霓虹懸浮控制面板
        val panelIntent = Intent(this, ControlPanelService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(panelIntent)
        } else {
            startService(panelIntent)
        }

        // 啟動雷達核心服務
        val captureIntent = Intent(this, CaptureService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(captureIntent)
        } else {
            startService(captureIntent)
        }

        // 啟動後直接返回桌面或遊戲畫面
        finish()
    }
}
