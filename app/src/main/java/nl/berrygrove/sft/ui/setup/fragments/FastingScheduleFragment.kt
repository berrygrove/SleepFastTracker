package nl.berrygrove.sft.ui.setup.fragments

import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import nl.berrygrove.sft.R
import nl.berrygrove.sft.ui.setup.SetupNavigationListener
import nl.berrygrove.sft.ui.setup.SetupViewModel
import java.util.*

/**
 * Fasting schedule screen - fifth step in the setup wizard.
 */
class FastingScheduleFragment : Fragment() {

    private lateinit var navigationListener: SetupNavigationListener
    private lateinit var viewModel: SetupViewModel
    private lateinit var eatingStartsEditText: EditText
    private lateinit var eatingWindowSeekBar: SeekBar
    private lateinit var eatingEndTextView: TextView
    private lateinit var fastingRatioTextView: TextView
    private lateinit var selectedHoursTextView: TextView
    
    private var eatingStartTime = "11:00"
    private var eatingWindowHours = 4

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
        return inflater.inflate(R.layout.fragment_fasting_schedule, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        eatingStartsEditText = view.findViewById(R.id.et_eating_start_time)
        eatingWindowSeekBar = view.findViewById(R.id.seek_bar_eating_window)
        eatingEndTextView = view.findViewById(R.id.tv_eating_end_time)
        fastingRatioTextView = view.findViewById(R.id.tv_fasting_ratio)
        selectedHoursTextView = view.findViewById(R.id.tv_selected_hours)
        
        eatingStartsEditText.setText(eatingStartTime)
        eatingWindowSeekBar.progress = eatingWindowHours - 4 // SeekBar starts at value 4
        
        // Initialize the hours text view
        selectedHoursTextView.text = "$eatingWindowHours hours"
        
        setupTimePickerDialog()
        setupSeekBar()
        updateDisplayedValues()
        
        view.findViewById<Button>(R.id.btn_finish).setOnClickListener {
            saveData()
            navigationListener.navigateToStep(6)
        }
    }
    
    private fun setupTimePickerDialog() {
        eatingStartsEditText.setOnClickListener {
            // Parse current time
            val timeParts = eatingStartTime.split(":")
            val hour = timeParts[0].toInt()
            val minute = timeParts[1].toInt()
            
            val timePickerDialog = TimePickerDialog(
                context,
                { _, selectedHour, selectedMinute ->
                    eatingStartTime = String.format("%02d:%02d", selectedHour, selectedMinute)
                    eatingStartsEditText.setText(eatingStartTime)
                    updateDisplayedValues()
                },
                hour,
                minute,
                true // 24-hour format
            )
            
            timePickerDialog.show()
        }
    }
    
    private fun setupSeekBar() {
        eatingWindowSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                eatingWindowHours = progress + 4 // Min value is 4 hours
                updateDisplayedValues()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
    
    private fun updateDisplayedValues() {
        val eatingEndTime = viewModel.calculateEatingEndTime(eatingStartTime, eatingWindowHours)
        eatingEndTextView.text = "Eating ends at $eatingEndTime"
        
        val fastingHours = viewModel.calculateFastingHours(eatingWindowHours)
        fastingRatioTextView.text = "$fastingHours hours fasting / $eatingWindowHours hours eating"
        
        selectedHoursTextView.text = "$eatingWindowHours hours"
    }

    private fun saveData() {
        viewModel.saveFastingSchedule(eatingStartTime, eatingWindowHours)
    }
} 