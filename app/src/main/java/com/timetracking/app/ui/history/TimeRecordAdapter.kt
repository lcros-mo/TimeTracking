package com.timetracking.app.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.timetracking.app.R
import com.timetracking.app.data.model.TimeRecord
import com.timetracking.app.data.model.RecordType
import java.text.SimpleDateFormat
import java.util.*

class TimeRecordAdapter(
    private val onItemLongClick: (TimeRecord) -> Unit
) : ListAdapter<TimeRecord, TimeRecordAdapter.ViewHolder>(TimeRecordDiffCallback()) {

    private val dateFormat = SimpleDateFormat("EEEE, d MMM", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_time_record, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = getItem(position)
        holder.bind(record)
        holder.itemView.setOnLongClickListener {
            onItemLongClick(record)
            true
        }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val dateText: TextView = view.findViewById(R.id.dateText)
        private val timeText: TextView = view.findViewById(R.id.timeText)
        private val typeText: TextView = view.findViewById(R.id.typeText)

        fun bind(record: TimeRecord) {
            dateText.text = dateFormat.format(record.date)
            timeText.text = timeFormat.format(record.date)
            typeText.text = when (record.type) {
                RecordType.CHECK_IN -> "Entrada"
                RecordType.CHECK_OUT -> "Salida"
            }
        }
    }

    private class TimeRecordDiffCallback : DiffUtil.ItemCallback<TimeRecord>() {
        override fun areItemsTheSame(oldItem: TimeRecord, newItem: TimeRecord): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TimeRecord, newItem: TimeRecord): Boolean {
            return oldItem == newItem
        }
    }
}