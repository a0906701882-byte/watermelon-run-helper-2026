# 西瓜跑刀助手 3.0

Android 輔助資訊顯示 App。此專案目前包含可移動浮窗、使用者授權後的螢幕擷取、右下角地圖名稱 OCR，以及實驗性的音訊節奏提示。

## 目前包含
- Android 浮窗權限引導與可拖曳浮窗
- Android MediaProjection 系統授權後進行螢幕擷取
- 只分析畫面右下角區域的中文 OCR，連續兩次辨識相同名稱後才鎖定地圖
- 支援零號大壩、長弓溪谷、航天基地、巴克什（舊版）；排除巴克什 2.0
- Android 10+ 嘗試擷取遊戲播放音訊；是否可擷取取決於遊戲與系統政策
- 以 RMS 音量脈衝節奏產生低可信度提示，並非已驗證的真人／AI 腳步辨識

## 重要限制
- App 不會操作遊戲、不讀取遊戲記憶體、不注入，也不繞過反作弊。
- 音訊節奏不能辨認特定動作或可靠區分真人與 AI。
- 尚未加入經驗證的物資座標、刷新機率、聲波方向或玩家位置座標模型。
- 浮窗與螢幕分享必須由使用者在 Android 系統設定／確認視窗中自行授權。

## GitHub Actions 編譯 APK
1. 開啟本 Repository 的 Actions 頁面。
2. 選擇 Build Android APK。
3. 等待「Run unit tests and build debug APK」完成。
4. 成功後在該次執行頁面下方的 Artifacts 下載 watermelon-run-helper-v3-apk。
5. 解壓縮 Artifact，取得 app-debug.apk。

工作流程會在 main 有新提交時自動執行，也可以從 Actions 手動執行。

## 首次安裝
1. 安裝並開啟 APK。
2. 依 Android 指示允許浮窗。
3. 需要使用音訊分析時，依提示處理相關權限。
4. 在 Android 螢幕分享系統視窗自行確認授權。
5. 若遊戲不允許音訊擷取，地圖 OCR 仍可獨立使用。
