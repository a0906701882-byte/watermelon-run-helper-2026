// 1. 在 MainActivity.kt 類別成員變數中加入：
private val screenCaptureLauncher = registerForActivityResult(
    androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
) { result ->
    if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
        val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_START
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        android.widget.Toast.makeText(this, "✅ 自動偵測撤離倒數已啟動！", android.widget.Toast.LENGTH_SHORT).show()
    }
}

// 2. 在你原本「啟動懸浮窗」的按鈕點擊事件中，一併呼叫這個方法：
private fun requestAutoCountdownPermission() {
    val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
    screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
}
