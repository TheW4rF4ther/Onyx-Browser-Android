package com.blackboxintelgroup.onyx.adblock

import android.content.Context

class AdBlocker(context: Context) {

    private val blockedDomains = HashSet<String>()

    init {
        // Common ad/tracking domains
        val domains = listOf(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "google-analytics.com", "googletagmanager.com", "googletagservices.com",
            "adnxs.com", "adsrvr.org", "adcolony.com", "admob.com",
            "facebook.com/tr", "connect.facebook.net/en_US/fbevents.js",
            "amazon-adsystem.com", "ads.yahoo.com", "ads.twitter.com",
            "moatads.com", "scorecardresearch.com", "quantserve.com",
            "taboola.com", "outbrain.com", "revcontent.com",
            "criteo.com", "criteo.net", "casalemedia.com",
            "pubmatic.com", "rubiconproject.com", "openx.net",
            "bidswitch.net", "sharethrough.com", "spotxchange.com",
            "advertising.com", "adform.net", "serving-sys.com",
            "2mdn.net", "3lift.com", "33across.com",
            "ad.doubleclick.net", "pagead2.googlesyndication.com",
            "tpc.googlesyndication.com", "ssl.google-analytics.com",
            "stats.wp.com", "pixel.wp.com",
            "hotjar.com", "mouseflow.com", "crazyegg.com",
            "analytics.tiktok.com", "ads.tiktok.com",
            "tr.snapchat.com", "sc-static.net/scevent.min.js"
        )
        blockedDomains.addAll(domains)
    }

    fun shouldBlock(url: String): Boolean {
        val lower = url.lowercase()
        return blockedDomains.any { domain -> lower.contains(domain) }
    }
}
