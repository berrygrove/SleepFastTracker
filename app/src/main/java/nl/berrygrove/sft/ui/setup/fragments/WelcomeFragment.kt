package nl.berrygrove.sft.ui.setup.fragments

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import nl.berrygrove.sft.R
import nl.berrygrove.sft.ui.setup.SetupNavigationListener
import nl.berrygrove.sft.ui.setup.SetupViewModel

/**
 * Welcome screen - first step in the setup wizard.
 */
class WelcomeFragment : Fragment() {

    private lateinit var navigationListener: SetupNavigationListener
    private lateinit var notificationPermissionButton: Button
    private lateinit var getStartedButton: Button
    private lateinit var notificationStatusText: TextView
    private lateinit var nameEditText: EditText
    private lateinit var viewModel: SetupViewModel

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        updateNotificationStatus(isGranted)
    }

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
        return inflater.inflate(R.layout.fragment_welcome, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        notificationPermissionButton = view.findViewById(R.id.btn_notification_permission)
        getStartedButton = view.findViewById(R.id.btn_get_started)
        notificationStatusText = view.findViewById(R.id.tv_notification_status)
        nameEditText = view.findViewById(R.id.et_name)

        // Check current notification permission status
        val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        updateNotificationStatus(hasNotificationPermission)

        notificationPermissionButton.setOnClickListener {
            requestNotificationPermission()
        }

        getStartedButton.setOnClickListener {
            if (validateInput()) {
                val name = nameEditText.text.toString().trim()
                viewModel.setUserName(name)
                navigationListener.navigateToStep(2)
            }
        }
    }

    private fun updateNotificationStatus(isGranted: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (isGranted) {
                notificationStatusText.text = "Notifications enabled"
                notificationStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.success))
                notificationPermissionButton.visibility = View.GONE
            } else {
                notificationStatusText.text = "Notifications disabled"
                notificationStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.error))
                notificationPermissionButton.visibility = View.VISIBLE
            }
        } else {
            notificationStatusText.text = "Notifications enabled"
            notificationStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.success))
            notificationPermissionButton.visibility = View.GONE
        }
    }

    private fun validateInput(): Boolean {
        val name = nameEditText.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(context, "Please enter your name", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
} 