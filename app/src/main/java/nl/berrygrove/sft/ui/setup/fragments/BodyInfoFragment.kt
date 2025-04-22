package nl.berrygrove.sft.ui.setup.fragments

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
 * Body info screen - third step in the setup wizard.
 */
class BodyInfoFragment : Fragment() {

    private lateinit var navigationListener: SetupNavigationListener
    private lateinit var viewModel: SetupViewModel
    private lateinit var heightEditText: EditText
    private lateinit var weightEditText: EditText
    private lateinit var bmiResultTextView: TextView

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
        return inflater.inflate(R.layout.fragment_body_info, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        heightEditText = view.findViewById(R.id.et_height)
        weightEditText = view.findViewById(R.id.et_weight)
        bmiResultTextView = view.findViewById(R.id.tv_bmi_result)
        
        setupTextWatchers()
        
        view.findViewById<Button>(R.id.btn_next).setOnClickListener {
            if (validateInputs()) {
                saveData()
                
                // Save weight record to database immediately
                viewModel.createWeightRecord()
                
                navigationListener.navigateToStep(4)
            }
        }
    }
    
    private fun setupTextWatchers() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                updateBMI()
            }
        }
        
        heightEditText.addTextChangedListener(textWatcher)
        weightEditText.addTextChangedListener(textWatcher)
    }
    
    private fun updateBMI() {
        val height = heightEditText.text.toString().toFloatOrNull() ?: 0f
        val weight = weightEditText.text.toString().toFloatOrNull() ?: 0f
        
        if (height > 0 && weight > 0) {
            viewModel.saveBodyInfo(height, weight)
            val bmi = viewModel.calculateBMI()
            val category = viewModel.getBMICategory()
            
            // Display BMI result with detailed categorization info for debugging
            bmiResultTextView.text = "Your BMI: $bmi ($category)\n" +
                "Underweight: <18.5 | Normal: 18.5-24.9 | Overweight: 25-29.9 | Obese: ≥30"
        } else {
            bmiResultTextView.text = "Your BMI: --"
        }
    }

    private fun validateInputs(): Boolean {
        val height = heightEditText.text.toString()
        val weight = weightEditText.text.toString()
        
        if (height.isEmpty()) {
            Toast.makeText(context, "Please enter your height", Toast.LENGTH_SHORT).show()
            return false
        }
        
        if (weight.isEmpty()) {
            Toast.makeText(context, "Please enter your weight", Toast.LENGTH_SHORT).show()
            return false
        }
        
        val heightFloat = height.toFloatOrNull()
        if (heightFloat == null || heightFloat < 50 || heightFloat > 250) {
            Toast.makeText(context, "Please enter a valid height in cm (50-250)", Toast.LENGTH_SHORT).show()
            return false
        }
        
        val weightFloat = weight.toFloatOrNull()
        if (weightFloat == null || weightFloat < 20 || weightFloat > 500) {
            Toast.makeText(context, "Please enter a valid weight in kg (20-500)", Toast.LENGTH_SHORT).show()
            return false
        }
        
        return true
    }

    private fun saveData() {
        val height = heightEditText.text.toString().toFloat()
        val weight = weightEditText.text.toString().toFloat()
        viewModel.saveBodyInfo(height, weight)
    }
} 