package nl.berrygrove.sft.utils

import android.graphics.Color
import androidx.core.graphics.ColorUtils

/**
 * Utility class for color transformations and interpolations
 */
object ColorUtils {
    
    /**
     * Generate a color between the start and end colors based on the progress (0-100)
     * 
     * @param startColor The color when progress is high (starting color)
     * @param endColor The color when progress approaches 0 (ending color)
     * @param progress Current progress value (0-100)
     * @return Interpolated color between start and end colors
     */
    fun interpolateColor(startColor: Int, endColor: Int, progress: Int): Int {
        // Convert progress (0-100) to a fraction (1.0-0.0)
        // Note: We invert the progress so it goes from startColor (100%) to endColor (0%)
        val fraction = 1 - (progress / 100f).coerceIn(0f, 1f)
        
        // Use AndroidX ColorUtils to blend the colors
        return ColorUtils.blendARGB(startColor, endColor, fraction)
    }
} 