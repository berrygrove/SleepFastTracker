package nl.berrygrove.sft.ui.setup

/**
 * Interface defining the navigation contract for the setup process.
 * Implemented by SetupActivity to handle navigation between setup steps.
 */
interface SetupNavigationListener {
    /**
     * Navigate to a specific step in the setup process
     * @param step The step number to navigate to
     */
    fun navigateToStep(step: Int)
    
    /**
     * Called when the setup process is complete
     */
    fun finishSetup()
} 