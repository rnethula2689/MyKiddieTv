package com.mykiddietv.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.mykiddietv.app.databinding.ItemCatChipBinding
import com.mykiddietv.app.databinding.ItemCheckBinding

/**
 * One browse row in "Manage Kid Content". A node is either:
 *  • a folder (open != null) — tap navigates in, shows a "›";
 *  • a pick (channel or vod != null) — tap toggles a checkbox.
 */
data class KidNode(
    val label: String,
    val icon: String?,
    val sortKey: String,
    val channel: Portal.Channel? = null,
    val vod: Portal.VodItem? = null,
    val episode: Profiles.KidEpisode? = null,
    val alreadyAdded: Boolean = false,
    val open: (() -> Unit)? = null
) {
    val pickId: String? get() = channel?.id ?: vod?.id ?: episode?.key
    val isPick: Boolean get() = pickId != null
}

class KidPickAdapter(
    private val isChecked: (KidNode) -> Boolean,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = ArrayList<KidNode>()

    fun submit(list: List<KidNode>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    fun nodeAt(pos: Int): KidNode = items[pos]

    /** Folders render as compact chips that tile 2-4 per row (parent-side look); picks stay full-width rows. */
    fun isFolder(pos: Int): Boolean = items.getOrNull(pos)?.open != null

    override fun getItemViewType(position: Int) = if (items[position].open != null) T_CHIP else T_ROW

    class VH(val b: ItemCheckBinding) : RecyclerView.ViewHolder(b.root)
    class ChipVH(val b: ItemCatChipBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == T_CHIP) ChipVH(ItemCatChipBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        else VH(ItemCheckBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
        if (h is ChipVH) { bindChip(h, items[position]); return }
        val holder = h as VH
        val n = items[position]
        holder.b.name.text = if (n.alreadyAdded) "✓ ${n.label}" else n.label
        if (n.isPick) {
            val on = isChecked(n)
            holder.b.check.text = if (on) "✅" else "◻"
            holder.b.check.setTextColor(if (on) 0xFF19c37d.toInt() else 0xFF8b97a5.toInt())
        } else {
            holder.b.check.text = "›"
            holder.b.check.setTextColor(0xFF8b97a5.toInt())
        }
        if (n.icon.isNullOrEmpty()) {
            holder.b.thumb.visibility = View.GONE
            holder.b.thumb.setImageDrawable(null)
        } else {
            holder.b.thumb.visibility = View.VISIBLE
            holder.b.thumb.load(n.icon) {
                crossfade(true); placeholder(R.drawable.thumb_placeholder); error(R.drawable.thumb_placeholder)
            }
        }
        holder.b.root.setOnClickListener {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onClick(p)
        }
    }

    private fun bindChip(holder: ChipVH, n: KidNode) {
        holder.b.chipName.text = n.label
        holder.b.chipRoot.setOnClickListener {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onClick(p)
        }
    }

    override fun getItemCount() = items.size

    companion object { const val T_ROW = 0; const val T_CHIP = 1 }
}
