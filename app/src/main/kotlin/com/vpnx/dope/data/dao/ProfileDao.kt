package com.vpnx.dope.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.vpnx.dope.data.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profiles ORDER BY createdAt DESC")
    fun getAllProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE isSelected = 1 LIMIT 1")
    fun getSelectedProfile(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedProfileSync(): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: String): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: ProfileEntity)

    @Update
    suspend fun update(profile: ProfileEntity)

    @Delete
    suspend fun delete(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE profiles SET isSelected = 0")
    suspend fun clearSelection()

    @Query("UPDATE profiles SET isSelected = 1 WHERE id = :id")
    suspend fun selectProfile(id: String)

    @Transaction
    suspend fun setSelected(id: String) {
        clearSelection()
        selectProfile(id)
    }

    @Query("UPDATE profiles SET lastPingMs = :pingMs WHERE id = :id")
    suspend fun updatePing(id: String, pingMs: Long)

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun getCount(): Int
}
