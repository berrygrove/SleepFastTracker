package nl.berrygrove.sft.data.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import nl.berrygrove.sft.data.model.UserSettings

/**
 * Data Access Object for the UserSettings entity.
 */
@Dao
interface UserSettingsDao {
    @Query("SELECT * FROM user_settings WHERE id = 1")
    fun getUserSettings(): Flow<UserSettings?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(userSettings: UserSettings)

    @Update
    suspend fun update(userSettings: UserSettings)

    @Query("DELETE FROM user_settings")
    suspend fun deleteAll()
} 