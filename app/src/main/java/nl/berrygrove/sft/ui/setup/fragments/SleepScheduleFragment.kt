package nl.berrygrove.sft.ui.setup.fragments

import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import nl.berrygrove.sft.R
import nl.berrygrove.sft.ui.setup.SetupNavigationListener
import nl.berrygrove.sft.ui.setup.SetupViewModel
import java.util.*

/**
 * Sleep schedule screen - fourth step in the setup wizard.
 */
class SleepScheduleFragment : Fragment() {

    private lateinit var navigationListener: SetupNavigationListener
    private lateinit var viewModel: SetupViewModel
    private lateinit var wakeUpTimeEditText: EditText
    private lateinit var recommendedBedtimeTextView: TextView
    private lateinit var bedtimeEditText: EditText
    
    private var wakeUpTime = "07:00"
    private var bedTime = "22:30"

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
        return inflater.inflate(R.layout.fragment_sleep_schedule, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        wakeUpTimeEditText = view.findViewById(R.id.et_wake_up_time)
        recommendedBedtimeTextView = view.findViewById(R.id.tv_recommended_bedtime)
        bedtimeEditText = view.findViewById(R.id.et_bedtime)
        
        wakeUpTimeEditText.setText(wakeUpTime)
        updateRecommendedBedtime()
        bedtimeEditText.setText(bedTime)
        
        setupTimePickerDialogs()
        
        view.findViewById<Button>(R.id.btn_next).setOnClickListener {
            saveData()
            navigationListener.navigateToStep(5)
        }
    }
    
    private fun setupTimePickerDialogs() {
        // Wake up time picker
        wakeUpTimeEditText.setOnClickListener {
            // Parse current time
            val timeParts = wakeUpTime.split(":")
            val hour = timeParts[0].toInt()
            val minute = timeParts[1].toInt()
            
            val timePickerDialog = TimePickerDialog(
                context,
                { _, selectedHour, selectedMinute ->
                    wakeUpTime = String.format("%02d:%02d", selectedHour, selectedMinute)
                    wakeUpTimeEditText.setText(wakeUpTime)
                    updateRecommendedBedtime()
                },
                hour,
                minute,
                true // 24-hour format
            )
            
            timePickerDialog.show()
        }
        
        // Bedtime picker
        bedtimeEditText.setOnClickListener {
            // Parse current time
            val timeParts = bedTime.split(":")
            val hour = timeParts[0].toInt()
            val minute = timeParts[1].toInt()
            
            val timePickerDialog = TimePickerDialog(
                context,
                { _, selectedHour, selectedMinute ->
                    bedTime = String.format("%02d:%02d", selectedHour, selectedMinute)
                    bedtimeEditText.setText(bedTime)
                },
                hour,
                minute,
                true // 24-hour format
            )
            
            timePickerDialog.show()
        }
    }
    
    private fun updateRecommendedBedtime() {
        val recommendedBedtime = viewModel.calculateRecommendedBedtime(wakeUpTime)
        recommendedBedtimeTextView.text = recommendedBedtime
        
        // Update bedtime field with recommended time if user hasn't already set a custom time
        if (bedTime == "22:30") { // Only update if it's still the default value
            bedTime = recommendedBedtime
            bedtimeEditText.setText(bedTime)
        }
    }

    private fun saveData() {
        viewModel.saveSleepSchedule(wakeUpTime, bedTime)
    }
}