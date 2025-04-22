package nl.berrygrove.sft.utils

/**
 * Utility class for emoji-related functions
 */
object EmojiUtils {
    /**
     * Returns a specific emoticon based on the streak length
     */
    fun getEmoticonsByStreakLength(days: Int): String {
        return when {
            days >= 1179 -> "⚜️"  // Ultimate achievement
            days >= 982 -> "💎"
            days >= 818 -> "👑"
            days >= 681 -> "🏆"
            days >= 567 -> "🔱"
            days >= 472 -> "🌟"
            days >= 393 -> "✨"
            days >= 327 -> "💯"
            days >= 272 -> "🔥"
            days >= 226 -> "🎖️"
            days >= 188 -> "🌠"
            days >= 156 -> "⭐"
            days >= 130 -> "🚀"
            days >= 108 -> "🎯"
            days >= 90 -> "🏅"
            days >= 75 -> "🪙"
            days >= 62 -> "💪"
            days >= 51 -> "🙌"
            days >= 42 -> "👏"
            days >= 35 -> "👌"
            days >= 29 -> "🎉"
            days >= 24 -> "🌱"
            days >= 20 -> "👍"
            days >= 16 -> "😎"
            days >= 13 -> "😄"
            days >= 8 -> "😊"
            days >= 5 -> "🙂"
            days >= 3 -> "😃"
            days >= 2 -> "👋"
            days >= 1 -> "🤞"
            else -> "👶"
        }
    }
} 