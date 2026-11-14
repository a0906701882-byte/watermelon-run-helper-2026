// 1. 在 ControlPanelService 中建立廣播接收器：
private val countdownReceiver = object : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == ScreenCaptureService.ACTION_COUNTDOWN_UPDATE) {
            val timeText = intent.getStringExtra(ScreenCaptureService.EXTRA_TIME_TEXT) ?: "--:--"
            val isEmergency = intent.getBooleanExtra(ScreenCaptureService.EXTRA_IS_EMERGENCY, false)

            // 更新你懸浮窗上的倒數 TextView（請將 tvCountdown 換成你介面上的元件名稱）
            tvCountdown?.text = "撤離倒數: $timeText"

            if (isEmergency) {
                // 最後 90 秒自動轉為紅色高對比警示
                tvCountdown?.setTextColor(android.graphics.Color.parseColor("#FF3B30"))
            } else {
                // 平常維持黑金風格的金色字體
                tvCountdown?.setTextColor(android.graphics.Color.parseColor("#FFD700"))
            }
        }
    }
}

// 2. 在 ControlPanelService 的 onCreate() 中註冊：
val filter = android.content.IntentFilter(ScreenCaptureService.ACTION_COUNTDOWN_UPDATE)
if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
    registerReceiver(countdownReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
} else {
    registerReceiver(countdownReceiver, filter)
}

// 3. 記得在 onDestroy() 中取消註冊：
unregisterReceiver(countdownReceiver)
