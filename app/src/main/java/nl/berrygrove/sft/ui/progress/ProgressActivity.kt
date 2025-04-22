package nl.berrygrove.sft.ui.progress

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import nl.berrygrove.sft.R
import nl.berrygrove.sft.SleepFastTrackerApplication
import nl.berrygrove.sft.databinding.ActivityProgressBinding
import nl.berrygrove.sft.ui.progress.ProgressViewModel.SleepTrend
import java.time.format.DateTimeFormatter
import kotlin.math.abs

class ProgressActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityProgressBinding
    private lateinit var viewModel: ProgressViewModel
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProgressBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Setup toolbar with back button
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_progress)
        
        setupViewModel()
        setupCharts()
        observeData()
    }
    
    private fun setupViewModel() {
        val application = application as SleepFastTrackerApplication
        val weightRepository = application.weightRecordRepository
        val fastingRepository = application.fastingRepository
        val sleepRepository = application.sleepRepository
        val userSettingsRepository = application.userSettingsRepository
        
        val factory = ProgressViewModelFactory(
            weightRepository,
            fastingRepository,
            sleepRepository,
            userSettingsRepository
        )
        
        viewModel = ViewModelProvider(this, factory)[ProgressViewModel::class.java]
    }
    
    private fun setupCharts() {
        // Setup weight chart
        binding.weightChart.apply {
            description.isEnabled = false
            legend.isEnabled = true
            setTouchEnabled(true)
            setScaleEnabled(true)
            setPinchZoom(true)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            axisRight.isEnabled = false
            
            // Display a message when no data is available
            setNoDataText("Loading chart data...")
            setNoDataTextColor(ContextCompat.getColor(this@ProgressActivity, R.color.primary))
        }
        
        // Setup BMI chart
        binding.bmiChart.apply {
            description.isEnabled = false
            legend.isEnabled = true
            setTouchEnabled(true)
            setScaleEnabled(true)
            setPinchZoom(true)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            axisRight.isEnabled = false
            
            // Display a message when no data is available
            setNoDataText("Loading chart data...")
            setNoDataTextColor(ContextCompat.getColor(this@ProgressActivity, R.color.primary))
        }
        
        // Start with empty data to initialize the charts
        setupEmptyCharts()
    }
    
    /**
     * Set up empty initial charts to ensure proper rendering
     */
    private fun setupEmptyCharts() {
        // Create dummy entry for weight chart
        val emptyWeightEntries = listOf(Entry(0f, 0f))
        val emptyWeightDataSet = LineDataSet(emptyWeightEntries, "Weight (kg)").apply {
            color = ContextCompat.getColor(this@ProgressActivity, R.color.primary)
            setDrawValues(false)
            setDrawCircles(false)
        }
        
        val emptyWeightData = LineData(emptyWeightDataSet)
        binding.weightChart.data = emptyWeightData
        binding.weightChart.invalidate()
        
        // Create dummy entry for BMI chart
        val emptyBmiEntries = listOf(Entry(0f, 0f))
        val emptyBmiDataSet = LineDataSet(emptyBmiEntries, "BMI").apply {
            color = ContextCompat.getColor(this@ProgressActivity, R.color.primary)
            setDrawValues(false)
            setDrawCircles(false)
        }
        
        val emptyBmiData = LineData(emptyBmiDataSet)
        binding.bmiChart.data = emptyBmiData
        binding.bmiChart.invalidate()
    }
    
    private fun observeData() {
        // Observe weight progress
        viewModel.weightDifference.observe(this) { difference ->
            binding.weightDifferenceText.text = 
                if (difference < 0) {
                    getString(R.string.weight_progress_loss, Math.abs(difference))
                } else if (difference > 0) {
                    getString(R.string.weight_progress_gain, difference)
                } else {
                    getString(R.string.weight_progress_same)
                }
        }
        
        viewModel.totalWeightLoss.observe(this) { totalChange ->
            binding.totalWeightLossText.text = 
                if (viewModel.weightDifference.value ?: 0.0 < 0) {
                    getString(R.string.total_weight_loss, totalChange)
                } else {
                    getString(R.string.total_weight_gain, totalChange)
                }
        }
        
        viewModel.weightProgress.observe(this) { progress ->
            binding.weightProgressBar.progress = progress
        }
        
        // Observe weight records for the chart
        viewModel.weightRecords.observe(this) { records ->
            if (records.isNotEmpty()) {
                updateWeightChart(records)
            } else {
                // Show explicit "no data" message if records are empty
                binding.weightChart.clear()
                binding.weightChart.setNoDataText("No weight data available")
                binding.weightChart.invalidate()
            }
        }
        
        // Observe BMI records for the chart
        viewModel.bmiRecords.observe(this) { records ->
            if (records.isNotEmpty()) {
                updateBmiChart(records)
            } else {
                // Show explicit "no data" message if records are empty
                binding.bmiChart.clear()
                binding.bmiChart.setNoDataText("No BMI data available")
                binding.bmiChart.invalidate()
            }
        }
        
        // Observe fasting progress
        viewModel.fastingCompletion.observe(this) { completion ->
            binding.fastingCompletionText.text = 
                getString(R.string.fasting_completion_percentage, completion)
        }
        
        viewModel.fastingProgress.observe(this) { progress ->
            binding.fastingProgressBar.progress = progress
        }
        
        // Observe sleep progress
        viewModel.sleepQualityImprovement.observe(this) { improvement ->
            binding.sleepQualityText.text = 
                if (improvement > 0) {
                    getString(R.string.sleep_quality_improved, improvement)
                } else if (improvement < 0) {
                    getString(R.string.sleep_quality_decreased, Math.abs(improvement))
                } else {
                    getString(R.string.sleep_quality_same)
                }
        }
        
        viewModel.sleepProgress.observe(this) { progress ->
            binding.sleepProgressBar.progress = progress
        }
        
        // Observe new bedtime adherence metrics
        viewModel.bedtimeAdherencePercentage.observe(this) { percentage ->
            binding.bedtimeAdherenceText.text = 
                getString(R.string.bedtime_adherence_percentage, percentage)
        }
        
        viewModel.sleepTrend.observe(this) { trend ->
            when (trend) {
                SleepTrend.IMPROVING -> {
                    binding.sleepTrendText.text = getString(R.string.trend_improving)
                    binding.sleepTrendText.setTextColor(getColor(R.color.success))
                    binding.sleepTrendText.setCompoundDrawablesWithIntrinsicBounds(
                        R.drawable.ic_trend_up, 0, 0, 0
                    )
                }
                SleepTrend.DECLINING -> {
                    binding.sleepTrendText.text = getString(R.string.trend_declining)
                    binding.sleepTrendText.setTextColor(getColor(R.color.error))
                    binding.sleepTrendText.setCompoundDrawablesWithIntrinsicBounds(
                        R.drawable.ic_trend_down, 0, 0, 0
                    )
                }
                SleepTrend.STABLE -> {
                    binding.sleepTrendText.text = getString(R.string.trend_stable)
                    binding.sleepTrendText.setTextColor(getColor(R.color.warning))
                    binding.sleepTrendText.setCompoundDrawablesWithIntrinsicBounds(
                        R.drawable.ic_trend_stable, 0, 0, 0
                    )
                }
                null -> {
                    binding.sleepTrendText.text = getString(R.string.trend_stable)
                    binding.sleepTrendText.setTextColor(getColor(R.color.warning))
                    binding.sleepTrendText.setCompoundDrawablesWithIntrinsicBounds(
                        R.drawable.ic_trend_stable, 0, 0, 0
                    )
                }
            }
        }
        
        // Observe bedtime warning state
        viewModel.shouldShowBedtimeWarning.observe(this) { shouldShow ->
            binding.bedtimeWarningText.visibility = if (shouldShow) View.VISIBLE else View.GONE
        }
        
        // Observe if we have enough sleep data (7+ days) and update UI accordingly
        viewModel.hasEnoughSleepData.observe(this) { hasEnoughData ->
            if (hasEnoughData) {
                // Show all the sleep metrics
                binding.sleepQualityText.visibility = View.VISIBLE
                binding.bedtimeAdherenceText.visibility = View.VISIBLE
                binding.sleepTrendContainer.visibility = View.VISIBLE
                binding.sleepProgressContainer.visibility = View.VISIBLE
                binding.notEnoughSleepDataText.visibility = View.GONE
            } else {
                // Show a placeholder message and hide metrics
                binding.sleepQualityText.visibility = View.GONE
                binding.bedtimeAdherenceText.visibility = View.GONE
                binding.sleepTrendContainer.visibility = View.GONE
                binding.sleepProgressContainer.visibility = View.GONE
                
                // We need to add this TextView to the layout 
                binding.notEnoughSleepDataText.visibility = View.VISIBLE
                binding.notEnoughSleepDataText.text = getString(R.string.not_enough_sleep_data)
            }
        }
    }
    
    private fun updateWeightChart(weightRecords: List<nl.berrygrove.sft.data.model.WeightRecord>) {
        val entries = weightRecords.mapIndexed { index, record ->
            Entry(index.toFloat(), record.weight)
        }
        
        val dataSet = LineDataSet(entries, "Weight (kg)").apply {
            color = ContextCompat.getColor(this@ProgressActivity, R.color.primary)
            setCircleColor(ContextCompat.getColor(this@ProgressActivity, R.color.primary))
            lineWidth = 2f
            circleRadius = 3f
            setDrawCircleHole(false)
            valueTextSize = 9f
            setDrawValues(true)
        }
        
        val formatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return value.toString() + " kg"
            }
        }
        dataSet.valueFormatter = formatter
        
        val lineData = LineData(dataSet)
        binding.weightChart.data = lineData
        
        // X-axis formatter to show dates
        val dateFormatter = object : ValueFormatter() {
            private val dateFormat = DateTimeFormatter.ofPattern("MM/dd")
            
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                return if (index >= 0 && index < weightRecords.size) {
                    weightRecords[index].timestamp.format(dateFormat)
                } else {
                    ""
                }
            }
        }
        
        binding.weightChart.xAxis.valueFormatter = dateFormatter
        binding.weightChart.invalidate()
    }
    
    private fun updateBmiChart(bmiRecords: List<Pair<java.time.LocalDateTime, Float>>) {
        val entries = bmiRecords.mapIndexed { index, (_, bmi) ->
            Entry(index.toFloat(), bmi)
        }
        
        val dataSet = LineDataSet(entries, "BMI").apply {
            color = ContextCompat.getColor(this@ProgressActivity, R.color.primary)
            setCircleColor(ContextCompat.getColor(this@ProgressActivity, R.color.primary))
            lineWidth = 2f
            circleRadius = 3f
            setDrawCircleHole(false)
            valueTextSize = 9f
            setDrawValues(true)
            
            // Set fill for the dataset instead of the axis
            setDrawFilled(true)
            fillColor = ContextCompat.getColor(this@ProgressActivity, R.color.success_light)
        }
        
        val lineData = LineData(dataSet)
        binding.bmiChart.data = lineData
        
        // Add limit lines for BMI categories
        val underweightLine = LimitLine(ProgressViewModel.BMI_UNDERWEIGHT, "Underweight").apply {
            lineWidth = 1f
            lineColor = Color.RED
            enableDashedLine(10f, 10f, 0f)
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
            textSize = 8f
        }
        
        val normalLine = LimitLine(ProgressViewModel.BMI_NORMAL, "Overweight").apply {
            lineWidth = 1f
            lineColor = Color.YELLOW
            enableDashedLine(10f, 10f, 0f)
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
            textSize = 8f
        }
        
        val obeseLine = LimitLine(ProgressViewModel.BMI_OVERWEIGHT, "Obese").apply {
            lineWidth = 1f
            lineColor = Color.RED
            enableDashedLine(10f, 10f, 0f)
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
            textSize = 8f
        }
        
        // Add limit lines to left axis
        binding.bmiChart.axisLeft.apply {
            removeAllLimitLines()
            addLimitLine(underweightLine)
            addLimitLine(normalLine)
            addLimitLine(obeseLine)
        }
        
        // X-axis formatter to show dates
        val dateFormatter = object : ValueFormatter() {
            private val dateFormat = DateTimeFormatter.ofPattern("MM/dd")
            
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                return if (index >= 0 && index < bmiRecords.size) {
                    bmiRecords[index].first.format(dateFormat)
                } else {
                    ""
                }
            }
        }
        
        binding.bmiChart.xAxis.valueFormatter = dateFormatter
        binding.bmiChart.invalidate()
    }
    
    // Force refresh data when activity resumes
    override fun onResume() {
        super.onResume()
        viewModel.loadData()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
} 