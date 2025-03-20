package com.timetracking.app.ui.history.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.timetracking.app.R
import com.timetracking.app.data.model.TimeRecord
import com.timetracking.app.ui.history.TimeEditBottomSheet
import com.timetracking.app.ui.history.model.TimeRecordBlock
import java.text.SimpleDateFormat
import java.util.*

class TimeRecordBlockAdapter(
    private val onCheckInClick: (TimeRecord) -> Unit,
    private val onCheckOutClick: (TimeRecord) -> Unit,
    private val onBlockUpdated: () -> Unit,
    private val onRecordDeleted: () -> Unit  // Nuevo parámetro añadido
) : ListAdapter<TimeRecordBlock, TimeRecordBlockAdapter.ViewHolder>(BlockDiffCallback()) {

    private val dateFormat = SimpleDateFormat("EEEE, d MMM", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_time_record_block, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val dateText: TextView = view.findViewById(R.id.dateText)
        private val checkInTime: TextView = view.findViewById(R.id.checkInTime)
        private val checkOutTime: TextView = view.findViewById(R.id.checkOutTime)
        private val duration: TextView = view.findViewById(R.id.duration)

        fun bind(block: TimeRecordBlock) {
            dateText.text = dateFormat.format(block.date)
            checkInTime.text = timeFormat.format(block.checkIn.date)

            if (block.checkOut != null) {
                checkOutTime.text = timeFormat.format(block.checkOut.date)
                val hours = block.duration / 60
                val minutes = block.duration % 60
                duration.text = "Duración: ${hours}h ${minutes}m"
            } else {
                checkOutTime.text = "Pendiente"
                duration.text = "En curso"
            }

            itemView.setOnLongClickListener {
                (itemView.context as? FragmentActivity)?.let { activity ->
                    TimeEditBottomSheet.newInstance(
                        block,
                        object : TimeEditBottomSheet.Callback {
                            override fun onTimeUpdated() {
                                onBlockUpdated()
                            }

                            override fun onRecordDeleted() {
                                onRecordDeleted() // Llama al callback de eliminación
                            }
                        }
                    ).show(activity.supportFragmentManager, "timeEdit")
                }
                true
            }

            checkInTime.setOnClickListener { onCheckInClick(block.checkIn) }
            checkOutTime.setOnClickListener {
                block.checkOut?.let { onCheckOutClick(it) }
            }
        }
    }

    class BlockDiffCallback : DiffUtil.ItemCallback<TimeRecordBlock>() {
        override fun areItemsTheSame(oldItem: TimeRecordBlock, newItem: TimeRecordBlock): Boolean {
            return oldItem.checkIn.id == newItem.checkIn.id
        }

        override fun areContentsTheSame(oldItem: TimeRecordBlock, newItem: TimeRecordBlock): Boolean {
            return oldItem == newItem
        }
    }
}