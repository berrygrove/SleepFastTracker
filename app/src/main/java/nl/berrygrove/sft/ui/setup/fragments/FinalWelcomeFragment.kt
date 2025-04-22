package nl.berrygrove.sft.ui.setup.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import nl.berrygrove.sft.R
import nl.berrygrove.sft.ui.setup.SetupNavigationListener
import nl.berrygrove.sft.ui.setup.SetupViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Final welcome screen - fifth step in the setup wizard.
 * Shows the user a summary and their current fasting status.
 */
class FinalWelcomeFragment : Fragment() {

    private lateinit var navigationListener: SetupNavigationListener
    private lateinit var viewModel: SetupViewModel
    private lateinit var statusTextView: TextView
    
    override fun onAttach(context: Context) {
        super.onAttach(context)
        navigationListener = context as SetupNavigationListener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity()).get(SetupViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_final_welcome, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        statusTextView = view.findViewById(R.id.tv_current_status)
        
        // Show current fasting status
        updateFastingStatus()
        
        view.findViewById<Button>(R.id.btn_finish).setOnClickListener {
            // Create first fasting entry before finishing setup
            createFirstFastingEntry()
            
            // Weight record is already created in BodyInfoFragment
            
            // Complete setup
            viewModel.setSetupCompleted()
            navigationListener.finishSetup()
        }
    }
    
    private fun updateFastingStatus() {
        // Get current time
        val currentTime = Calendar.getInstance()
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val currentTimeStr = timeFormat.format(currentTime.time)
        
        // Get eating start time and window from ViewModel
        val eatingStartTime = viewModel.getEatingStartTime()
        val eatingWindowHours = viewModel.getEatingWindowHours()
        
        // Calculate eating end time
        val eatingEndTime = viewModel.calculateEatingEndTime(eatingStartTime, eatingWindowHours)
        
        // Parse times to compare
        val currentTimeParts = currentTimeStr.split(":")
        val currentHour = currentTimeParts[0].toInt()
        val currentMinute = currentTimeParts[1].toInt()
        
        val startTimeParts = eatingStartTime.split(":")
        val startHour = startTimeParts[0].toInt()
        val startMinute = startTimeParts[1].toInt()
        
        val endTimeParts = eatingEndTime.split(":")
        val endHour = endTimeParts[0].toInt()
        val endMinute = endTimeParts[1].toInt()
        
        // Convert all to minutes for easier comparison
        val currentTimeMinutes = currentHour * 60 + currentMinute
        val startTimeMinutes = startHour * 60 + startMinute
        val endTimeMinutes = endHour * 60 + endMinute
        
        // Check if we're in eating window
        val isEating = if (endTimeMinutes < startTimeMinutes) {
            // Eating window spans midnight
            currentTimeMinutes >= startTimeMinutes || currentTimeMinutes <= endTimeMinutes
        } else {
            // Normal eating window
            currentTimeMinutes >= startTimeMinutes && currentTimeMinutes <= endTimeMinutes
        }
        
        // Update status text
        if (isEating) {
            statusTextView.text = getString(R.string.status_eating, eatingEndTime)
        } else {
            statusTextView.text = getString(R.string.status_fasting, eatingStartTime)
        }
    }
    
    private fun createFirstFastingEntry() {
        // Get current time
        val currentTime = Calendar.getInstance()
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val currentTimeStr = timeFormat.format(currentTime.time)
        
        // Get eating start time and window from ViewModel
        val eatingStartTime = viewModel.getEatingStartTime()
        val eatingWindowHours = viewModel.getEatingWindowHours()
        
        // Calculate eating end time
        val eatingEndTime = viewModel.calculateEatingEndTime(eatingStartTime, eatingWindowHours)
        
        // Parse times to compare
        val currentTimeParts = currentTimeStr.split(":")
        val currentHour = currentTimeParts[0].toInt()
        val currentMinute = currentTimeParts[1].toInt()
        
        val startTimeParts = eatingStartTime.split(":")
        val startHour = startTimeParts[0].toInt()
        val startMinute = startTimeParts[1].toInt()
        
        val endTimeParts = eatingEndTime.split(":")
        val endHour = endTimeParts[0].toInt()
        val endMinute = endTimeParts[1].toInt()
        
        // Convert all to minutes for easier comparison
        val currentTimeMinutes = currentHour * 60 + currentMinute
        val startTimeMinutes = startHour * 60 + startMinute
        val endTimeMinutes = endHour * 60 + endMinute
        
        // Check if we're in eating window
        val isEating = if (endTimeMinutes < startTimeMinutes) {
            // Eating window spans midnight
            currentTimeMinutes >= startTimeMinutes || currentTimeMinutes <= endTimeMinutes
        } else {
            // Normal eating window
            currentTimeMinutes >= startTimeMinutes && currentTimeMinutes <= endTimeMinutes
        }
        
        // Calculate timestamp when current state started
        val timestamp = Calendar.getInstance()
        
        if (isEating) {
            // If we're eating, eating started at the eating start time
            // Set timestamp to today's eating start time
            timestamp.set(Calendar.HOUR_OF_DAY, startHour)
            timestamp.set(Calendar.MINUTE, startMinute)
            timestamp.set(Calendar.SECOND, 0)
            timestamp.set(Calendar.MILLISECOND, 0)
            
            // If current time is before start time, we must have crossed midnight, so eating started yesterday
            if (currentTimeMinutes < startTimeMinutes && endTimeMinutes < startTimeMinutes) {
                timestamp.add(Calendar.DAY_OF_YEAR, -1)
            }
        } else {
            // If we're fasting, calculate when fasting started
            // For fasting, we need to set timestamp to (eating start time tomorrow - (24 - eating window))
            
            // First, get tomorrow's date and set eating start time
            timestamp.add(Calendar.DAY_OF_YEAR, 1)
            timestamp.set(Calendar.HOUR_OF_DAY, startHour)
            timestamp.set(Calendar.MINUTE, startMinute)
            timestamp.set(Calendar.SECOND, 0)
            timestamp.set(Calendar.MILLISECOND, 0)
            
            // Then subtract (24 - eating window) hours
            timestamp.add(Calendar.HOUR_OF_DAY, -(24 - eatingWindowHours))
            
            // If we subtracted and went back to today, then we need to check if that time has passed
            // If it has, then our fasting started at the end of the eating window today
            if (timestamp.timeInMillis > currentTime.timeInMillis) {
                // The calculated time is in the future, so fasting hasn't started according to that calculation
                // Instead, fasting started at the end of the last eating window
                timestamp.setTimeInMillis(currentTime.timeInMillis)
                timestamp.set(Calendar.HOUR_OF_DAY, endHour)
                timestamp.set(Calendar.MINUTE, endMinute)
                timestamp.set(Calendar.SECOND, 0)
                timestamp.set(Calendar.MILLISECOND, 0)
                
                // If current time is before end time, we went back a day
                if (currentTimeMinutes < endTimeMinutes) {
                    timestamp.add(Calendar.DAY_OF_YEAR, -1)
                }
            }
        }
        
        // Create and save the fasting entry with the correct state
        // IMPORTANT: For FastingRecord, state=true means fasting, state=false means eating
        viewModel.createFirstFastingEntry(!isEating, timestamp.timeInMillis)
    }
} 