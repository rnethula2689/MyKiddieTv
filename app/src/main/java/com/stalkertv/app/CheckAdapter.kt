package com.stalkertv.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.stalkertv.app.databinding.ItemCheckBinding

/** A list row with a checkbox indicator, used to whitelist kid content. */
class CheckAdapter(private val onToggle: (Int) -> Unit) : RecyclerView.Adapter<CheckAdapter.VH>() {

    data class Item(val id: String, val label: String, val iconUrl: String?, var checked: Boolean)

    private val items = ArrayList<Item>()

    fun submit(list: List<Item>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun itemAt(pos: Int): Item = items[pos]

    class VH(val b: ItemCheckBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemCheckBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.b.name.text = item.label
        holder.b.check.text = if (item.checked) "✅" else "◻"
        holder.b.check.setTextColor(if (item.checked) 0xFF19c37d.toInt() else 0xFF8b97a5.toInt())
        if (item.iconUrl.isNullOrEmpty()) {
            holder.b.thumb.visibility = View.GONE
            holder.b.thumb.setImageDrawable(null)
        } else {
            holder.b.thumb.visibility = View.VISIBLE
            holder.b.thumb.load(item.iconUrl) {
                crossfade(true); placeholder(R.drawable.thumb_placeholder); error(R.drawable.thumb_placeholder)
            }
        }
        holder.b.root.setOnClickListener { onToggle(holder.bindingAdapterPosition) }
    }

    override fun getItemCount() = items.size
}
