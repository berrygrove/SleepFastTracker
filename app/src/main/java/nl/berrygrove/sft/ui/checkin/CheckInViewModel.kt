package nl.berrygrove.sft.ui.checkin

import androidx.lifecycle.*
import kotlinx.coroutines.launch
import nl.berrygrove.sft.SleepFastTrackerApplication
import nl.berrygrove.sft.data.model.WeightRecord
import nl.berrygrove.sft.repository.AchievementRepository
import nl.berrygrove.sft.repository.WeightRecordRepository
import java.time.LocalDateTime
import kotlin.math.max

class CheckInViewModel(
    private val weightRecordRepository: WeightRecordRepository,
    private val achievementRepository: AchievementRepository
) : ViewModel() {

    // LiveData for the last 10 weight records
    private val _weightRecords = MutableLiveData<List<WeightRecord>>()
    val weightRecords: LiveData<List<WeightRecord>> = _weightRecords

    // LiveData for weight delta calculations
    private val _weightDeltas = MutableLiveData<Map<Long, Float>>()
    val weightDeltas: LiveData<Map<Long, Float>> = _weightDeltas
    
    // LiveData for record that is being edited
    private val _currentEditRecord = MutableLiveData<WeightRecord?>()
    val currentEditRecord: LiveData<WeightRecord?> = _currentEditRecord

    // EditMode state
    private val _isInEditMode = MutableLiveData<Boolean>(false)
    val isInEditMode: LiveData<Boolean> = _isInEditMode

    // Observer reference to clean up properly
    private var weightRecordsObserver: Observer<List<WeightRecord>>? = null

    init {
        loadRecentWeightRecords()
    }

    fun loadRecentWeightRecords() {
        // Clean up any existing observer to prevent memory leaks
        if (weightRecordsObserver != null) {
            val recentRecordsLiveData = weightRecordRepository.getRecentWeightRecords(10)
            recentRecordsLiveData.removeObserver(weightRecordsObserver!!)
            weightRecordsObserver = null
        }
        
        // Create and register a new observer
        val recentRecordsLiveData = weightRecordRepository.getRecentWeightRecords(10)
        weightRecordsObserver = Observer { records ->
            _weightRecords.value = records
            calculateWeightDeltas(records)
        }
        recentRecordsLiveData.observeForever(weightRecordsObserver!!)
    }

    private fun calculateWeightDeltas(records: List<WeightRecord>) {
        val deltas = mutableMapOf<Long, Float>()
        
        // Start from the second-newest record and compare to previous (newer) ones
        for (i in 1 until records.size) {
            val current = records[i]      // Older record
            val previous = records[i-1]   // Newer record
            
            // Calculate the delta (negative means weight loss over time)
            // This shows how weight changed from this record to the newer one
            val delta = previous.weight - current.weight
            deltas[current.id] = delta
        }
        
        _weightDeltas.value = deltas
    }

    fun saveWeightRecord(weight: Float) {
        viewModelScope.launch {
            val weightRecord = WeightRecord(
                weight = weight,
                timestamp = LocalDateTime.now()
            )
            weightRecordRepository.insert(weightRecord)
            loadRecentWeightRecords()
            
            // Check for achievements after adding a weight record
            checkWeightAchievements()
        }
    }
    
    fun toggleEditMode() {
        _isInEditMode.value = !(_isInEditMode.value ?: false)
        _currentEditRecord.value = null // Clear any record being edited
    }
    
    fun startEditingRecord(record: WeightRecord) {
        _currentEditRecord.value = record
    }
    
    fun cancelEdit() {
        _currentEditRecord.value = null
    }
    
    fun updateWeightRecord(id: Long, newWeight: Float) {
        android.util.Log.d("CheckInViewModel", "updateWeightRecord called with id: $id, newWeight: $newWeight")
        SleepFastTrackerApplication.addDebugLog("CheckInViewModel", "updateWeightRecord called with id: $id, newWeight: $newWeight")
        
        viewModelScope.launch {
            val record = _currentEditRecord.value
            android.util.Log.d("CheckInViewModel", "Current edit record: $record")
            SleepFastTrackerApplication.addDebugLog("CheckInViewModel", "Current edit record: $record")
            
            if (record != null && record.id == id) {
                try {
                    // Keep the original timestamp when updating weight
                    val updatedRecord = WeightRecord(
                        id = record.id,
                        weight = newWeight,
                        timestamp = record.timestamp
                    )
                    android.util.Log.d("CheckInViewModel", "Created updated record: $updatedRecord")
                    SleepFastTrackerApplication.addDebugLog("CheckInViewModel", "Created updated record: $updatedRecord")
                    
                    // Update the record in the database
                    android.util.Log.d("CheckInViewModel", "Calling repository.update()")
                    SleepFastTrackerApplication.addDebugLog("CheckInViewModel", "Calling repository.update()")
                    
                    // CRITICAL FIX: Directly call DAO rather than repository for immediate update
                    val weightRecordDao = (weightRecordRepository as? nl.berrygrove.sft.repository.WeightRecordRepository)?.weightRecordDao
                    if (weightRecordDao != null) {
                        SleepFastTrackerApplication.addDebugLog("CheckInViewModel", "Using direct DAO access for update")
                        weightRecordDao.update(updatedRecord)
                    } else {
                        SleepFastTrackerApplication.addDebugLog("CheckInViewModel", "Using repository for update")
                        weightRecordRepository.update(updatedRecord)
                    }
                    
                    android.util.Log.d("CheckInViewModel", "Repository update completed")
                    SleepFastTrackerApplication.addDebugLog("CheckInViewModel", "Repository update completed")
                    
                    // Clear edit state
                    _currentEditRecord.value = null
                    android.util.Log.d("CheckInViewModel", "Cleared current edit record")
                    SleepFastTrackerApplication.addDebugLog("CheckInViewModel", "Cleared current edit record")
                    
                    // Force immediate update of the UI by directly setting the value
                    // This helps ensure the UI updates even if the LiveData doesn't trigger
                    val currentRecords = _weightRecords.value?.toMutableList() ?: mutableListOf()
                    android.util.Log.d("CheckInViewModel", "Current records size: ${currentRecords.size}")
                    SleepFastTrackerApplication.addDebugLog("CheckInViewModel", "Current records size: ${currentRecords.size}")
                    
                    val index = currentRecords.indexOfFirst { it.id == id }
                    android.util.Log.d("CheckInViewModel", "Index of record to update: $index")
                    SleepFastTrackerApplication.addDebugLog("CheckInViewModel", "Index of record to update: $index")
                    
                    if (index != -1) {
                        // Update the record in the list
                        android.util.Log.d("CheckInViewModel", "Updating record at index $index from ${currentRecords[index]} to $updatedRecord")
                        SleepFastTrackerApplication.addDebugLog("CheckInViewModel", "Updating record at index $index from ${currentRecords[index]} to $updatedRecord")
                        currentRecords[index] = updatedRecord
                        _weightRecords.postValue(currentRecords)
                        android.util.Log.d("CheckInViewModel", "Posted updated records to UI")
                        SleepFastTrackerApplication.addDebugLog("CheckInViewModel", "Posted updated records to UI")
                        calculateWeightDeltas(currentRecords)
                        android.util.Log.d("CheckInViewModel", "Calculated new weight deltas")
                        SleepFastTrackerApplication.addDebugLog("CheckInViewModel", "Calculated new weight deltas")
                    } else {
                        android.util.Log.e("CheckInViewModel", "ERROR: Record not found in current list")
                        SleepFastTrackerApplication.addDebugLog("CheckInViewModel", "ERROR: Record not found in current list")
                    }
                    
                    // Also fetch fresh data in case there were other changes
                    android.util.Log.d("CheckInViewModel", "Calling fetchLatestRecords()")
                    SleepFastTrackerApplication.addDebugLog("CheckInViewModel", "Calling fetchLatestRecords()")
                    fetchLatestRecords()
                    
                    // Check for achievements after updating a weight record
                    checkWeightAchievements()
                    
                } catch (e: Exception) {
                    android.util.Log.e("CheckInViewModel", "Error updating weight record", e)
                    SleepFastTrackerApplication.addDebugLog("CheckInViewModel", "Error updating weight record: ${e.message}")
                }
            } else {
                android.util.Log.e("CheckInViewModel", "ERROR: Current edit record is null or ID mismatch. Current: ${record?.id}, Requested: $id")
                SleepFastTrackerApplication.addDebugLog("CheckInViewModel", "ERROR: Current edit record is null or ID mismatch. Current: ${record?.id}, Requested: $id")
            }
        }
    }
    
    // Directly fetch the latest records from the repository
    private suspend fun fetchLatestRecords() {
        try {
            android.util.Log.d("CheckInViewModel", "Inside fetchLatestRecords()")
            SleepFastTrackerApplication.addDebugLog("CheckInViewModel", "Inside fetchLatestRecords()")
            val records = weightRecordRepository.getAllWeightRecords()
            android.util.Log.d("CheckInViewModel", "Fetched ${records.size} records directly from repository")
            SleepFastTrackerApplication.addDebugLog("CheckInViewModel", "Fetched ${records.size} records directly from repository")
            
            val recentRecords = records.sortedByDescending { it.timestamp }.take(10)
            android.util.Log.d("CheckInViewModel", "Sorted and trimmed to ${recentRecords.size} records")
            SleepFastTrackerApplication.addDebugLog("CheckInViewModel", "Sorted and trimmed to ${recentRecords.size} records")
            
            _weightRecords.postValue(recentRecords)
            android.util.Log.d("CheckInViewModel", "Posted fetched records to UI")
            SleepFastTrackerApplication.addDebugLog("CheckInViewModel", "Posted fetched records to UI")
            
            calculateWeightDeltas(recentRecords)
            android.util.Log.d("CheckInViewModel", "Calculated weight deltas for fetched records")
            SleepFastTrackerApplication.addDebugLog("CheckInViewModel", "Calculated weight deltas for fetched records")
        } catch (e: Exception) {
            // Handle any errors
            android.util.Log.e("CheckInViewModel", "Error fetching records", e)
            SleepFastTrackerApplication.addDebugLog("CheckInViewModel", "Error fetching records: ${e.message}")
        }
    }

    fun deleteWeightRecord(record: WeightRecord) {
        viewModelScope.launch {
            weightRecordRepository.delete(record)
            _currentEditRecord.value = null
            loadRecentWeightRecords()
            
            // Check for achievements after deleting a weight record
            checkWeightAchievements()
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up the observer when the ViewModel is destroyed
        if (weightRecordsObserver != null) {
            val recentRecordsLiveData = weightRecordRepository.getRecentWeightRecords(10)
            recentRecordsLiveData.removeObserver(weightRecordsObserver!!)
            weightRecordsObserver = null
        }
    }

    /**
     * Calculate weight loss and check for weight achievements
     */
    private suspend fun checkWeightAchievements() {
        try {
            // Get all weight records
            val weightRecords = weightRecordRepository.getAllWeightRecords()
            
            // Only proceed if we have at least 1 weight record
            if (weightRecords.isNotEmpty()) {
                // Sort by timestamp to ensure correct order
                val sortedRecords = weightRecords.sortedBy { it.timestamp }
                
                // Get the first weight record
                val firstWeightRecord = sortedRecords.first()
                
                // Get user settings to access height
                val userSettingsRepository = (weightRecordRepository as? nl.berrygrove.sft.repository.WeightRecordRepository)?.
                    weightRecordDao?.let {
                        SleepFastTrackerApplication.getInstance()?.userSettingsRepository
                    }
                
                if (userSettingsRepository != null) {
                    val userSettings = userSettingsRepository.getUserSettings()
                    
                    if (userSettings != null && userSettings.height > 0) {
                        // Recalculate weight achievements based on first weight and height
                        // This ensures the goals are always personalized
                        SleepFastTrackerApplication.addDebugLog(
                            "CheckInViewModel", 
                            "Recalculating weight goals based on height: ${userSettings.height} cm and first weight: ${firstWeightRecord.weight} kg"
                        )
                        achievementRepository.recalculateWeightAchievements(userSettings.height, firstWeightRecord.weight)
                    }
                }
                
                // Only check for unlock when we have at least 2 records
                if (weightRecords.size > 1) {
                    // Calculate weight loss (first weight - current weight)
                    val firstWeight = sortedRecords.first().weight
                    val currentWeight = sortedRecords.last().weight
                    val weightDifference = firstWeight - currentWeight
                    
                    // We're interested in weight loss, so if they gained weight, use 0
                    val weightLost = max(0f, weightDifference)
                    
                    // Check and unlock weight achievements
                    SleepFastTrackerApplication.addDebugLog("CheckInViewModel", "Checking weight achievements with loss of $weightLost kg")
                    achievementRepository.checkAndUnlockWeightAchievements(weightLost)
                }
            }
        } catch (e: Exception) {
            SleepFastTrackerApplication.addDebugLog("CheckInViewModel", "Error checking weight achievements: ${e.message}")
        }
    }
}

class CheckInViewModelFactory(
    private val weightRecordRepository: WeightRecordRepository,
    private val achievementRepository: AchievementRepository
) : ViewModelProvider.Factory {
    
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CheckInViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CheckInViewModel(weightRecordRepository, achievementRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 