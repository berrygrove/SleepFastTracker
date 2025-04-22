package nl.berrygrove.sft.ui.checkin

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import nl.berrygrove.sft.R
import nl.berrygrove.sft.SleepFastTrackerApplication
import nl.berrygrove.sft.data.model.WeightRecord
import nl.berrygrove.sft.databinding.ActivityCheckinBinding
import java.time.format.DateTimeFormatter

class CheckInActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityCheckinBinding
    private lateinit var viewModel: CheckInViewModel
    private lateinit var adapter: WeightRecordAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCheckinBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Setup toolbar with back button
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_check_in)
        
        // Setup ViewModel
        val application = application as SleepFastTrackerApplication
        val weightRecordRepository = application.weightRecordRepository
        
        val factory = CheckInViewModelFactory(weightRecordRepository, application.achievementRepository)
        viewModel = ViewModelProvider(this, factory)[CheckInViewModel::class.java]
        
        // Setup RecyclerView
        setupRecyclerView()
        
        // Setup UI elements
        setupUI()
        
        // Observe data
        observeData()
    }
    
    private fun setupRecyclerView() {
        adapter = WeightRecordAdapter(
            this,
            onEditClick = { record -> 
                android.util.Log.d("CheckInActivity", "Edit button clicked for record: ${record.id}")
                SleepFastTrackerApplication.addDebugLog("CheckInActivity", "Edit button clicked for record: ${record.id}")
                viewModel.startEditingRecord(record)
                showEditDialog(record) 
            },
            onDeleteClick = { record -> confirmDeleteRecord(record) }
        )
        
        binding.recyclerViewWeightRecords.apply {
            layoutManager = LinearLayoutManager(this@CheckInActivity)
            adapter = this@CheckInActivity.adapter
        }
    }
    
    private fun setupUI() {
        // Save button click listener
        binding.buttonSave.setOnClickListener {
            val weightText = binding.editTextWeight.text.toString()
            if (weightText.isNotEmpty()) {
                try {
                    val weight = weightText.toFloat()
                    viewModel.saveWeightRecord(weight)
                    binding.editTextWeight.text?.clear()
                } catch (e: NumberFormatException) {
                    Toast.makeText(this, "Please enter a valid weight", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Please enter a weight", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Toggle edit mode button
        binding.toggleEditMode.setOnClickListener {
            val currentEditMode = viewModel.isInEditMode.value ?: false
            viewModel.toggleEditMode()
            binding.toggleEditMode.text = if (!currentEditMode) getString(R.string.edit_mode) else getString(R.string.edit)
        }
        
        // Cancel edit button
        binding.buttonCancelEdit.setOnClickListener {
            hideEditDialog()
        }
        
        // Confirm edit button
        binding.buttonConfirmEdit.setOnClickListener {
            val weightText = binding.editTextEditWeight.text.toString()
            android.util.Log.d("CheckInActivity", "Update button clicked. Input weight: $weightText")
            SleepFastTrackerApplication.addDebugLog("CheckInActivity", "Update button clicked. Input weight: $weightText")
            
            if (weightText.isNotEmpty()) {
                try {
                    val weight = weightText.toFloat()
                    val record = viewModel.currentEditRecord.value
                    android.util.Log.d("CheckInActivity", "Parsed weight: $weight, Current edit record: ${record?.id}, original weight: ${record?.weight}")
                    SleepFastTrackerApplication.addDebugLog("CheckInActivity", "Parsed weight: $weight, Current edit record: ${record?.id}, original weight: ${record?.weight}")
                    
                    if (record != null) {
                        // Hide dialog immediately to improve perceived responsiveness
                        binding.dimBackground.visibility = View.GONE
                        binding.editRecordDialog.visibility = View.GONE
                        android.util.Log.d("CheckInActivity", "Dialog hidden, calling updateWeightRecord with id: ${record.id}, new weight: $weight")
                        SleepFastTrackerApplication.addDebugLog("CheckInActivity", "Dialog hidden, calling updateWeightRecord with id: ${record.id}, new weight: $weight")
                        
                        // Update the record in the database
                        viewModel.updateWeightRecord(record.id, weight)
                    } else {
                        android.util.Log.e("CheckInActivity", "ERROR: Current edit record is null")
                        SleepFastTrackerApplication.addDebugLog("CheckInActivity", "ERROR: Current edit record is null")
                    }
                } catch (e: NumberFormatException) {
                    android.util.Log.e("CheckInActivity", "Error parsing weight", e)
                    SleepFastTrackerApplication.addDebugLog("CheckInActivity", "Error parsing weight: ${e.message}")
                    Toast.makeText(this, "Please enter a valid weight", Toast.LENGTH_SHORT).show()
                }
            } else {
                android.util.Log.e("CheckInActivity", "Weight text is empty")
                SleepFastTrackerApplication.addDebugLog("CheckInActivity", "Weight text is empty")
                Toast.makeText(this, "Please enter a weight", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Clicking on dim background cancels edit
        binding.dimBackground.setOnClickListener {
            hideEditDialog()
        }
    }
    
    private fun observeData() {
        // Observe weight records
        viewModel.weightRecords.observe(this) { records ->
            if (records.isEmpty()) {
                binding.textViewNoRecords.visibility = View.VISIBLE
                binding.recyclerViewWeightRecords.visibility = View.GONE
            } else {
                binding.textViewNoRecords.visibility = View.GONE
                binding.recyclerViewWeightRecords.visibility = View.VISIBLE
                adapter.updateData(records, viewModel.weightDeltas.value ?: emptyMap())
            }
        }
        
        // Observe weight deltas
        viewModel.weightDeltas.observe(this) { deltas ->
            adapter.updateData(viewModel.weightRecords.value ?: emptyList(), deltas)
        }
        
        // Observe edit mode
        viewModel.isInEditMode.observe(this) { isEditMode ->
            adapter.setEditMode(isEditMode)
            binding.toggleEditMode.text = if (isEditMode) getString(R.string.edit_mode) else getString(R.string.edit)
        }
        
        // Observe current edit record - but don't auto-show dialog to prevent infinite recursion
        // The dialog will be shown manually when user clicks edit button
        viewModel.currentEditRecord.observe(this) { record ->
            // We no longer auto-show dialog here to prevent recursion
            // Dialog is shown directly in adapter click handler
        }
    }
    
    private fun showEditDialog(record: WeightRecord) {
        // Populate the edit dialog
        binding.editTextEditWeight.setText(record.weight.toString())
        binding.textViewEditDialogTimestamp.text = getString(
            R.string.recorded_on,
            record.timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        )
        
        // Show the dialog
        binding.dimBackground.visibility = View.VISIBLE
        binding.editRecordDialog.visibility = View.VISIBLE
    }
    
    private fun hideEditDialog() {
        binding.dimBackground.visibility = View.GONE
        binding.editRecordDialog.visibility = View.GONE
        viewModel.cancelEdit()
    }
    
    private fun confirmDeleteRecord(record: WeightRecord) {
        AlertDialog.Builder(this)
            .setTitle("Delete Record")
            .setMessage("Are you sure you want to delete this weight record?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteWeightRecord(record)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
} 