package org.diaabend.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import org.diaabend.R

class Row(
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val icon: Int,
    val onLongClick: (() -> Unit)? = null,
    val onClick: () -> Unit,
)

/** The one list layout both browsing screens use: a title, an optional message and D-pad friendly rows. */
class ListScreen(private val activity: Activity) {
    private val adapter = RowAdapter()
    private val list: ListView
    private val progress: View
    private val message: TextView
    val title: TextView

    init {
        activity.setContentView(R.layout.list_screen)
        title = activity.findViewById(R.id.title)
        progress = activity.findViewById(R.id.progress)
        message = activity.findViewById(R.id.message)
        list = activity.findViewById(R.id.list)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> adapter.rows[position].onClick() }
        list.setOnItemLongClickListener { _, _, position, _ ->
            val action = adapter.rows[position].onLongClick
            action?.invoke()
            action != null
        }
    }

    fun setRows(rows: List<Row>) {
        adapter.rows = rows
        adapter.notifyDataSetChanged()
        list.requestFocus()
    }

    fun setBusy(busy: Boolean) {
        progress.visibility = if (busy) View.VISIBLE else View.INVISIBLE
    }

    fun setMessage(text: CharSequence?) {
        message.text = text
        message.visibility = if (text == null) View.GONE else View.VISIBLE
    }

    private inner class RowAdapter : BaseAdapter() {
        var rows: List<Row> = emptyList()

        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = rows[position].key.hashCode().toLong()
        override fun hasStableIds() = true

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: activity.layoutInflater.inflate(R.layout.row, parent, false)
            val row = rows[position]
            view.findViewById<ImageView>(R.id.icon).setImageResource(row.icon)
            view.findViewById<TextView>(R.id.row_title).text = row.title
            view.findViewById<TextView>(R.id.row_subtitle).apply {
                text = row.subtitle
                visibility = if (row.subtitle == null) View.GONE else View.VISIBLE
            }
            return view
        }
    }
}
