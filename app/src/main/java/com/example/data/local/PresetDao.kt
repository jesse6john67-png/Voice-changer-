package com.example.data.local

import androidx.room.*
import com.example.data.model.AudioPreset
import kotlinx.coroutines.flow.Flow

@Dao
interface PresetDao {
    @Query("SELECT * FROM audio_presets ORDER BY name ASC")
    fun getAllPresets(): Flow<List<AudioPreset>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: AudioPreset)

    @Update
    suspend fun updatePreset(preset: AudioPreset)

    @Delete
    suspend fun deletePreset(preset: AudioPreset)

    @Query("SELECT * FROM audio_presets WHERE id = :id")
    suspend fun getPresetById(id: Int): AudioPreset?
}
