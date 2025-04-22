package nl.berrygrove.sft

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import nl.berrygrove.sft.ui.home.HomeActivity
import nl.berrygrove.sft.ui.setup.SetupActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Check if setup is completed
        checkSetupStatus()
    }
    
    private fun checkSetupStatus() {
        val userSettingsRepository = (application as SleepFastTrackerApplication).userSettingsRepository
        
        lifecycleScope.launch {
            val isSetupCompleted = userSettingsRepository.isSetupCompleted()
            
            if (!isSetupCompleted) {
                // If setup is not completed, launch SetupActivity
                val intent = Intent(this@MainActivity, SetupActivity::class.java)
                startActivity(intent)
                finish() // Close MainActivity so user can't go back with back button
            } else {
                // If setup is completed, launch HomeActivity
                val intent = Intent(this@MainActivity, HomeActivity::class.java)
                startActivity(intent)
                finish() // Close MainActivity
            }
        }
    }
} 