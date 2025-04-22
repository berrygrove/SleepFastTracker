package nl.berrygrove.sft.ui.checkin

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import nl.berrygrove.sft.R
import nl.berrygrove.sft.data.model.WeightRecord
import java.time.format.DateTimeFormatter

class WeightRecordAdapter(
    private val context: Context,
    private var records: List<WeightRecord> = emptyList(),
    private var deltas: Map<Long, Float> = emptyMap(),
    private val onEditClick: (WeightRecord) -> Unit,
    private val onDeleteClick: (WeightRecord) -> Unit
) : RecyclerView.Adapter<WeightRecordAdapter.ViewHolder>() {

    private var isEditMode = false
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textViewWeight: TextView = itemView.findViewById(R.id.textViewWeight)
        val textViewTimestamp: TextView = itemView.findViewById(R.id.textViewTimestamp)
        val textViewDelta: TextView = itemView.findViewById(R.id.textViewDelta)
        val layoutEditControls: LinearLayout = itemView.findViewById(R.id.layoutEditControls)
        val buttonEdit: View = itemView.findViewById(R.id.buttonEdit)
        val buttonDelete: View = itemView.findViewById(R.id.buttonDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_weight_record, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = records[position]
        
        // Set weight and timestamp
        holder.textViewWeight.text = context.getString(R.string.weight_kg, record.weight)
        holder.textViewTimestamp.text = record.timestamp.format(dateTimeFormatter)
        
        // Set delta if available
        if (deltas.containsKey(record.id)) {
            val delta = deltas[record.id] ?: 0f
            if (delta > 0) {
                // Positive delta means weight increased in the next record (weight gain)
                holder.textViewDelta.text = context.getString(R.string.weight_delta_positive, delta)
                holder.textViewDelta.setTextColor(ContextCompat.getColor(context, R.color.error))
            } else if (delta < 0) {
                // Negative delta means weight decreased in the next record (weight loss)
                holder.textViewDelta.text = context.getString(R.string.weight_delta_negative, delta)
                holder.textViewDelta.setTextColor(ContextCompat.getColor(context, R.color.accent))
            } else {
                // No change
                holder.textViewDelta.text = context.getString(R.string.weight_delta_negative, 0f)
                holder.textViewDelta.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            }
            holder.textViewDelta.visibility = View.VISIBLE
        } else {
            holder.textViewDelta.visibility = View.GONE
        }
        
        // Show/hide edit controls based on edit mode
        holder.layoutEditControls.visibility = if (isEditMode) View.VISIBLE else View.GONE
        
        // Set click listeners for edit and delete
        holder.buttonEdit.setOnClickListener { onEditClick(record) }
        holder.buttonDelete.setOnClickListener { onDeleteClick(record) }
    }

    override fun getItemCount(): Int = records.size

    fun updateData(newRecords: List<WeightRecord>, newDeltas: Map<Long, Float>) {
        records = newRecords
        deltas = newDeltas
        notifyDataSetChanged()
    }
    
    fun setEditMode(editMode: Boolean) {
        if (isEditMode != editMode) {
            isEditMode = editMode
            notifyDataSetChanged()
        }
    }
} 