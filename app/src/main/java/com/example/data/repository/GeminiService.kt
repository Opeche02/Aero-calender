package com.example.data.repository

import android.util.Log
import com.example.BuildConfig
import com.example.data.local.EventDao
import com.example.data.model.Event
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.*
import java.util.concurrent.TimeUnit

data class EventSuggestion(
    val title: String,
    val location: String = "",
    val category: String = "Local",
    val hour: Int = 12,
    val minute: Int = 0,
    val description: String = "",
    val justification: String = ""
)

class GeminiService(private val eventDao: EventDao) {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Obtains real-time AI suggestions based on user context, input text, and event history databases.
     */
    suspend fun getEventSuggestions(
        inputText: String,
        selectedDate: Calendar
    ): List<EventSuggestion> = withContext(Dispatchers.IO) {
        val lowercaseInput = inputText.trim().lowercase(Locale.getDefault())
        if (lowercaseInput.isEmpty()) {
            return@withContext emptyList()
        }

        // Fetch historical events as context
        val allEventsFlow = eventDao.getActiveEvents()
        // Try to get a static list of the up to 50 active items for context
        val historyList = try {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.MONTH, -3) // Last 3 months
            eventDao.getUpcomingEventsSync(calendar.timeInMillis, 50)
        } catch (e: Exception) {
            emptyList()
        }

        // 1. Check if Gemini API is available and key is configured (i.e. not the placeholder string)
        val apiKey = BuildConfig.GEMINI_API_KEY
        val isKeyConfigured = apiKey.isNotEmpty() && 
                apiKey != "MY_GEMINI_API_KEY" && 
                !apiKey.contains("PLACEHOLDER")

        if (isKeyConfigured) {
            try {
                return@withContext callGeminiApi(inputText, selectedDate, historyList, apiKey)
            } catch (e: Exception) {
                Log.e("GeminiService", "Gemini API call failed, falling back to local heuristics context engine", e)
            }
        }

        // 2. Local context-aware heuristic intelligence fallback
        return@withContext generateLocalHeuristicSuggestions(inputText, selectedDate, historyList)
    }

    private suspend fun callGeminiApi(
        inputText: String,
        selectedDate: Calendar,
        historyList: List<Event>,
        apiKey: String
    ): List<EventSuggestion> {
        val selectedDateStr = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDate.time)
        val selectedDayOfWeek = java.text.SimpleDateFormat("EEEE", Locale.getDefault()).format(selectedDate.time)

        // Compile historical events into a succinct prompt context
        val historyCtx = historyList.take(15).joinToString("\n") { ev ->
            "- Title: '${ev.title}', Category: '${ev.categoryName}', Location: '${ev.location}', Time: ${formatHourMin(ev.startTime)}"
        }

        val prompt = """
            You are a minimalist event suggestion engine embedded in an Android calendar application.
            The user is typing: "$inputText"
            The calendar current visible/selected date is: $selectedDateStr ($selectedDayOfWeek).
            
            We also have a few of the user's historical calendar records to learn patterns from:
            $historyCtx
            
            Generate between 1 and 3 smart event suggestions completing what the user is typing based on their typical routines (matched from history if any overlap) and current context. Each suggestion must predict:
            1. An elegant 'title' (e.g. if typing 'gym', auto-resolve to 'Gym Gym Workout' or their historical 'Gym Workout').
            2. A logical 'location' (e.g. Gold's Gym or Google Meet link based on categories or history).
            3. A recommended 'category' (Choose strictly from [Work, Personal, Family, Fitness, Local]).
            4. Start time hour (0-23) and minute (0-59).
            5. A short detail description.
            6. A concise 1-sentence 'justification' from the AI (e.g. "Recommended based on your Monday gym routines" or "Schedules a standard work review block").

            Respond STRICTLY with a valid JSON array of objects. Do not enclose in markdown blocks other than standard raw json text.
            JSON schema format:
            [
              {
                "title": "string",
                "location": "string",
                "category": "string",
                "hour": integer,
                "minute": integer,
                "description": "string",
                "justification": "string"
              }
            ]
        """.trimIndent()

        // Construct Gemini REST API payload
        val jsonPayload = JSONObject().apply {
            val contentsArr = org.json.JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArr = org.json.JSONArray().apply {
                        val partObj = JSONObject().apply {
                            put("text", prompt)
                        }
                        put(partObj)
                    }
                    put("parts", partsArr)
                }
                put(contentObj)
            }
            put("contents", contentsArr)

            // Optional structural JSON enforce
            val configObj = JSONObject().apply {
                val respFormatObj = JSONObject().apply {
                    put("mimeType", "application/json")
                }
                put("responseFormat", respFormatObj)
                put("temperature", 0.4)
            }
            put("generationConfig", configObj)
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonPayload.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Response code: ${response.code}")
            }
            val rawOutput = response.body?.string() ?: ""
            return parseGeminiResponse(rawOutput)
        }
    }

    private fun parseGeminiResponse(rawJson: String): List<EventSuggestion> {
        val rootObj = JSONObject(rawJson)
        val candidatesArr = rootObj.optJSONArray("candidates") ?: return emptyList()
        if (candidatesArr.length() == 0) return emptyList()
        
        val firstCandContent = candidatesArr.getJSONObject(0).optJSONObject("content") ?: return emptyList()
        val partsArr = firstCandContent.optJSONArray("parts") ?: return emptyList()
        if (partsArr.length() == 0) return emptyList()

        val responseText = partsArr.getJSONObject(0).optString("text") ?: ""
        if (responseText.isEmpty()) return emptyList()

        // Parse list from JSON Array
        val suggestions = mutableListOf<EventSuggestion>()
        try {
            val jsonArray = org.json.JSONArray(responseText.trim())
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                suggestions.add(
                    EventSuggestion(
                        title = item.optString("title", "Scheduled Event"),
                        location = item.optString("location", ""),
                        category = item.optString("category", "Local"),
                        hour = item.optInt("hour", 12),
                        minute = item.optInt("minute", 0),
                        description = item.optString("description", ""),
                        justification = item.optString("justification", "Suggested from Gemini AI Context")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "Error decoding JSON suggestions array, attempting raw fallback extraction", e)
        }
        return suggestions
    }

    /**
     * Local rule pattern matcher matching based on database history.
     */
    private fun generateLocalHeuristicSuggestions(
        inputText: String,
        selectedDate: Calendar,
        historyList: List<Event>
    ): List<EventSuggestion> {
        val suggestions = mutableListOf<EventSuggestion>()
        val query = inputText.lowercase(Locale.getDefault())

        // Compile unique historical match options that contain the text
        val matches = historyList.filter { 
            it.title.lowercase(Locale.getDefault()).contains(query) ||
            it.categoryName.lowercase(Locale.getDefault()).contains(query)
        }.distinctBy { it.title.lowercase(Locale.getDefault()) }

        if (matches.isNotEmpty()) {
            matches.take(3).forEach { match ->
                val cal = Calendar.getInstance().apply { timeInMillis = match.startTime }
                suggestions.add(
                    EventSuggestion(
                        title = match.title,
                        location = match.location,
                        category = match.categoryName,
                        hour = cal.get(Calendar.HOUR_OF_DAY),
                        minute = cal.get(Calendar.MINUTE),
                        description = match.description,
                        justification = "Matched historical habits in '${match.categoryName}' folder."
                    )
                )
            }
        } else {
            // Generate standard context defaults
            if (query.contains("gym") || query.contains("workout") || query.contains("run")) {
                suggestions.add(
                    EventSuggestion(
                        title = "Gym Workout Session",
                        location = "Gold's Gym",
                        category = "Fitness",
                        hour = 17,
                        minute = 0,
                        description = "Fitness conditioning routine.",
                        justification = "Typical workout window recommendation (5:00 PM)."
                    )
                )
            } else if (query.contains("meeting") || query.contains("standup") || query.contains("sync") || query.contains("work")) {
                suggestions.add(
                    EventSuggestion(
                        title = "Team Project Sync",
                        location = "Google Meet Link",
                        category = "Work",
                        hour = 10,
                        minute = 0,
                        description = "Progress tracking and sprint alignment review.",
                        justification = "Standard Work morning schedule recommended (10:00 AM)."
                    )
                )
            } else if (query.contains("dinner") || query.contains("lunch") || query.contains("coffee") || query.contains("drink")) {
                val isLunch = query.contains("lunch") || query.contains("coffee")
                suggestions.add(
                    EventSuggestion(
                        title = if (isLunch) "Coffee with Friend" else "Dinner with Family",
                        location = "Olive Garden",
                        category = "Personal",
                        hour = if (isLunch) 12 else 19,
                        minute = if (isLunch) 0 else 30,
                        description = "Social catch-up reservation.",
                        justification = "Personal evening slot advice."
                    )
                )
            } else {
                // Generic autocomplete completion
                val formattedTitle = inputText.trim().replaceFirstChar { it.uppercase() }
                suggestions.add(
                    EventSuggestion(
                        title = "$formattedTitle",
                        location = "Local Area",
                        category = "Local",
                        hour = 12,
                        minute = 0,
                        description = "",
                        justification = "Quick Add scheduler item."
                    )
                )
                suggestions.add(
                    EventSuggestion(
                        title = "$formattedTitle Meeting",
                        location = "Conference Room B",
                        category = "Work",
                        hour = 14,
                        minute = 0,
                        description = "",
                        justification = "Afternoon review block."
                    )
                )
            }
        }

        return suggestions
    }

    private fun formatHourMin(ms: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = ms }
        val hr = cal.get(Calendar.HOUR_OF_DAY)
        val min = cal.get(Calendar.MINUTE)
        return String.format(Locale.getDefault(), "%02d:%02d", hr, min)
    }
}
