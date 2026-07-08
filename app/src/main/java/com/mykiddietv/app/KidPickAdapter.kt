package com.mykiddietv.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
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
    val rating: String = "",   // age-cert badge for movies ("R", "PG-13", "NR"…); "" = no badge
    val open: (() -> Unit)? = null
) {
    val pickId: String? get() = channel?.id ?: vod?.id ?: episode?.key
    val isPick: Boolean get() = pickId != null
}

class KidPickAdapter(
    private val isChecked: (KidNode) -> Boolean,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<KidPickAdapter.VH>() {

    private val items = ArrayList<KidNode>()

    fun submit(list: List<KidNode>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    fun nodeAt(pos: Int): KidNode = items[pos]

    class VH(val b: ItemCheckBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemCheckBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val n = items[position]
        val base = if (n.alreadyAdded) "✓ ${n.label}" else n.label
        holder.b.name.text = if (n.rating.isEmpty()) base else badged(base, n.rating)
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

    override fun getItemCount() = items.size

    /** Append a bold, colour-coded rating chip after the title (colour = maturity level). */
    private fun badged(base: String, cert: String): CharSequence {
        val sp = android.text.SpannableStringBuilder(base).append("    ")
        val start = sp.length
        sp.append(cert)
        val color = when (AgeBands.certLevel(cert)) {
            0 -> 0xFF19C37D.toInt()   // G / TV-Y — green
            1 -> 0xFF4F8CFF.toInt()   // PG / TV-PG — blue
            2 -> 0xFFFFB020.toInt()   // PG-13 / TV-14 — amber
            3 -> 0xFFFF5252.toInt()   // R / TV-MA — red
            else -> 0xFF8B97A5.toInt() // NR / unrated — grey
        }
        sp.setSpan(android.text.style.ForegroundColorSpan(color), start, sp.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        sp.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, sp.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return sp
    }
}
