package nl.berrygrove.sft.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import nl.berrygrove.sft.data.dao.UserSettingsDao
import nl.berrygrove.sft.data.model.UserSettings

/**
 * Repository for accessing and managing UserSettings data.
 */
class UserSettingsRepository(private val userSettingsDao: UserSettingsDao) {

    val userSettings: Flow<UserSettings?> = userSettingsDao.getUserSettings()

    suspend fun saveUserSettings(userSettings: UserSettings) {
        userSettingsDao.insert(userSettings)
    }

    suspend fun updateUserSettings(userSettings: UserSettings) {
        userSettingsDao.update(userSettings)
    }

    suspend fun clearAll() {
        userSettingsDao.deleteAll()
    }
    
    /**
     * Check if setup wizard has been completed
     * @return true if setup is completed, false otherwise
     */
    suspend fun isSetupCompleted(): Boolean {
        val settings = userSettings.firstOrNull()
        return settings?.setupCompleted ?: false
    }
    
    /**
     * Get the current user settings (non-Flow version)
     * @return UserSettings or null if not found
     */
    suspend fun getUserSettings(): UserSettings? {
        return userSettings.firstOrNull()
    }
} 