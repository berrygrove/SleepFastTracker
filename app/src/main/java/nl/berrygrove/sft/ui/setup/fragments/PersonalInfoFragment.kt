package nl.berrygrove.sft.ui.setup.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import nl.berrygrove.sft.R
import nl.berrygrove.sft.ui.setup.SetupNavigationListener
import nl.berrygrove.sft.ui.setup.SetupViewModel

/**
 * Personal info screen - second step in the setup wizard.
 */
class PersonalInfoFragment : Fragment() {

    private lateinit var navigationListener: SetupNavigationListener
    private lateinit var viewModel: SetupViewModel
    private lateinit var ageEditText: EditText
    private lateinit var nameTextView: TextView

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
        return inflater.inflate(R.layout.fragment_personal_info, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ageEditText = view.findViewById(R.id.et_age)
        nameTextView = view.findViewById(R.id.tv_personal_info_title)
        
        // Update title with user's name if available
        val title = nameTextView.text.toString()
        if (viewModel.getUserName().isNotEmpty()) {
            nameTextView.text = "Hi ${viewModel.getUserName()}, tell us about yourself"
        }
        
        view.findViewById<Button>(R.id.btn_next).setOnClickListener {
            if (validateInputs()) {
                saveData()
                navigationListener.navigateToStep(3)
            }
        }
    }

    private fun validateInputs(): Boolean {
        val age = ageEditText.text.toString()
        
        if (age.isEmpty()) {
            Toast.makeText(context, "Please enter your age", Toast.LENGTH_SHORT).show()
            return false
        }
        
        val ageInt = age.toIntOrNull()
        if (ageInt == null || ageInt <= 0 || ageInt > 120) {
            Toast.makeText(context, "Please enter a valid age (1-120)", Toast.LENGTH_SHORT).show()
            return false
        }
        
        return true
    }

    private fun saveData() {
        val age = ageEditText.text.toString().toInt()
        viewModel.savePersonalInfo(viewModel.getUserName(), age) // Use existing name from ViewModel
    }
} 