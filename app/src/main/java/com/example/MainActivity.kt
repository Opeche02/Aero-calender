package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.data.local.AppDatabase
import com.example.data.repository.CalendarRepository
import com.example.ui.components.CalendarMainDashboard
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.CalendarViewModel
import com.example.ui.widget.CalendarAppWidgetProvider

class MainActivity : ComponentActivity() {
    
    private lateinit var viewModel: CalendarViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Local-first architecture database and repository initializer
        val database = AppDatabase.getDatabase(this)
        val repository = CalendarRepository(
            eventDao = database.eventDao(),
            categoryDao = database.categoryDao(),
            syncLogDao = database.syncLogDao()
        )
        viewModel = CalendarViewModel(repository)

        // Connect the dynamic update trigger to keep the Home Screen Widget fully real-time
        viewModel.onDataChangedListener = {
            CalendarAppWidgetProvider.triggerUpdate(this)
        }

        // Processes custom launch events from widget clicks
        handleWidgetIntents(intent)

        setContent {
            MyApplicationTheme {
                CalendarMainDashboard(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetIntents(intent)
    }

    private fun handleWidgetIntents(intent: Intent?) {
        if (intent == null) return

        val openQuickAdd = intent.getBooleanExtra("EXTRA_OPEN_QUICK_ADD", false)
        if (openQuickAdd) {
            viewModel.setShowQuickAddDialog(true)
        }

        val eventId = intent.getLongExtra("EXTRA_EVENT_ID", -1L)
        if (eventId != -1L) {
            viewModel.selectEventById(eventId)
        }
    }
}

