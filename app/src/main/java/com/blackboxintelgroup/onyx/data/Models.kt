package com.blackboxintelgroup.onyx.data

import android.graphics.Bitmap
import android.webkit.WebView

data class TabInfo(
    val webView: WebView,
    var url: String = "",
    var title: String = "New Tab",
    var loading: Boolean = false,
    var favicon: Bitmap? = null
)

data class BookmarkItem(
    val url: String,
    val title: String,
    val timestamp: Long
)

data class HistoryItem(
    val url: String,
    val title: String,
    val timestamp: Long
)
