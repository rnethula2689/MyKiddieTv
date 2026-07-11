package com.mykiddietv.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.mykiddietv.app.databinding.ItemCatChipBinding
import com.mykiddietv.app.databinding.ItemCheckBinding
import com.mykiddietv.app.databinding.ItemVodPosterBinding

/**
 * One browse row in "Manage Kid Content". A node is either:
 *  • a folder (open != null) — tap navigates in, shows a "›";
 *  • a pick (channel or vod != null) — tap toggles a checkbox.
 *  • a poster — a movie pick or a series folder that renders with cover art.
 * [isSeries] marks a series folder (open != null but drills into seasons); combined with
 * [vod] != null it decides the POSTER view type so movies + series both show artwork.
 */
data class KidNode(
    val label: String,
    val icon: String?,
    val sortKey: String,
    val channel: Portal.Channel? = null,
    val vod: Portal.VodItem? = null,
    val episode: Profiles.KidEpisode? = null,
    val alreadyAdded: Boolean = false,
    val isSeries: Boolean = false,
    val open: (() -> Unit)? = null
) {
    val pickId: String? get() = channel?.id ?: vod?.id ?: episode?.key
    val isPick: Boolean get() = pickId != null
    /** A movie pick or a series folder → renders as a poster card with cover art. */
    val isPosterNode: Boolean get() = vod != null || isSeries
}

class KidPickAdapter(
    private val isChecked: (KidNode) -> Boolean,
    private val onClick: (Int) -> Unit,
    private val onLongClick: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = ArrayList<KidNode>()

    fun submit(list: List<KidNode>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    /** Append rows to the end without rebinding what's shown (keeps scroll + focus — used by the
     *  progressive VOD page-load, where pages append while the user is already browsing). */
    fun append(list: List<KidNode>) {
        if (list.isEmpty()) return
        val start = items.size
        items.addAll(list)
        notifyItemRangeInserted(start, list.size)
    }

    fun nodeAt(pos: Int): KidNode = items[pos]

    /** Folders render as compact chips that tile 2-4 per row (parent-side look); picks stay full-width rows.
     *  A *poster* node (movie pick / series folder) is excluded here — it tiles as a poster, not a chip. */
    fun isFolder(pos: Int): Boolean = items.getOrNull(pos)?.let { it.open != null && !it.isPosterNode } == true

    /** Poster nodes (movie picks + series folders) tile as cover-art cards. */
    fun isPoster(pos: Int): Boolean = items.getOrNull(pos)?.isPosterNode == true

    override fun getItemViewType(position: Int): Int {
        val n = items[position]
        return when {
            n.isPosterNode -> T_POSTER
            n.open != null -> T_CHIP
            else -> T_ROW
        }
    }

    class VH(val b: ItemCheckBinding) : RecyclerView.ViewHolder(b.root)
    class ChipVH(val b: ItemCatChipBinding) : RecyclerView.ViewHolder(b.root)
    class PosterVH(val b: ItemVodPosterBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            T_CHIP -> ChipVH(ItemCatChipBinding.inflate(inf, parent, false))
            T_POSTER -> PosterVH(ItemVodPosterBinding.inflate(inf, parent, false))
            else -> VH(ItemCheckBinding.inflate(inf, parent, false))
        }
    }

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
        if (h is ChipVH) { bindChip(h, items[position]); return }
        if (h is PosterVH) { bindPoster(h, items[position]); return }
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
        holder.b.root.setOnLongClickListener {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onLongClick?.invoke(p)
            true
        }
    }

    /**
     * A movie pick or a series folder rendered as a poster card (reuses ItemVodPosterBinding — the
     * same look as the kid's browse grid). For a MOVIE pick the corner badge ([posterStar]) doubles as
     * the check indicator: ✅ when selected/added, ◻ otherwise. For a SERIES folder the badge is hidden
     * (no checkbox — tapping drills into seasons).
     */
    private fun bindPoster(holder: PosterVH, n: KidNode) {
        holder.b.posterRoot.setOnFocusChangeListener { v, hasFocus ->
            val s = if (hasFocus) 1.10f else 1f
            v.animate().scaleX(s).scaleY(s).setDuration(120).start()
            v.elevation = if (hasFocus) 12f else 0f
        }
        // Title fallback so a missing/broken poster is still identifiable (art carries it otherwise).
        holder.b.cardLabel.text = n.label.dropWhile { !it.isLetterOrDigit() }.ifBlank { n.label }
        val url = n.icon
        if (url.isNullOrEmpty()) {
            holder.b.posterImg.setImageDrawable(null)
            holder.b.cardLabel.visibility = View.VISIBLE
        } else {
            holder.b.cardLabel.visibility = View.GONE
            holder.b.posterImg.load(url) {
                crossfade(true)
                listener(
                    onError = { _, _ -> holder.b.cardLabel.visibility = View.VISIBLE },
                    onSuccess = { _, _ -> holder.b.cardLabel.visibility = View.GONE }
                )
            }
        }
        // Corner badge = pick check indicator for movies; hidden for series folders.
        if (n.isPick) {
            val on = isChecked(n) || n.alreadyAdded
            holder.b.posterStar.visibility = View.VISIBLE
            holder.b.posterStar.text = if (on) "✅" else "◻"
            holder.b.posterStar.setTextColor(if (on) 0xFF19c37d.toInt() else 0xFFFFFFFF.toInt())
        } else {
            holder.b.posterStar.visibility = View.GONE
        }
        holder.b.posterStar.setOnClickListener(null)
        holder.b.posterStar.isClickable = false
        holder.b.posterRoot.setOnClickListener {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onClick(p)
        }
        holder.b.posterRoot.setOnLongClickListener {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onLongClick?.invoke(p)
            true
        }
    }

    private fun bindChip(holder: ChipVH, n: KidNode) {
        holder.b.chipName.text = n.label
        holder.b.chipRoot.setOnClickListener {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onClick(p)
        }
        holder.b.chipRoot.setOnLongClickListener {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onLongClick?.invoke(p)
            true
        }
    }

    override fun getItemCount() = items.size

    companion object { const val T_ROW = 0; const val T_CHIP = 1; const val T_POSTER = 2 }
}
