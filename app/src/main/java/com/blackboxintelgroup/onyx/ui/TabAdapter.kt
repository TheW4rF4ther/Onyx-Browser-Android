package com.blackboxintelgroup.onyx.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.blackboxintelgroup.onyx.R
import com.blackboxintelgroup.onyx.data.TabInfo

class TabAdapter(
    private val tabs: List<TabInfo>,
    private val activeIndex: Int,
    private val onSelect: (Int) -> Unit,
    private val onClose: (Int) -> Unit
) : RecyclerView.Adapter<TabAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tabTitle)
        val url: TextView = view.findViewById(R.id.tabUrl)
        val favicon: ImageView = view.findViewById(R.id.tabFavicon)
        val closeBtn: ImageButton = view.findViewById(R.id.tabCloseBtn)
        val card: View = view.findViewById(R.id.tabCard)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tab, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val tab = tabs[position]
        holder.title.text = tab.title.ifEmpty { "New Tab" }
        holder.url.text = tab.url.ifEmpty { "about:blank" }
        tab.favicon?.let { holder.favicon.setImageBitmap(it) }

        if (position == activeIndex) {
            holder.card.setBackgroundResource(R.drawable.tab_card_active)
        } else {
            holder.card.setBackgroundResource(R.drawable.tab_card)
        }

        holder.card.setOnClickListener { onSelect(position) }
        holder.closeBtn.setOnClickListener { onClose(position) }
    }

    override fun getItemCount() = tabs.size
}
