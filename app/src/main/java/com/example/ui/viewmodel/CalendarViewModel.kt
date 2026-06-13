package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CalendarCategory
import com.example.data.model.Event
import com.example.data.model.SyncLog
import com.example.data.repository.CalendarRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CalendarViewModel(private val repository: CalendarRepository) : ViewModel() {

    // OnDataChanged listener for home screen widget sync
    var onDataChangedListener: (() -> Unit)? = null

    // Gemini AI Suggestion States
    private val geminiService = com.example.data.repository.GeminiService(repository.eventDao)
    
    private val _aiSuggestions = MutableStateFlow<List<com.example.data.repository.EventSuggestion>>(emptyList())
    val aiSuggestions: StateFlow<List<com.example.data.repository.EventSuggestion>> = _aiSuggestions.asStateFlow()

    private val _isGeneratingSuggestions = MutableStateFlow(false)
    val isGeneratingSuggestions: StateFlow<Boolean> = _isGeneratingSuggestions.asStateFlow()

    // View States
    private val _selectedDate = MutableStateFlow(Calendar.getInstance())
    val selectedDate: StateFlow<Calendar> = _selectedDate.asStateFlow()

    private val _currentViewType = MutableStateFlow(CalendarViewType.MONTH) // MONTH, WEEK, DAY
    val currentViewType: StateFlow<CalendarViewType> = _currentViewType.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _selectedEvent = MutableStateFlow<Event?>(null)
    val selectedEvent: StateFlow<Event?> = _selectedEvent.asStateFlow()

    private val _showQuickAddDialog = MutableStateFlow(false)
    val showQuickAddDialog: StateFlow<Boolean> = _showQuickAddDialog.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    // Interactive custom categories configuration
    private val _activeCategoryFilters = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val activeCategoryFilters: StateFlow<Map<String, Boolean>> = _activeCategoryFilters.asStateFlow()

    // Core Streams
    val activeEvents: StateFlow<List<Event>> = repository.activeEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CalendarCategory>> = repository.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncLogs: StateFlow<List<SyncLog>> = repository.syncLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Combined stream to show only active-filtered events
    val filteredEvents: StateFlow<List<Event>> = combine(activeEvents, _activeCategoryFilters) { events, filters ->
        if (filters.isEmpty()) {
            events
        } else {
            events.filter { filters[it.categoryName] != false }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.populateDefaultsIfEmpty()
            // Initialize filters map based on categories
            repository.categories.collectLatest { catList ->
                val filters = _activeCategoryFilters.value.toMutableMap()
                catList.forEach { category ->
                    if (!filters.containsKey(category.name)) {
                        filters[category.name] = category.isEnabled
                    }
                }
                _activeCategoryFilters.value = filters
            }
            notifyDataChanged()
        }
    }

    private fun notifyDataChanged() {
        onDataChangedListener?.invoke()
    }

    fun selectDate(calendar: Calendar) {
        _selectedDate.value = calendar.clone() as Calendar
    }

    fun changeViewType(viewType: CalendarViewType) {
        _currentViewType.value = viewType
    }

    fun setSelectedEvent(event: Event?) {
        _selectedEvent.value = event
    }

    fun setShowQuickAddDialog(show: Boolean) {
        _showQuickAddDialog.value = show
        // Clear suggestions list on dismiss/open
        if (!show) {
            _aiSuggestions.value = emptyList()
        }
    }

    fun toggleCategoryFilter(categoryName: String) {
        val current = _activeCategoryFilters.value.toMutableMap()
        val isEnabled = current[categoryName] ?: true
        current[categoryName] = !isEnabled
        _activeCategoryFilters.value = current
    }

    fun selectEventById(id: Long) {
        viewModelScope.launch {
            val event = repository.getEventById(id)
            if (event != null) {
                _selectedEvent.value = event
            }
        }
    }

    fun fetchAiSuggestions(inputText: String) {
        if (inputText.trim().isEmpty()) {
            _aiSuggestions.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isGeneratingSuggestions.value = true
            try {
                val suggestions = geminiService.getEventSuggestions(inputText, _selectedDate.value)
                _aiSuggestions.value = suggestions
            } catch (e: Exception) {
                _aiSuggestions.value = emptyList()
            } finally {
                _isGeneratingSuggestions.value = false
            }
        }
    }

    fun addQuickParsedEvent(text: String) {
        val parsed = parseSamsungNaturalLanguage(text)
        viewModelScope.launch {
            repository.insertEvent(parsed)
            notifyDataChanged()
        }
    }

    fun addSuggestedEvent(suggestion: com.example.data.repository.EventSuggestion) {
        val calendar = Calendar.getInstance().apply {
            time = _selectedDate.value.time
            set(Calendar.HOUR_OF_DAY, suggestion.hour)
            set(Calendar.MINUTE, suggestion.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val event = Event(
            title = suggestion.title,
            description = "${suggestion.description}\n\n[Parsed using GenAI Recommendation Engine: ${suggestion.justification}]",
            startTime = calendar.timeInMillis,
            endTime = calendar.timeInMillis + 60 * 60 * 1000,
            categoryName = suggestion.category,
            location = suggestion.location,
            syncSource = "Local"
        )
        viewModelScope.launch {
            repository.insertEvent(event)
            notifyDataChanged()
        }
    }

    fun updateEventDetails(event: Event) {
        viewModelScope.launch {
            repository.updateEvent(event)
            notifyDataChanged()
        }
    }

    fun deleteEventById(id: Long) {
        viewModelScope.launch {
            repository.deleteEvent(id)
            notifyDataChanged()
        }
    }

    fun startRemoteSync(source: String) {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                // Simulate net delay for premium look feel
                kotlinx.coroutines.delay(1200)
                val result = repository.simulateSync(source)
                _syncMessage.value = "Synced with $source Account successfully! Added: ${result.added}, Updated (Conflicts Resolved): ${result.updated}."
                notifyDataChanged()
            } catch (e: Exception) {
                _syncMessage.value = "Sync with $source channel failed: ${e.message}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    /**
     * Advanced Natural Language Processor (Samsung style)
     * Maps phrase: "Dinner with Sarah in Personal category tomorrow at 7:30pm"
     */
    fun parseSamsungNaturalLanguage(input: String): Event {
        val lower = input.lowercase()
        val now = Calendar.getInstance()
        val eventCal = Calendar.getInstance()
        eventCal.time = _selectedDate.value.time // Base on current selection

        // 1. Determine Day Term
        if (lower.contains("tomorrow")) {
            eventCal.add(Calendar.DAY_OF_YEAR, 1)
        } else if (lower.contains("today")) {
            // keep today
            eventCal.set(Calendar.DAY_OF_YEAR, now.get(Calendar.DAY_OF_YEAR))
            eventCal.set(Calendar.YEAR, now.get(Calendar.YEAR))
        } else {
            // check days of week
            val days = mapOf(
                "monday" to Calendar.MONDAY,
                "tuesday" to Calendar.TUESDAY,
                "wednesday" to Calendar.WEDNESDAY,
                "thursday" to Calendar.THURSDAY,
                "friday" to Calendar.FRIDAY,
                "saturday" to Calendar.SATURDAY,
                "sunday" to Calendar.SUNDAY
            )
            for ((dayStr, calendarDay) in days) {
                if (lower.contains(dayStr)) {
                    val currentDayOfWeek = eventCal.get(Calendar.DAY_OF_WEEK)
                    var daysToAdd = calendarDay - currentDayOfWeek
                    if (daysToAdd <= 0) daysToAdd += 7 // Next week's target day if passed or today
                    eventCal.add(Calendar.DAY_OF_YEAR, daysToAdd)
                    break
                }
            }
        }

        // 2. Parse Time Term ("at 7pm", "at 4:30pm", "at 11am")
        var hour = 12
        var minute = 0
        val timeRegex = "(at\\s+)?(\\d{1,2})(:(\\d{2}))?\\s*(pm|am)?".toRegex()
        val matchResult = timeRegex.find(lower)
        if (matchResult != null) {
            val matchedHour = matchResult.groupValues[2].toInt()
            val matchedMinStr = matchResult.groupValues[4]
            val matchedMin = if (matchedMinStr.isNotEmpty()) matchedMinStr.toInt() else 0
            val amPm = matchResult.groupValues[5]

            hour = matchedHour
            minute = matchedMin

            if (amPm == "pm" && hour < 12) {
                hour += 12
            } else if (amPm == "am" && hour == 12) {
                hour = 0
            }
        } else {
            // Set default of current hour + 1
            hour = (now.get(Calendar.HOUR_OF_DAY) + 1) % 24
            minute = 0
        }

        eventCal.set(Calendar.HOUR_OF_DAY, hour)
        eventCal.set(Calendar.MINUTE, minute)
        eventCal.set(Calendar.SECOND, 0)
        eventCal.set(Calendar.MILLISECOND, 0)

        // 3. Category Match
        var catName = "Local"
        val cleanMapCategory = mapOf(
            "work" to "Work",
            "personal" to "Personal",
            "family" to "Family",
            "gym" to "Fitness",
            "fitness" to "Fitness"
        )
        for ((trigger, category) in cleanMapCategory) {
            if (lower.contains(trigger)) {
                catName = category
                break
            }
        }

        // 4. Extract Clean Event Title by trimming time/day qualifiers
        var titleCandidate = input
        val qualifiers = listOf(
            "tomorrow", "today", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
            "at ", "pm", "am", "in work", "in personal", "in family", "in fitness"
        )
        for (qualifier in qualifiers) {
            val index = titleCandidate.lowercase().indexOf(qualifier)
            if (index != -1) {
                titleCandidate = titleCandidate.substring(0, index)
            }
        }

        // Clean up trailing and leading spaces/punctuation
        titleCandidate = titleCandidate.trim().replace("[,\\s\\-at]+$".toRegex(), "").trim()

        if (titleCandidate.isEmpty()) {
            titleCandidate = "Quick Scheduling Event"
        }

        return Event(
            title = titleCandidate,
            startTime = eventCal.timeInMillis,
            endTime = eventCal.timeInMillis + 60 * 60 * 1000, // 1 hour duration default
            categoryName = catName,
            syncSource = "Local"
        )
    }

    fun updateCategoryColor(categoryName: String, colorHex: String) {
        viewModelScope.launch {
            repository.updateCategoryColor(categoryName, colorHex)
            notifyDataChanged()
        }
    }
}

enum class CalendarViewType {
    MONTH, WEEK, DAY
}
