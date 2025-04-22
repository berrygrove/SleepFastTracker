package nl.berrygrove.sft.ui.streaks

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import nl.berrygrove.sft.R
import nl.berrygrove.sft.data.model.Achievement

/**
 * Adapter for displaying achievements in a RecyclerView
 */
class AchievementAdapter : ListAdapter<Achievement, AchievementAdapter.AchievementViewHolder>(AchievementDiffCallback()) {

    // Flag to determine if category icons should be shown (true for "All" tab, false for others)
    private var showCategoryIcons = false
    
    fun setShowCategoryIcons(show: Boolean) {
        showCategoryIcons = show
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AchievementViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_achievement, parent, false)
        return AchievementViewHolder(view)
    }

    override fun onBindViewHolder(holder: AchievementViewHolder, position: Int) {
        val achievement = getItem(position)
        holder.bind(achievement, showCategoryIcons)
    }

    class AchievementViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val emoticonView: TextView = itemView.findViewById(R.id.tv_achievement_emoticon)
        private val nameView: TextView = itemView.findViewById(R.id.tv_achievement_name)
        private val descriptionView: TextView = itemView.findViewById(R.id.tv_achievement_description)
        private val pointsView: TextView = itemView.findViewById(R.id.tv_achievement_points)
        private val statusView: ImageView = itemView.findViewById(R.id.iv_achievement_status)
        private val categoryIconView: ImageView = itemView.findViewById(R.id.iv_achievement_category)

        fun bind(achievement: Achievement, showCategoryIcon: Boolean) {
            emoticonView.text = achievement.emoticon
            nameView.text = achievement.name
            descriptionView.text = achievement.description
            pointsView.text = itemView.context.getString(R.string.points_format, achievement.points)
            
            // Show/hide and set the category icon if needed
            if (showCategoryIcon) {
                categoryIconView.visibility = View.VISIBLE
                
                // Set the appropriate icon based on the category
                when (achievement.category) {
                    "sleep" -> categoryIconView.setImageResource(R.drawable.ic_sleep)
                    "fasting" -> categoryIconView.setImageResource(R.drawable.ic_fasting)
                    "weight" -> categoryIconView.setImageResource(R.drawable.ic_weight)
                    else -> categoryIconView.visibility = View.GONE
                }
                
                // Set the appropriate tint color
                when (achievement.category) {
                    "sleep" -> categoryIconView.setColorFilter(itemView.context.getColor(R.color.sleep_color))
                    "fasting" -> categoryIconView.setColorFilter(itemView.context.getColor(R.color.fasting_color))
                    "weight" -> categoryIconView.setColorFilter(itemView.context.getColor(R.color.weight_color))
                }
            } else {
                categoryIconView.visibility = View.GONE
            }
            
            // Update status icon based on whether achievement is achieved
            if (achievement.achieved) {
                statusView.setImageResource(R.drawable.ic_check_circle)
                statusView.setColorFilter(itemView.context.getColor(R.color.success))
                statusView.contentDescription = itemView.context.getString(R.string.achievement_unlocked_description)
                
                // Make everything fully visible
                itemView.alpha = 1.0f
            } else {
                statusView.setImageResource(R.drawable.ic_lock)
                statusView.setColorFilter(itemView.context.getColor(R.color.text_secondary))
                statusView.contentDescription = itemView.context.getString(R.string.achievement_locked_description)
                
                // Make locked achievements partially transparent
                itemView.alpha = 0.6f
            }
        }
    }
}

/**
 * DiffUtil callback for optimizing RecyclerView updates
 */
class AchievementDiffCallback : DiffUtil.ItemCallback<Achievement>() {
    override fun areItemsTheSame(oldItem: Achievement, newItem: Achievement): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Achievement, newItem: Achievement): Boolean {
        return oldItem == newItem
    }
} 