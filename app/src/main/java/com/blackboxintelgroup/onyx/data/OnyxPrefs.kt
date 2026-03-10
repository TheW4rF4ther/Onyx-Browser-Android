package com.blackboxintelgroup.onyx.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class OnyxPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("onyx_prefs", Context.MODE_PRIVATE)

    // ── Search Engine ──────────────────────────────────────

    fun getSearchEngine(): String = prefs.getString("search_engine", "duckduckgo") ?: "duckduckgo"

    fun setSearchEngine(engine: String) {
        prefs.edit().putString("search_engine", engine).apply()
    }

    // ── Ad Blocker ─────────────────────────────────────────

    fun getAdBlockEnabled(): Boolean = prefs.getBoolean("ad_block", true)

    fun setAdBlockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("ad_block", enabled).apply()
    }

    // ── Do Not Track ───────────────────────────────────────

    fun getDoNotTrack(): Boolean = prefs.getBoolean("dnt", true)

    fun setDoNotTrack(enabled: Boolean) {
        prefs.edit().putBoolean("dnt", enabled).apply()
    }

    // ── Bookmarks ──────────────────────────────────────────

    fun getBookmarks(): List<BookmarkItem> {
        val json = prefs.getString("bookmarks", "[]") ?: "[]"
        return parseBookmarks(json)
    }

    fun addBookmark(item: BookmarkItem) {
        val list = getBookmarks().toMutableList()
        // Remove duplicate
        list.removeAll { it.url == item.url }
        list.add(0, item)
        saveBookmarks(list)
    }

    fun removeBookmark(url: String) {
        val list = getBookmarks().toMutableList()
        list.removeAll { it.url == url }
        saveBookmarks(list)
    }

    private fun saveBookmarks(list: List<BookmarkItem>) {
        val arr = JSONArray()
        list.forEach { bm ->
            arr.put(JSONObject().apply {
                put("url", bm.url)
                put("title", bm.title)
                put("timestamp", bm.timestamp)
            })
        }
        prefs.edit().putString("bookmarks", arr.toString()).apply()
    }

    private fun parseBookmarks(json: String): List<BookmarkItem> {
        val list = mutableListOf<BookmarkItem>()
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(BookmarkItem(
                url = obj.getString("url"),
                title = obj.getString("title"),
                timestamp = obj.optLong("timestamp", 0)
            ))
        }
        return list
    }

    // ── History ────────────────────────────────────────────

    fun getHistory(): List<HistoryItem> {
        val json = prefs.getString("history", "[]") ?: "[]"
        return parseHistory(json)
    }

    fun addHistory(item: HistoryItem) {
        val list = getHistory().toMutableList()
        // Remove duplicate
        list.removeAll { it.url == item.url }
        list.add(0, item)
        // Keep only 500 entries
        if (list.size > 500) list.subList(500, list.size).clear()
        saveHistory(list)
    }

    fun clearHistory() {
        prefs.edit().putString("history", "[]").apply()
    }

    private fun saveHistory(list: List<HistoryItem>) {
        val arr = JSONArray()
        list.forEach { h ->
            arr.put(JSONObject().apply {
                put("url", h.url)
                put("title", h.title)
                put("timestamp", h.timestamp)
            })
        }
        prefs.edit().putString("history", arr.toString()).apply()
    }

    private fun parseHistory(json: String): List<HistoryItem> {
        val list = mutableListOf<HistoryItem>()
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(HistoryItem(
                url = obj.getString("url"),
                title = obj.optString("title", ""),
                timestamp = obj.optLong("timestamp", 0)
            ))
        }
        return list
    }
}
