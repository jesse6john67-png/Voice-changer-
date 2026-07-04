package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audio_presets")
data class AudioPreset(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val pitchShift: Float,
    val speed: Float,
    val voiceVolume: Float,
    val noiseReductionEnabled: Boolean,
    val noiseReductionLevel: Float,
    val selectedBgmTrack: String,
    val bgmVolume: Float,
    val equalizerBass: Float,
    val equalizerTreble: Float,
    val isSystem: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
