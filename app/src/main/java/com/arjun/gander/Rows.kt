package com.arjun.gander

import android.annotation.SuppressLint
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.accessibility.AccessibilityViewCommand
import androidx.recyclerview.widget.RecyclerView

/**
 * The rows of a file list: the home screen's recents and folders, and what is inside a .zip.
 *
 * One adapter for both, so a folder inside an archive looks and behaves exactly like a folder
 * on the phone, down to what TalkBack says about each row.
 */
internal sealed interface Row {
    data class Header(val title: String) : Row
    data class Hint(val text: String) : Row
    data class Item(
        val badge: String,
        val color: Int,
        val title: String,
        val subtitle: String?,
        val onClick: () -> Unit,
        /**
         * Asks whether to remove the row, and calls back if the answer is no, so a row swiped
         * away can come back. Null for a row that cannot be removed.
         */
        val onRemove: ((kept: () -> Unit) -> Unit)? = null,
        val thumbUri: Uri? = null,
        val thumbExt: String = ""
    ) : Row
}

internal class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val headerText: TextView = view.findViewById(R.id.headerText)
}

internal class HintViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val hintText: TextView = view.findViewById(R.id.hintText)
}

internal class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val badge: TextView = view.findViewById(R.id.badge)
    val thumb: ImageView = view.findViewById(R.id.thumb)
    val title: TextView = view.findViewById(R.id.title)
    val subtitle: TextView = view.findViewById(R.id.subtitle)
}

internal class RowAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val rows = mutableListOf<Row>()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(newRows: List<Row>) {
        rows.clear()
        rows.addAll(newRows)
        notifyDataSetChanged()
    }

    /**
     * Whether the row at [position] wants the whole width rather than one cell.
     *
     * Out-of-range answers full span on purpose: the layout manager can ask about a
     * position mid-update, and a header-shaped guess reflows harmlessly where a
     * cell-shaped one would throw.
     */
    fun isFullSpan(position: Int): Boolean =
        position !in rows.indices || rows[position] !is Row.Item

    /** Whether the row at [position] can be swiped away. See [SwipeToRemove]. */
    fun removable(position: Int): Boolean = (rows.getOrNull(position) as? Row.Item)?.onRemove != null

    /** The row at [position] was swiped away. It is asked about, and put back if it stays. */
    fun swiped(position: Int) {
        val putBack = { if (position in rows.indices) notifyItemChanged(position) }
        val remove = (rows.getOrNull(position) as? Row.Item)?.onRemove
        if (remove == null) putBack() else remove(putBack)
    }

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is Row.Header -> 0
        is Row.Hint -> 1
        is Row.Item -> 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> HeaderViewHolder(inflater.inflate(R.layout.row_header, parent, false))
            1 -> HintViewHolder(inflater.inflate(R.layout.row_hint, parent, false))
            else -> ItemViewHolder(inflater.inflate(R.layout.row_item, parent, false))
        }
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> (holder as HeaderViewHolder).headerText.text = row.title
            is Row.Hint -> (holder as HintViewHolder).hintText.text = row.text
            is Row.Item -> {
                val itemHolder = holder as ItemViewHolder
                itemHolder.badge.text = row.badge
                itemHolder.badge.background.mutate().setTint(row.color)
                itemHolder.badge.visibility = View.VISIBLE
                itemHolder.thumb.visibility = View.GONE
                itemHolder.thumb.setImageDrawable(null)
                itemHolder.thumb.tag = null
                if (row.thumbUri != null) {
                    Thumbs.load(
                        holder.itemView.context, row.thumbUri, row.thumbExt, itemHolder.thumb, itemHolder.badge
                    )
                }
                itemHolder.title.text = row.title
                itemHolder.subtitle.text = row.subtitle
                itemHolder.subtitle.visibility = if (row.subtitle == null) View.GONE else View.VISIBLE
                // The row children are not-important for accessibility, so this is
                // the whole announcement. Keeping the badge in it matters: the badge
                // is hidden once a thumbnail loads, and the file type would go with it
                holder.itemView.contentDescription =
                    listOfNotNull(row.title, row.badge, row.subtitle).joinToString(", ")
                holder.itemView.setOnClickListener { row.onClick() }
                // A swipe is how a row is removed, and nothing on screen says so, nor can a
                // screen reader make one. So TalkBack is offered Remove among the row's
                // actions, as dismiss, the action a view that can be swiped away has, and
                // rows that stay must not offer it.
                val remove = row.onRemove
                ViewCompat.replaceAccessibilityAction(
                    holder.itemView, AccessibilityActionCompat.ACTION_DISMISS,
                    remove?.let { holder.itemView.context.getString(R.string.remove) },
                    remove?.let { AccessibilityViewCommand { _, _ -> it {}; true } }
                )
            }
        }
    }
}
