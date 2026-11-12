companion object {
    const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
    const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
}
package com.watermelon.runhelper

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.media.projection.MediaProjectionManager

class MainActivity : Activity() {
    private val captureRequest = 4102
    private val audioRequest = 4103
    private val notificationRequest = 4104
    private var pendingFirstRun = false
    private var pendingCapture = false
    private var notificationAsked = false
    private var status: TextView? = null
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
            setBackgroundColor(0xFF15151B.toInt())
        }
        page.addView(TextView(this).apply {
            text = "🍉 西瓜跑刀助手 3.0"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFD166.toInt())
        }, LinearLayout.LayoutParams(-1, dp(60)))
        page.addView(TextView(this).apply {
            text = "首次啟動會依序詢問 Android 權限：浮窗設定、麥克風權限（音訊擷取用途），以及系統螢幕分享確認。你可以拒絕，之後再從下方按鈕重新開啟。"
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, dp(12), 0, dp(18))
        })
        status = TextView(this).apply {
            text = "狀態：等待權限確認"
            textSize = 13f
            setTextColor(0xFFB8E0D2.toInt())
            setPadding(0, 0, 0, dp(14))
        }
        page.addView(status)
        page.addView(btn("開始設定與啟動") { startPermissionFlow() })
        page.addView(btn("開啟／修復浮窗權限") { openOverlaySettings() })
        page.addView(btn("啟動／更新浮窗") { startOverlayIfAllowed() })
        page.addView(btn("開始畫面與音訊分析") { beginCaptureFlow() })
        page.addView(btn("停止分析") { stopService(Intent(this, CaptureService::class.java)); status?.text = "狀態：已要求停止分析" })
        page.addView(TextView(this).apply {
            text = "辨識限制：目前聲音分析只計算事件節奏，不能從音量直接辨認跳躍、翻越或換彈等聲音。真人／AI 結果是低可信度實驗線索；地圖依 OCR 文字自動切換，辨識不到就保留原地圖。"
            textSize = 12f
            setTextColor(0xFFBBBBBB.toInt())
            setPadding(0, dp(16), 0, 0)
        })
        setContentView(page)
        if (savedInstanceState == null) startPermissionFlow()
    }

    private fun btn(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { bottomMargin = dp(9) }
    }

    private fun startPermissionFlow() {
        if (!Settings.canDrawOverlays(this)) {
            pendingFirstRun = true
            status?.text = "狀態：請在 Android 設定允許顯示在其他應用程式上層"
            openOverlaySettings()
            return
        }
        startOverlayIfAllowed()
        pendingFirstRun = false
        requestOptionalAudioPermissionThenCapture()
    }

    private fun openOverlaySettings() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        if (pendingFirstRun && Settings.canDrawOverlays(this)) {
            pendingFirstRun = false
            startOverlayIfAllowed()
            requestOptionalAudioPermissionThenCapture()
        }
    }

    private fun startOverlayIfAllowed() {
        if (Settings.canDrawOverlays(this)) {
            startService(Intent(this, OverlayService::class.java))
            status?.text = "狀態：浮窗已啟動"
        } else {
            status?.text = "狀態：尚未允許浮窗顯示"
            openOverlaySettings()
        }
    }

    private fun requestOptionalAudioPermissionThenCapture() {
        if (Build.VERSION.SDK_INT >= 33 && !notificationAsked && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationAsked = true
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), notificationRequest)
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingCapture = true
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), audioRequest)
            return
        }
        requestProjection()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            notificationRequest -> requestOptionalAudioPermissionThenCapture()
            audioRequest -> {
                pendingCapture = false
                requestProjection()
            }
        }
    }

    private fun beginCaptureFlow() {
        if (!Settings.canDrawOverlays(this)) {
            pendingFirstRun = true
            openOverlaySettings()
            return
        }
        requestOptionalAudioPermissionThenCapture()
    }

    private fun requestProjection() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        status?.text = "狀態：等待你在 Android 系統視窗確認螢幕分享"
        startActivityForResult(manager.createScreenCaptureIntent(), captureRequest)
    }

    @Deprecated("Deprecated by Android; retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureRequest) {
            if (resultCode == RESULT_OK && data != null) {
                val service = Intent(this, CaptureService::class.java).apply {
                    putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
                    putExtra(CaptureService.EXTRA_RESULT_DATA, data)
                }
                ContextCompat.startForegroundService(this, service)
                status?.text = "狀態：已取得螢幕分享授權，正在啟動分析"
            } else {
                status?.text = "狀態：你尚未允許螢幕分享；浮窗仍可單獨使用"
            }
        }
    }
}
