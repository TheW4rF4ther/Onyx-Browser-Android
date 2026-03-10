package com.blackboxintelgroup.onyx.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.blackboxintelgroup.onyx.R
import java.text.SimpleDateFormat
import java.util.*

data class ListItem(
    val title: String,
    val subtitle: String,
    val timestamp: Long
)

class ListItemAdapter(
    private val items: List<ListItem>,
    private val onClick: (ListItem) -> Unit,
    private val onLongClick: (ListItem) -> Unit
) : RecyclerView.Adapter<ListItemAdapter.VH>() {

    private val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.itemTitle)
        val subtitle: TextView = view.findViewById(R.id.itemSubtitle)
        val date: TextView = view.findViewById(R.id.itemDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_list, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = item.title.ifEmpty { item.subtitle }
        holder.subtitle.text = item.subtitle
        holder.date.text = if (item.timestamp > 0) dateFormat.format(Date(item.timestamp)) else ""

        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener { onLongClick(item); true }
    }

    override fun getItemCount() = items.size
}
