package nl.berrygrove.sft.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import nl.berrygrove.sft.data.model.Achievement

@Dao
interface AchievementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(achievement: Achievement): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(achievements: List<Achievement>)

    @Update
    suspend fun update(achievement: Achievement)
    
    @Query("UPDATE achievements SET achieved = :achieved WHERE id = :id")
    suspend fun updateAchievedStatus(id: Long, achieved: Boolean)
    
    @Query("SELECT * FROM achievements ORDER BY category, threshold")
    fun getAllAchievements(): Flow<List<Achievement>>
    
    @Query("SELECT * FROM achievements WHERE category = :category ORDER BY threshold")
    fun getAchievementsByCategory(category: String): Flow<List<Achievement>>
    
    @Query("SELECT * FROM achievements WHERE achieved = 1 ORDER BY category, threshold")
    fun getAchievedAchievements(): Flow<List<Achievement>>
    
    @Query("SELECT * FROM achievements WHERE id = :id LIMIT 1")
    suspend fun getAchievementById(id: Long): Achievement?
    
    @Query("SELECT COUNT(*) FROM achievements WHERE achieved = 1")
    fun getAchievedAchievementsCount(): Flow<Int>
    
    @Query("SELECT SUM(points) FROM achievements WHERE achieved = 1")
    fun getTotalAchievementPoints(): Flow<Int?>
    
    @Query("SELECT * FROM achievements WHERE category = :category AND threshold <= :value AND achieved = 0 ORDER BY threshold")
    suspend fun getUnachievedEligibleAchievements(category: String, value: Float): List<Achievement>
    
    @Query("SELECT COUNT(*) FROM achievements")
    suspend fun getAchievementCount(): Int
    
    @Query("DELETE FROM achievements")
    suspend fun deleteAllAchievements()

    @Query("DELETE FROM achievements WHERE category = :category")
    suspend fun deleteAchievementsByCategory(category: String)

    @Query("SELECT * FROM achievements WHERE category = 'weight' AND threshold <= :weightLostKg AND achieved = 0")
    suspend fun getUnachievedEligibleWeightAchievements(weightLostKg: Float): List<Achievement>
} 