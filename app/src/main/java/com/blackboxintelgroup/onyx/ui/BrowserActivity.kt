package com.blackboxintelgroup.onyx.ui

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blackboxintelgroup.onyx.R
import com.blackboxintelgroup.onyx.adblock.AdBlocker
import com.blackboxintelgroup.onyx.data.BookmarkItem
import com.blackboxintelgroup.onyx.data.HistoryItem
import com.blackboxintelgroup.onyx.data.OnyxPrefs
import com.blackboxintelgroup.onyx.data.TabInfo
import com.blackboxintelgroup.onyx.databinding.ActivityBrowserBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayout

class BrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBrowserBinding
    private lateinit var prefs: OnyxPrefs
    private lateinit var adBlocker: AdBlocker

    private val tabs = mutableListOf<TabInfo>()
    private var activeTabIndex = -1

    private val searchEngines = mapOf(
        "duckduckgo" to "https://duckduckgo.com/?q=%s",
        "google" to "https://www.google.com/search?q=%s",
        "bing" to "https://www.bing.com/search?q=%s",
        "brave" to "https://search.brave.com/search?q=%s"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = OnyxPrefs(this)
        adBlocker = AdBlocker(this)

        setupToolbar()
        setupTabBar()

        // Create first tab from intent or new tab
        val intentUrl = intent?.dataString
        createTab(intentUrl)

        // Handle status bar
        window.statusBarColor = ContextCompat.getColor(this, R.color.bg_primary)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.bg_primary)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.dataString?.let { url -> createTab(url) }
    }

    // ── Toolbar ────────────────────────────────────────────

    private fun setupToolbar() {
        binding.urlBar.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                navigateToInput(binding.urlBar.text.toString().trim())
                hideKeyboard()
                true
            } else false
        }

        binding.btnBack.setOnClickListener { currentWebView()?.goBack() }
        binding.btnForward.setOnClickListener { currentWebView()?.goForward() }
        binding.btnRefresh.setOnClickListener {
            currentWebView()?.let { wv ->
                if (wv.isLoading()) wv.stopLoading() else wv.reload()
            }
        }
        binding.btnTabs.setOnClickListener { showTabSwitcher() }
        binding.btnMenu.setOnClickListener { showMenu() }
        binding.btnHome.setOnClickListener { showNewTabPage() }
    }

    private fun navigateToInput(input: String) {
        if (input.isEmpty()) return
        val url = if (input.contains(".") && !input.contains(" ") &&
            (input.startsWith("http://") || input.startsWith("https://") || !input.startsWith("/"))) {
            if (input.startsWith("http://") || input.startsWith("https://")) input
            else "https://$input"
        } else {
            val engine = prefs.getSearchEngine()
            val template = searchEngines[engine] ?: searchEngines["duckduckgo"]!!
            template.replace("%s", Uri.encode(input))
        }
        loadUrl(url)
    }

    private fun loadUrl(url: String) {
        binding.newTabPage.visibility = View.GONE
        binding.webviewContainer.visibility = View.VISIBLE
        val wv = currentWebView() ?: return
        wv.visibility = View.VISIBLE
        wv.loadUrl(url)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.urlBar.windowToken, 0)
        binding.urlBar.clearFocus()
    }

    // ── WebView factory ────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        val wv = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                builtInZoomControls = true
                displayZoomControls = false
                setSupportMultipleWindows(true)
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                cacheMode = WebSettings.LOAD_DEFAULT
                allowFileAccess = false
                allowContentAccess = false
                userAgentString = settings.userAgentString.replace("; wv", "")
                    .replace("Version/4.0 ", "")
                if (prefs.getDoNotTrack()) {
                    // DNT header handled in shouldInterceptRequest
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    request?.url?.toString()?.let { url ->
                        if (prefs.getAdBlockEnabled() && adBlocker.shouldBlock(url)) {
                            return WebResourceResponse("text/plain", "UTF-8", null)
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    url?.let {
                        updateUrlBar(it)
                        getCurrentTab()?.apply {
                            this.url = it
                            loading = true
                        }
                        updateTabCounter()
                        binding.progressBar.visibility = View.VISIBLE
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    getCurrentTab()?.apply {
                        title = view?.title ?: "Untitled"
                        this.url = url ?: ""
                        loading = false
                    }
                    binding.progressBar.visibility = View.GONE
                    updateTabCounter()
                    // Save to history
                    url?.let { u ->
                        if (!u.startsWith("about:") && !u.startsWith("data:")) {
                            prefs.addHistory(HistoryItem(
                                url = u,
                                title = view?.title ?: "",
                                timestamp = System.currentTimeMillis()
                            ))
                        }
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url?.toString() ?: return false
                    // Handle intent:// and market:// schemes
                    if (url.startsWith("intent://") || url.startsWith("market://")) {
                        try {
                            startActivity(Intent.parseUri(url, Intent.URI_INTENT_SCHEME))
                        } catch (_: Exception) {}
                        return true
                    }
                    return false
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    binding.progressBar.progress = newProgress
                    if (newProgress == 100) {
                        binding.progressBar.visibility = View.GONE
                    }
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    getCurrentTab()?.title = title ?: "Untitled"
                    updateTabCounter()
                }

                override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                    getCurrentTab()?.favicon = icon
                }

                override fun onCreateWindow(
                    view: WebView?, isDialog: Boolean,
                    isUserGesture: Boolean, resultMsg: android.os.Message?
                ): Boolean {
                    val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                    val newWv = createWebView()
                    binding.webviewContainer.addView(newWv)
                    transport.webView = newWv
                    resultMsg.sendToTarget()
                    // Create a proper tab for this
                    val tab = TabInfo(webView = newWv)
                    tabs.add(tab)
                    switchToTab(tabs.size - 1)
                    return true
                }
            }

            setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimetype)
                    addRequestHeader("User-Agent", userAgent)
                    val filename = URLUtil.guessFileName(url, contentDisposition, mimetype)
                    setTitle(filename)
                    setDescription("Downloading file...")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                }
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                Toast.makeText(this@BrowserActivity, "Download started", Toast.LENGTH_SHORT).show()
            }
        }

        binding.webviewContainer.addView(wv)
        return wv
    }

    private fun WebView.isLoading(): Boolean {
        return getCurrentTab()?.loading == true
    }

    // ── Tab Management ─────────────────────────────────────

    private fun setupTabBar() {
        binding.btnTabs.text = "1"
    }

    fun createTab(url: String? = null) {
        val webView = createWebView()
        val tab = TabInfo(webView = webView, url = url ?: "")
        tabs.add(tab)
        switchToTab(tabs.size - 1)

        if (url != null && url.isNotEmpty()) {
            loadUrl(url)
        } else {
            showNewTabPage()
        }
    }

    private fun switchToTab(index: Int) {
        if (index < 0 || index >= tabs.size) return

        // Hide all webviews
        tabs.forEach { it.webView.visibility = View.GONE }

        activeTabIndex = index
        val tab = tabs[index]
        tab.webView.visibility = View.VISIBLE

        if (tab.url.isEmpty() || tab.url == "about:blank") {
            showNewTabPage()
        } else {
            binding.newTabPage.visibility = View.GONE
            binding.webviewContainer.visibility = View.VISIBLE
            updateUrlBar(tab.url)
        }
        updateTabCounter()
    }

    private fun closeTab(index: Int) {
        if (index < 0 || index >= tabs.size) return
        val tab = tabs[index]
        tab.webView.destroy()
        binding.webviewContainer.removeView(tab.webView)
        tabs.removeAt(index)

        if (tabs.isEmpty()) {
            createTab()
        } else {
            switchToTab(minOf(index, tabs.size - 1))
        }
    }

    private fun getCurrentTab(): TabInfo? {
        return if (activeTabIndex in tabs.indices) tabs[activeTabIndex] else null
    }

    private fun currentWebView(): WebView? = getCurrentTab()?.webView

    private fun updateTabCounter() {
        binding.btnTabs.text = tabs.size.toString()
    }

    private fun updateUrlBar(url: String) {
        if (url.isNotEmpty() && !url.startsWith("about:") && !url.startsWith("data:")) {
            binding.urlBar.setText(url)
            // Security icon
            val isSecure = url.startsWith("https://")
            binding.securityIcon.setImageResource(
                if (isSecure) R.drawable.ic_lock else R.drawable.ic_lock_open
            )
        }
    }

    private fun showNewTabPage() {
        binding.newTabPage.visibility = View.VISIBLE
        binding.webviewContainer.visibility = View.GONE

        binding.ntpSearchInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                navigateToInput(binding.ntpSearchInput.text.toString().trim())
                binding.ntpSearchInput.text?.clear()
                hideKeyboard()
                true
            } else false
        }
    }

    // ── Tab Switcher ───────────────────────────────────────

    private fun showTabSwitcher() {
        val dialog = BottomSheetDialog(this, R.style.Theme_OnyxBrowser_BottomSheet)
        val view = layoutInflater.inflate(R.layout.dialog_tabs, null)
        dialog.setContentView(view)

        val recycler = view.findViewById<RecyclerView>(R.id.tabsRecycler)
        val btnNewTab = view.findViewById<Button>(R.id.btnNewTab)

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = TabAdapter(tabs, activeTabIndex,
            onSelect = { idx ->
                switchToTab(idx)
                dialog.dismiss()
            },
            onClose = { idx ->
                closeTab(idx)
                if (tabs.isEmpty()) {
                    dialog.dismiss()
                } else {
                    recycler.adapter?.notifyDataSetChanged()
                }
            }
        )

        btnNewTab.setOnClickListener {
            createTab()
            dialog.dismiss()
        }

        dialog.show()
    }

    // ── Menu ───────────────────────────────────────────────

    private fun showMenu() {
        val dialog = BottomSheetDialog(this, R.style.Theme_OnyxBrowser_BottomSheet)
        val view = layoutInflater.inflate(R.layout.dialog_menu, null)
        dialog.setContentView(view)

        view.findViewById<LinearLayout>(R.id.menuBookmarks).setOnClickListener {
            dialog.dismiss(); showBookmarks()
        }
        view.findViewById<LinearLayout>(R.id.menuHistory).setOnClickListener {
            dialog.dismiss(); showHistory()
        }
        view.findViewById<LinearLayout>(R.id.menuNewTab).setOnClickListener {
            dialog.dismiss(); createTab()
        }
        view.findViewById<LinearLayout>(R.id.menuShare).setOnClickListener {
            dialog.dismiss()
            currentWebView()?.url?.let { url ->
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, url)
                }
                startActivity(Intent.createChooser(intent, "Share URL"))
            }
        }
        view.findViewById<LinearLayout>(R.id.menuAddBookmark).setOnClickListener {
            dialog.dismiss()
            val tab = getCurrentTab() ?: return@setOnClickListener
            if (tab.url.isNotEmpty()) {
                prefs.addBookmark(BookmarkItem(
                    url = tab.url,
                    title = tab.title.ifEmpty { tab.url },
                    timestamp = System.currentTimeMillis()
                ))
                Toast.makeText(this, "Bookmark added", Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<LinearLayout>(R.id.menuFindInPage).setOnClickListener {
            dialog.dismiss(); showFindInPage()
        }
        view.findViewById<LinearLayout>(R.id.menuDesktopSite).setOnClickListener {
            dialog.dismiss()
            currentWebView()?.settings?.let { s ->
                val desktop = !s.userAgentString.contains("Windows NT")
                s.userAgentString = if (desktop) {
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                } else {
                    WebSettings.getDefaultUserAgent(this)
                }
                currentWebView()?.reload()
                Toast.makeText(this,
                    if (desktop) "Desktop mode" else "Mobile mode",
                    Toast.LENGTH_SHORT).show()
            }
        }

        val adBlockSwitch = view.findViewById<Switch>(R.id.switchAdBlock)
        adBlockSwitch.isChecked = prefs.getAdBlockEnabled()
        adBlockSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.setAdBlockEnabled(checked)
            Toast.makeText(this,
                if (checked) "Ad blocker enabled" else "Ad blocker disabled",
                Toast.LENGTH_SHORT).show()
        }

        view.findViewById<LinearLayout>(R.id.menuSettings).setOnClickListener {
            dialog.dismiss(); showSettings()
        }

        dialog.show()
    }

    // ── Find in Page ───────────────────────────────────────

    private fun showFindInPage() {
        binding.findBar.visibility = View.VISIBLE
        binding.findInput.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.findInput, InputMethodManager.SHOW_IMPLICIT)

        binding.findInput.setOnEditorActionListener { _, _, _ ->
            val query = binding.findInput.text.toString()
            currentWebView()?.findAllAsync(query)
            true
        }
        binding.btnFindNext.setOnClickListener { currentWebView()?.findNext(true) }
        binding.btnFindPrev.setOnClickListener { currentWebView()?.findNext(false) }
        binding.btnFindClose.setOnClickListener {
            binding.findBar.visibility = View.GONE
            currentWebView()?.clearMatches()
            hideKeyboard()
        }
    }

    // ── Bookmarks ──────────────────────────────────────────

    private fun showBookmarks() {
        val dialog = BottomSheetDialog(this, R.style.Theme_OnyxBrowser_BottomSheet)
        val view = layoutInflater.inflate(R.layout.dialog_list, null)
        dialog.setContentView(view)

        view.findViewById<TextView>(R.id.listTitle).text = "Bookmarks"
        val recycler = view.findViewById<RecyclerView>(R.id.listRecycler)
        recycler.layoutManager = LinearLayoutManager(this)

        val bookmarks = prefs.getBookmarks()
        recycler.adapter = ListItemAdapter(
            items = bookmarks.map { ListItem(it.title, it.url, it.timestamp) },
            onClick = { item ->
                dialog.dismiss()
                loadUrl(item.subtitle)
            },
            onLongClick = { item ->
                prefs.removeBookmark(item.subtitle)
                Toast.makeText(this, "Bookmark removed", Toast.LENGTH_SHORT).show()
                recycler.adapter = ListItemAdapter(
                    items = prefs.getBookmarks().map { ListItem(it.title, it.url, it.timestamp) },
                    onClick = { i -> dialog.dismiss(); loadUrl(i.subtitle) },
                    onLongClick = {}
                )
            }
        )
        dialog.show()
    }

    // ── History ────────────────────────────────────────────

    private fun showHistory() {
        val dialog = BottomSheetDialog(this, R.style.Theme_OnyxBrowser_BottomSheet)
        val view = layoutInflater.inflate(R.layout.dialog_list, null)
        dialog.setContentView(view)

        view.findViewById<TextView>(R.id.listTitle).text = "History"
        val recycler = view.findViewById<RecyclerView>(R.id.listRecycler)
        recycler.layoutManager = LinearLayoutManager(this)

        val history = prefs.getHistory()
        recycler.adapter = ListItemAdapter(
            items = history.map { ListItem(it.title, it.url, it.timestamp) },
            onClick = { item ->
                dialog.dismiss()
                loadUrl(item.subtitle)
            },
            onLongClick = {}
        )

        val btnClear = view.findViewById<Button>(R.id.btnClearList)
        btnClear.visibility = View.VISIBLE
        btnClear.text = "Clear History"
        btnClear.setOnClickListener {
            prefs.clearHistory()
            dialog.dismiss()
            Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show()
        }
        dialog.show()
    }

    // ── Settings ───────────────────────────────────────────

    private fun showSettings() {
        val dialog = BottomSheetDialog(this, R.style.Theme_OnyxBrowser_BottomSheet)
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        dialog.setContentView(view)

        // Search engine spinner
        val spinner = view.findViewById<Spinner>(R.id.spinnerSearchEngine)
        val engines = listOf("DuckDuckGo", "Google", "Bing", "Brave")
        val engineKeys = listOf("duckduckgo", "google", "bing", "brave")
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, engines)
        spinner.setSelection(engineKeys.indexOf(prefs.getSearchEngine()).coerceAtLeast(0))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                prefs.setSearchEngine(engineKeys[pos])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Do Not Track
        val dntSwitch = view.findViewById<Switch>(R.id.switchDnt)
        dntSwitch.isChecked = prefs.getDoNotTrack()
        dntSwitch.setOnCheckedChangeListener { _, checked -> prefs.setDoNotTrack(checked) }

        // Clear data
        view.findViewById<Button>(R.id.btnClearData).setOnClickListener {
            WebStorage.getInstance().deleteAllData()
            CookieManager.getInstance().removeAllCookies(null)
            currentWebView()?.clearCache(true)
            currentWebView()?.clearHistory()
            prefs.clearHistory()
            Toast.makeText(this, "Browsing data cleared", Toast.LENGTH_SHORT).show()
        }

        // About
        view.findViewById<TextView>(R.id.aboutText).text =
            "Onyx Browser v0.1.0\nby Blackbox Intel Group"

        dialog.show()
    }

    // ── Back handling ──────────────────────────────────────

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            binding.findBar.isVisible -> {
                binding.findBar.visibility = View.GONE
                currentWebView()?.clearMatches()
            }
            currentWebView()?.canGoBack() == true -> currentWebView()?.goBack()
            tabs.size > 1 -> closeTab(activeTabIndex)
            else -> super.onBackPressed()
        }
    }

    override fun onDestroy() {
        tabs.forEach { it.webView.destroy() }
        super.onDestroy()
    }
}
