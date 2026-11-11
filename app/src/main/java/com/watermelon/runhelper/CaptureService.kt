<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="240dp"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:background="#CC141414"
    android:padding="10dp"
    android:elevation="8dp">

    <!-- 頂部：助手標題（可拖曳區塊） -->
    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="🍉 西瓜跑刀助手 3.0 | 拖曳移動"
        android:textColor="#FF5252"
        android:textSize="11sp"
        android:textStyle="bold"
        android:paddingBottom="4dp"/>

    <!-- 第一部分：原本的系統與擷取狀態 -->
    <TextView
        android:id="@+id/tvScreenStatus"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="畫面：等待授權擷取 (🟢)"
        android:textColor="#00E676"
        android:textSize="9sp"
        android:paddingBottom="2dp"/>

    <TextView
        android:id="@+id/tvMapStatus"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="地圖：等待畫面辨識"
        android:textColor="#FFFFFF"
        android:textSize="9sp"
        android:paddingBottom="2dp"/>

    <TextView
        android:id="@+id/tvAudioStatus"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="聲音：尚未分析"
        android:textColor="#FFFFFF"
        android:textSize="9sp"
        android:paddingBottom="2dp"/>

    <TextView
        android:id="@+id/tvFootstepStatus"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="腳步判斷：不確定 | 等待聲音資料"
        android:textColor="#FF9800"
        android:textSize="9sp"
        android:paddingBottom="4dp"/>

    <!-- 分隔線 -->
    <View
        android:layout_width="match_parent"
        android:layout_height
