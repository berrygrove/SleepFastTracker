package nl.berrygrove.sft.ui.setup

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.ViewModelProvider
import nl.berrygrove.sft.R
import nl.berrygrove.sft.ui.home.HomeActivity
import nl.berrygrove.sft.ui.setup.fragments.*

/**
 * Activity that hosts the setup wizard fragments.
 * Users see this only the first time they open the app.
 */
class SetupActivity : AppCompatActivity(), SetupNavigationListener {

    private lateinit var viewModel: SetupViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this).get(SetupViewModel::class.java)

        // Only load the first fragment if this is a new instance
        if (savedInstanceState == null) {
            navigateToStep(1)
        }
    }

    override fun navigateToStep(step: Int) {
        val fragment = when (step) {
            1 -> WelcomeFragment()
            2 -> PersonalInfoFragment()
            3 -> BodyInfoFragment()
            4 -> SleepScheduleFragment()
            5 -> FastingScheduleFragment()
            6 -> FinalWelcomeFragment()
            else -> WelcomeFragment()
        }

        supportFragmentManager.beginTransaction()
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            .replace(R.id.setup_container, fragment)
            .commit()
    }

    override fun finishSetup() {
        // Save that setup is completed
        viewModel.setSetupCompleted()
        
        // Redirect to home activity
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        
        // Close this activity
        finish()
    }
} 