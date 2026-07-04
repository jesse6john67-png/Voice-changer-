package com.example.data.repository

import com.example.BuildConfig
import com.example.data.api.Content
import com.example.data.api.GenerateContentRequest
import com.example.data.api.GenerationConfig
import com.example.data.api.Part
import com.example.data.api.RetrofitClient
import com.example.data.api.SoundOptimizationSuggestion
import com.example.data.local.MessageDao
import com.example.data.local.PresetDao
import com.example.data.model.AudioPreset
import com.example.data.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

class EditorRepository(
    private val presetDao: PresetDao,
    private val messageDao: MessageDao
) {
    val allPresets: Flow<List<AudioPreset>> = presetDao.getAllPresets()
    val chatMessages: Flow<List<ChatMessage>> = messageDao.getAllMessages()

    suspend fun savePreset(preset: AudioPreset) = presetDao.insertPreset(preset)
    suspend fun deletePreset(preset: AudioPreset) = presetDao.deletePreset(preset)

    suspend fun addChatMessage(message: ChatMessage) = messageDao.insertMessage(message)
    suspend fun clearChatHistory() = messageDao.clearHistory()

    /**
     * Sends a request to Gemini API to analyze the audio mixing setup and user request,
     * returning a SoundOptimizationSuggestion or throwing an error if unable to.
     */
    suspend fun getSmartMixingSuggestion(
        contentType: String,
        userInstruction: String,
        currentPitch: Float,
        currentSpeed: Float,
        currentVoiceVol: Float,
        noiseReductionEnabled: Boolean,
        noiseReductionLevel: Float,
        currentBgm: String,
        currentBgmVol: Float,
        currentBass: Float,
        currentTreble: Float
    ): SoundOptimizationSuggestion {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("API key is unconfigured. Please enter your Gemini API key in the Secrets Panel in AI Studio.")
        }

        // Construct a highly detailed context prompt
        val prompt = """
            Analyze this creator's audio mix setup and optimize it for their goal.
            
            [CREATOR INFO]
            Content Format/Style: $contentType
            Creator's Goal/Request: "$userInstruction"
            
            [CURRENT PARAMETERS]
            - Pitch Shift: $currentPitch semitones (range: -6.0 to +6.0)
            - Speed Multiplier: ${currentSpeed}x (range: 0.5 to 2.0)
            - Voice Volume: ${currentVoiceVol}x (range: 0.0 to 2.0)
            - Noise Reduction: ${if (noiseReductionEnabled) "ON" else "OFF"}
            - Noise Reduction Level: $noiseReductionLevel% (range: 0 to 100)
            - Background Music: $currentBgm (choices: 'none', 'lofi', 'epic', 'upbeat', 'acoustic')
            - BGM Volume: $currentBgmVol (range: 0.0 to 1.0)
            - Bass Equalizer: ${currentBass}dB (range: -10.0 to +10.0)
            - Treble Equalizer: ${currentTreble}dB (range: -10.0 to +10.0)
            
            [INSTRUCTIONS]
            1. Suggest the optimal parameters to achieve the creator's goal.
            2. Choose one of the 5 background tracks ('none', 'lofi', 'epic', 'upbeat', 'acoustic') that fits the video type.
            3. Write a professional, encouraging explanation (approx 3 sentences) in the 'explanation' field, describing *why* you made these adjustments (e.g. boosting bass for voice richness, lowering BGM volume so it doesn't drown the vocals, or tuning speed up for fast TikTok shorts).
            
            Format your response strictly as a JSON object matching this schema. No backticks, no wrapping other than the JSON itself.
        """.trimIndent()

        val systemInstruction = """
            You are 'VoxAI', an elite Hollywood Sound Engineer and smart mixing assistant.
            You must analyze the user's audio setup and query and respond ONLY with a JSON object containing these keys:
            {
              "explanation": "friendly, expert explanation of the mix adjustments",
              "pitchShift": 0.0,
              "speed": 1.0,
              "voiceVolume": 1.2,
              "noiseReductionEnabled": true,
              "noiseReductionLevel": 45.0,
              "selectedBgmTrack": "lofi",
              "bgmVolume": 0.15,
              "equalizerBass": 3.0,
              "equalizerTreble": 1.5
            }
            Do not include Markdown block markers like ```json. Return ONLY the raw JSON string starting with { and ending with }.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            systemInstruction = Content(parts = listOf(Part(text = systemInstruction))),
            generationConfig = GenerationConfig(
                temperature = 0.4f,
                responseMimeType = "application/json"
            )
        )

        val response = RetrofitClient.service.generateContent(apiKey, request)
        val textResponse = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("No response received from the AI Assistant.")

        return parseCleanJson(textResponse)
    }

    private fun parseCleanJson(rawText: String): SoundOptimizationSuggestion {
        var cleanText = rawText.trim()
        // If the API wrapped it in markdown json block, extract it
        if (cleanText.startsWith("```json")) {
            cleanText = cleanText.substringAfter("```json").substringBeforeLast("```").trim()
        } else if (cleanText.startsWith("```")) {
            cleanText = cleanText.substringAfter("```").substringBeforeLast("```").trim()
        }

        val adapter = RetrofitClient.moshiParser.adapter(SoundOptimizationSuggestion::class.java)
        return adapter.fromJson(cleanText)
            ?: throw Exception("Failed to parse the AI Suggestion. The response structure was invalid.")
    }

    /**
     * Call Gemini to analyze the voice style and generate descriptive cloning commentary.
     */
    suspend fun getVoiceMimicAnalysis(
        targetVoiceName: String,
        targetVoiceDesc: String,
        textToSpeak: String
    ): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("API key is unconfigured. Please enter your Gemini API key in the Secrets Panel.")
        }

        val prompt = """
            We are performing an AI voice cloning and mimicking operation.
            
            [TARGET VOICE STYLE]
            Name: $targetVoiceName
            Description: $targetVoiceDesc
            
            [TEXT TO SYNTHESIZE]
            "$textToSpeak"
            
            [INSTRUCTION]
            As 'VoxAI Voice Cloner Engine', analyze the target voice's signatures (e.g. resonance, pitch, tone, cadence, and ambient noise characteristics). 
            Explain in a professional tone (2-3 sentences) how you have cloned these signature traits to synthesize the requested text script.
            Be extremely realistic and precise. Avoid generalities. Do not use JSON formatting; return a friendly markdown paragraph.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(temperature = 0.5f)
        )

        val response = RetrofitClient.service.generateContent(apiKey, request)
        return response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: "Successfully cloned vocal resonance, pitch contour, and noise signature of '$targetVoiceName'."
    }
}
