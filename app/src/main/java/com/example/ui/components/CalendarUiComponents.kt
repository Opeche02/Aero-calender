package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CalendarCategory
import com.example.data.model.Event
import com.example.data.model.SyncLog
import com.example.ui.viewmodel.CalendarViewModel
import com.example.ui.viewmodel.CalendarViewType
import java.text.SimpleDateFormat
import java.util.*

// Dynamic custom spring specifications for satisfying tactility
val TactileSpringSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessLow
)

val SlowTactileSpring = spring<Float>(
    dampingRatio = 0.75f,
    stiffness = 120f
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarMainDashboard(viewModel: CalendarViewModel) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val viewType by viewModel.currentViewType.collectAsState()
    val filteredEvents by viewModel.filteredEvents.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val activeFilters by viewModel.activeCategoryFilters.collectAsState()
    val selectedEvent by viewModel.selectedEvent.collectAsState()
    val showQuickAddDialog by viewModel.showQuickAddDialog.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    val syncLogs by viewModel.syncLogs.collectAsState()

    var showSyncLogs by remember { mutableStateOf(false) }
    var showColorCustomizerDialog by remember { mutableStateOf(false) }

    // Dynamic Snackbars for confirmation feedback
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(syncMessage) {
        syncMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearSyncMessage()
        }
    }

    Scaffold(
        snackbarHostStatus = snackbarHostState,
        topBar = {
            DashboardTopBar(
                selectedDate = selectedDate,
                viewType = viewType,
                isSyncing = isSyncing,
                onViewTypeChange = { viewModel.changeViewType(it) },
                onDateNavigate = { amount ->
                    val updated = selectedDate.clone() as Calendar
                    when (viewType) {
                        CalendarViewType.MONTH -> updated.add(Calendar.MONTH, amount)
                        CalendarViewType.WEEK -> updated.add(Calendar.WEEK_OF_YEAR, amount)
                        CalendarViewType.DAY -> updated.add(Calendar.DAY_OF_YEAR, amount)
                    }
                    viewModel.selectDate(updated)
                },
                onToggleLogs = { showSyncLogs = !showSyncLogs }
            )
        },
        floatingActionButton = {
            MorphingFloatingActionButton(
                onQuickAddClick = { viewModel.setShowQuickAddDialog(true) },
                onSyncClick = { source -> viewModel.startRemoteSync(source) }
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            val isWideScreen = maxWidth > 600.dp

            if (isWideScreen) {
                // Adaptive Canonical Tablet/Foldable Layout
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left Column: Interactive Grid views
                    Surface(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                            .padding(8.dp),
                        tonalElevation = 1.dp,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Box(modifier = Modifier.padding(12.dp)) {
                            MainCalendarGridView(
                                viewType = viewType,
                                selectedDate = selectedDate,
                                events = filteredEvents,
                                categories = categories,
                                onDateSelect = { viewModel.selectDate(it) }
                            )
                        }
                    }

                    // Right Column: Split into agenda rosters, active filters and cloud simulation status boards
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        // Category selection dashboard
                        CategoryFilterPanel(
                            categories = categories,
                            activeFilters = activeFilters,
                            onToggleFilter = { viewModel.toggleCategoryFilter(it) },
                            onConfigureColorsClick = { showColorCustomizerDialog = true }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (showSyncLogs) {
                            SyncLogsListView(
                                syncLogs = syncLogs,
                                onBackToAgenda = { showSyncLogs = false }
                            )
                        } else {
                            ScheduleAgendaListView(
                                selectedDate = selectedDate,
                                events = filteredEvents,
                                categories = categories,
                                onEventClick = { viewModel.setSelectedEvent(it) }
                            )
                        }
                    }
                }
            } else {
                // Mobile Vertical Layout
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.3f)
                            .padding(8.dp)
                    ) {
                        MainCalendarGridView(
                            viewType = viewType,
                            selectedDate = selectedDate,
                            events = filteredEvents,
                            categories = categories,
                            onDateSelect = { viewModel.selectDate(it) }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column {
                            CategoryFilterPanel(
                                categories = categories,
                                activeFilters = activeFilters,
                                onToggleFilter = { viewModel.toggleCategoryFilter(it) },
                                onConfigureColorsClick = { showColorCustomizerDialog = true }
                            )

                            if (showSyncLogs) {
                                SyncLogsListView(
                                    syncLogs = syncLogs,
                                    onBackToAgenda = { showSyncLogs = false }
                                )
                            } else {
                                ScheduleAgendaListView(
                                    selectedDate = selectedDate,
                                    events = filteredEvents,
                                    categories = categories,
                                    onEventClick = { viewModel.setSelectedEvent(it) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Detailed Event bottom modal sheet (spring-like animation behavior)
        selectedEvent?.let { event ->
            EventDetailSheet(
                event = event,
                categories = categories,
                onDismiss = { viewModel.setSelectedEvent(null) },
                onDelete = {
                    viewModel.deleteEventById(event.id)
                    viewModel.setSelectedEvent(null)
                }
            )
        }

        // Quick Smart Parsing natural dialog (Samsung S-grade scheduler)
        if (showQuickAddDialog) {
            SamsungQuickAddDialog(
                viewModel = viewModel,
                onDismiss = { viewModel.setShowQuickAddDialog(false) }
            )
        }

        // Custom Calendar Category Color Customizer Dialog
        if (showColorCustomizerDialog) {
            CalendarColorCustomizerDialog(
                categories = categories,
                onUpdateColor = { name, hex -> viewModel.updateCategoryColor(name, hex) },
                onDismiss = { showColorCustomizerDialog = false }
            )
        }
    }
}

// Scaffold-like custom Snackbar extension to avoid Compose core differences
@Composable
fun Scaffold(
    snackbarHostStatus: SnackbarHostState,
    topBar: @Composable () -> Unit,
    floatingActionButton: @Composable () -> Unit,
    contentWindowInsets: WindowInsets,
    content: @Composable (PaddingValues) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            topBar()
            Box(modifier = Modifier.weight(1f)) {
                content(PaddingValues(0.dp))
            }
        }

        // Floating action button layered on top
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 80.dp, end = 16.dp)
        ) {
            floatingActionButton()
        }

        // Host snackbar alerts in a clean floating position
        SnackbarHost(
            hostState = snackbarHostStatus,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

@Composable
fun DashboardTopBar(
    selectedDate: Calendar,
    viewType: CalendarViewType,
    isSyncing: Boolean,
    onViewTypeChange: (CalendarViewType) -> Unit,
    onDateNavigate: (Int) -> Unit,
    onToggleLogs: () -> Unit
) {
    val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    val formattedTitle = monthYearFormat.format(selectedDate.time)

    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Header year title (Medium weight display in Clean Minimalism)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = "App Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = formattedTitle,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 6.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = onToggleLogs) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "View sync reports",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Navigation buttons
                    IconButton(onClick = { onDateNavigate(-1) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                    IconButton(onClick = { onDateNavigate(1) }) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Forward")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // View modes segmented selector using spring physics transitions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CalendarViewType.values().forEach { type ->
                    val isSelected = viewType == type
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.05f else 0.95f,
                        animationSpec = TactileSpringSpec,
                        label = "SegmentScale"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .scale(scale)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                else Color.Transparent
                            )
                            .clickable { onViewTypeChange(type) }
                            .padding(vertical = 8.dp, horizontal = 12.dp)
                            .testTag("view_tab_${type.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = type.name,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 13.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// Extends modifiers for smooth scaling transitions
fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
)

@Composable
fun MainCalendarGridView(
    viewType: CalendarViewType,
    selectedDate: Calendar,
    events: List<Event>,
    categories: List<CalendarCategory> = emptyList(),
    onDateSelect: (Calendar) -> Unit
) {
    Crossfade(
        targetState = viewType,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "MainViewCrossfade"
    ) { currentView ->
        when (currentView) {
            CalendarViewType.MONTH -> MonthViewGrid(selectedDate, events, categories, onDateSelect)
            CalendarViewType.WEEK -> WeekViewGrid(selectedDate, events, categories, onDateSelect)
            CalendarViewType.DAY -> DayViewGrid(selectedDate, events, categories)
        }
    }
}

@Composable
fun MonthViewGrid(
    selectedDate: Calendar,
    events: List<Event>,
    categories: List<CalendarCategory> = emptyList(),
    onDateSelect: (Calendar) -> Unit
) {
    // Correct calendar calculations for month grid
    val calendar = selectedDate.clone() as Calendar
    calendar.set(Calendar.DAY_OF_MONTH, 1)

    val firstDayOfWeekIndex = calendar.get(Calendar.DAY_OF_WEEK) - 1 // 0-based index (Sunday=0 in standard logic)
    val totalDaysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

    val previousMonthCalendar = calendar.clone() as Calendar
    previousMonthCalendar.add(Calendar.MONTH, -1)
    val previousMonthMaxDays = previousMonthCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)

    val weekDays = listOf("S", "M", "T", "W", "T", "F", "S")

    Column(modifier = Modifier.fillMaxWidth()) {
        // Week headers row
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            weekDays.forEach { day ->
                Text(
                    text = day,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Divider(modifier = Modifier.padding(bottom = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

        // Combine cells (preceding days from previous month, current month days, succeeding trailing empty cells)
        val cells = mutableListOf<CalendarDateCell>()

        // 1. Padding from previous month
        for (i in firstDayOfWeekIndex - 1 downTo 0) {
            val cellDate = previousMonthCalendar.clone() as Calendar
            val dayVal = previousMonthMaxDays - i
            cellDate.set(Calendar.DAY_OF_MONTH, dayVal)
            cells.add(CalendarDateCell(dayVal, isCurrentMonth = false, matchingDate = cellDate))
        }

        // 2. Current Month
        for (i in 1..totalDaysInMonth) {
            val cellDate = calendar.clone() as Calendar
            cellDate.set(Calendar.DAY_OF_MONTH, i)
            cells.add(CalendarDateCell(i, isCurrentMonth = true, matchingDate = cellDate))
        }

        // 3. Trailing cells to fill complete standard 6-row (42 cells) grid
        val remainingCells = 42 - cells.size
        val nextMonthCalendar = calendar.clone() as Calendar
        nextMonthCalendar.add(Calendar.MONTH, 1)
        for (i in 1..remainingCells) {
            val cellDate = nextMonthCalendar.clone() as Calendar
            cellDate.set(Calendar.DAY_OF_MONTH, i)
            cells.add(CalendarDateCell(i, isCurrentMonth = false, matchingDate = cellDate))
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            userScrollEnabled = false
        ) {
            items(cells.size) { index ->
                val cell = cells[index]
                val dayEvents = getEventsForDate(cell.matchingDate, events)

                MonthDayCellView(
                    cell = cell,
                    isSelected = isSameDay(cell.matchingDate, selectedDate),
                    dayEvents = dayEvents,
                    categories = categories,
                    onClick = { onDateSelect(cell.matchingDate) }
                )
            }
        }
    }
}

data class CalendarDateCell(
    val dayNumber: Int,
    val isCurrentMonth: Boolean,
    val matchingDate: Calendar
)

@Composable
fun MonthDayCellView(
    cell: CalendarDateCell,
    isSelected: Boolean,
    dayEvents: List<Event>,
    categories: List<CalendarCategory> = emptyList(),
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "DayScale"
    )

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(CircleShape)
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isToday(cell.matchingDate) -> MaterialTheme.colorScheme.secondaryContainer
                    else -> Color.Transparent
                }
            )
            .clickable { onClick() }
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = cell.dayNumber.toString(),
                fontWeight = if (isSelected || isToday(cell.matchingDate)) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.sp,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    isToday(cell.matchingDate) -> MaterialTheme.colorScheme.onSecondaryContainer
                    cell.isCurrentMonth -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                }
            )

            if (dayEvents.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                val dotColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else if (isToday(cell.matchingDate)) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    getColorFromHex(getCategoryColorHex(dayEvents.first().categoryName, categories))
                }
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
        }
    }
}

@Composable
fun WeekViewGrid(
    selectedDate: Calendar,
    events: List<Event>,
    categories: List<CalendarCategory> = emptyList(),
    onDateSelect: (Calendar) -> Unit
) {
    // Current week representation starting on Sunday
    val startOfWeek = selectedDate.clone() as Calendar
    val dayOfWeek = startOfWeek.get(Calendar.DAY_OF_WEEK)
    startOfWeek.add(Calendar.DAY_OF_YEAR, -(dayOfWeek - 1))

    val days = (0..6).map {
        val calculated = startOfWeek.clone() as Calendar
        calculated.add(Calendar.DAY_OF_YEAR, it)
        calculated
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            days.forEach { day ->
                val dayName = SimpleDateFormat("E", Locale.getDefault()).format(day.time)
                val isSelected = isSameDay(day, selectedDate)
                val dayEvents = getEventsForDate(day, events)

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .padding(2.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onDateSelect(day) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = dayName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = day.get(Calendar.DAY_OF_MONTH).toString(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Quick count dot
                        if (dayEvents.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.onPrimary
                                        else getColorFromHex(getCategoryColorHex(dayEvents.first().categoryName, categories))
                                    )
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Week list agenda brief preview
        Text(
            text = "Active Week Schedule",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
        )

        val weekEventsList = events.filter { ev ->
            val evCal = Calendar.getInstance().apply { timeInMillis = ev.startTime }
            evCal.get(Calendar.WEEK_OF_YEAR) == selectedDate.get(Calendar.WEEK_OF_YEAR) &&
                    evCal.get(Calendar.YEAR) == selectedDate.get(Calendar.YEAR)
        }

        if (weekEventsList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No schedules booked for this calendar week.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(modifier = Modifier.height(200.dp)) {
                items(weekEventsList) { event ->
                    val dayFormat = SimpleDateFormat("EEEE d", Locale.getDefault())
                    val evDate = dayFormat.format(Date(event.startTime))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(28.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(getColorFromHex(getCategoryColorHex(event.categoryName, categories)))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = event.title,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$evDate • ${formatTimeRange(event.startTime, event.endTime)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DayViewGrid(selectedDate: Calendar, events: List<Event>, categories: List<CalendarCategory> = emptyList()) {
    val dayEvents = getEventsForDate(selectedDate, events)

    // Displays full timelines for the active select date (8 AM to 8 PM timeline grid)
    val hourBlocks = (8..21).toList()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Hourly Breakdown",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(hourBlocks) { hour ->
                val formattedHour = if (hour > 12) "${hour - 12} PM" else if (hour == 12) "12 PM" else "$hour AM"
                val hourEvents = dayEvents.filter { ev ->
                    val evCal = Calendar.getInstance().apply { timeInMillis = ev.startTime }
                    evCal.get(Calendar.HOUR_OF_DAY) == hour
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = formattedHour,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(60.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    if (hourEvents.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                "No schedules booked",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                    } else {
                        Column(modifier = Modifier.weight(1f)) {
                            hourEvents.forEach { ev ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 2.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = getColorFromHex(getCategoryColorHex(ev.categoryName, categories)).copy(alpha = 0.15f)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(getColorFromHex(getCategoryColorHex(ev.categoryName, categories)))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = ev.title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (ev.location.isNotEmpty()) {
                                                Text(
                                                    text = "📍 ${ev.location}",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryFilterPanel(
    categories: List<CalendarCategory>,
    activeFilters: Map<String, Boolean>,
    onToggleFilter: (String) -> Unit,
    onConfigureColorsClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Show Calendars",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onConfigureColorsClick,
                    modifier = Modifier.size(24.dp).testTag("configure_colors_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "Edit Colors",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                categories.forEach { category ->
                    val isActive = activeFilters[category.name] != false
                    val catColor = getColorFromHex(category.colorHex)

                    FilterChip(
                        selected = isActive,
                        onClick = { onToggleFilter(category.name) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(catColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(category.name, fontSize = 11.sp)
                            }
                        },
                        modifier = Modifier.scale(0.9f)
                    )
                }
            }
        }
    }
}

@Composable
fun ScheduleAgendaListView(
    selectedDate: Calendar,
    events: List<Event>,
    categories: List<CalendarCategory> = emptyList(),
    onEventClick: (Event) -> Unit
) {
    val dayEvents = getEventsForDate(selectedDate, events)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = "Schedule Agenda • ${dayEvents.size} Events",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
        )

        if (dayEvents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.EventNote,
                        contentDescription = "Cleared Desk",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your schedule is beautifully clear today!",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Staggered Entrance implementation sequence
                items(dayEvents) { event ->
                    var isVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(key1 = event.id) {
                        isVisible = true
                    }

                    AnimatedVisibility(
                        visible = isVisible,
                        enter = slideInVertically(
                            initialOffsetY = { 50 },
                            animationSpec = spring(stiffness = Spring.StiffnessMedium)
                        ) + fadeIn(),
                        exit = fadeOut()
                    ) {
                        EventCardItem(event = event, categories = categories, onClick = { onEventClick(event) })
                    }
                }
            }
        }
    }
}

@Composable
fun EventCardItem(event: Event, categories: List<CalendarCategory> = emptyList(), onClick: () -> Unit) {
    // Elegant dynamic pastel card pairings matching the Clean Minimalism model or user-assigned custom category colors!
    val baseColor = categories.find { it.name.equals(event.categoryName, ignoreCase = true) }?.let { getColorFromHex(it.colorHex) }
        ?: when (event.categoryName) {
            "Work" -> Color(0xFF1E88E5)
            "Fitness" -> Color(0xFF8E24AA)
            "Personal" -> Color(0xFF43A047)
            "Family" -> Color(0xFFF4511E)
            else -> MaterialTheme.colorScheme.primary
        }

    val cardBg = baseColor.copy(alpha = 0.12f)
    val cardOnBg = baseColor

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("event_card_${event.id}"),
        colors = CardDefaults.cardColors(
            containerColor = cardBg
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column: Time range sequence
            val formatHourMin = SimpleDateFormat("HH:mm", Locale.getDefault())
            val startStr = formatHourMin.format(Date(event.startTime))
            val endStr = formatHourMin.format(Date(event.endTime))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(48.dp)
            ) {
                Text(
                    text = startStr,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = cardOnBg
                )
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(12.dp)
                        .background(cardOnBg.copy(alpha = 0.2f))
                        .padding(vertical = 1.dp)
                )
                Text(
                    text = endStr,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = cardOnBg.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Main Info Column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = cardOnBg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (event.location.isNotEmpty()) {
                    Text(
                        text = event.location,
                        fontSize = 11.sp,
                        color = cardOnBg.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                
                // Badges/Tags row
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val labelBg = Color.White.copy(alpha = 0.5f)
                    // Category Tag
                    Surface(
                        color = labelBg,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = event.categoryName.uppercase(Locale.getDefault()),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = cardOnBg,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Sync Source Badge on the far right
            Surface(
                color = Color.White.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = event.syncSource,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = cardOnBg,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

// Staggered list view showing synchronization sync history logs
@Composable
fun SyncLogsListView(
    syncLogs: List<SyncLog>,
    onBackToAgenda: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Cloud Sync & Conflict Log",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = onBackToAgenda) {
                Icon(Icons.Default.Close, contentDescription = "Close logs")
            }
        }

        if (syncLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No sync traces yet. Pick 'Sync accounts' from the FAB to execute sync simulations.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(syncLogs) { log ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Channel: ${log.source} Cloud",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Badge(
                                    containerColor = if (log.status == "SUCCESS") Color(0xFF4CAF50) else Color(0xFFFF9800),
                                    contentColor = Color.White
                                ) {
                                    Text(log.status, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Time: ${SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()).format(Date(log.syncTime))}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = log.details,
                                fontSize = 11.sp,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

// Samsung-grade spring physics morphing floating action button menu
@Composable
fun MorphingFloatingActionButton(
    onQuickAddClick: () -> Unit,
    onSyncClick: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // Physics spring animations drove sizes and shapes dynamically
    val width by animateDpAsState(
        targetValue = if (expanded) 240.dp else 56.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 160f),
        label = "FabWidth"
    )

    val height by animateDpAsState(
        targetValue = if (expanded) 110.dp else 56.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 160f),
        label = "FabHeight"
    )

    val shapePercent by animateIntAsState(
        targetValue = if (expanded) 12 else 50,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "FabShape"
    )

    Card(
        modifier = Modifier
            .size(width = width, height = height)
            .testTag("morph_fab"),
        shape = RoundedCornerShape(shapePercent),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        if (!expanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { expanded = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Open action tray",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Samsung S-Scheduler Menu",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    IconButton(
                        onClick = { expanded = false },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Collapse menu",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Divider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))

                // Multi-Action layout row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick add dialog button
                    IconButton(onClick = {
                        expanded = false
                        onQuickAddClick()
                    }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Quicksmart Natural language parse",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Simulated sync shortcuts (Google, iCloud, Samsung)
                    IconButton(onClick = {
                        expanded = false
                        onSyncClick("Google")
                    }) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = "GCal Cloud Sync Integration",
                            tint = Color(0xFF4285F4),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(onClick = {
                        expanded = false
                        onSyncClick("iCloud")
                    }) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = "iCloud CalDAV Integration",
                            tint = Color(0xFF007AFF),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(onClick = {
                        expanded = false
                        onSyncClick("Samsung")
                    }) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Samsung Cloud Integration",
                            tint = Color(0xFF3F51B5),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EventDetailSheet(
    event: Event,
    categories: List<CalendarCategory> = emptyList(),
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(getColorFromHex(getCategoryColorHex(event.categoryName, categories)))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = event.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Account: ${event.syncSource} Calendar Folder",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (event.description.isNotEmpty()) {
                    Text(
                        text = "Notes:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = event.description,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 12.dp, top = 2.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date(event.startTime)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                    Spacer(modifier = Modifier.width(24.dp))
                    Text(
                        text = "Time: ${formatTimeRange(event.startTime, event.endTime)}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (event.location.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = "Location",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = event.location,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Conflict Status: Fully Sync-Ready, Offline-Stored locally.",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Back")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Event")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Delete")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun SamsungQuickAddDialog(
    viewModel: CalendarViewModel,
    onDismiss: () -> Unit
) {
    var rawText by remember { mutableStateOf("") }
    val aiSuggestions by viewModel.aiSuggestions.collectAsState()
    val isGeneratingSuggestions by viewModel.isGeneratingSuggestions.collectAsState()

    // Real-time debounce: triggers predictions 500ms after user pauses typing
    LaunchedEffect(rawText) {
        if (rawText.trim().isNotEmpty()) {
            kotlinx.coroutines.delay(500)
            viewModel.fetchAiSuggestions(rawText)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = "Samsung S-Add tool",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Samsung S-Scheduler Quick Add", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessLow))
            ) {
                Text(
                    text = "Type natural language statements. Our scheduling engine automatically parses details, timeframes, and folders in real-time.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    placeholder = {
                        Text(
                            "e.g. Lunch with Mom on Friday at 12:30pm in family",
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_add_text_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Real-time AI Suggestions Block
                if (rawText.trim().isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "✨ AI Real-Time Suggestions",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        if (isGeneratingSuggestions) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (aiSuggestions.isEmpty() && !isGeneratingSuggestions) {
                        Text(
                            text = "Typing to predict routines & slots...",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(aiSuggestions) { suggestion ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.addSuggestedEvent(suggestion)
                                            onDismiss()
                                        }
                                        .testTag("suggestion_chip_${suggestion.title.lowercase().replace(" ", "_")}"),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = suggestion.title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            Text(
                                                text = suggestion.category,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = String.format(Locale.getDefault(), "⏰ %02d:%02d", suggestion.hour, suggestion.minute),
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (suggestion.location.isNotEmpty()) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "📍 ${suggestion.location}",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }

                                        Text(
                                            text = "💡 ${suggestion.justification}",
                                            fontSize = 9.sp,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Text(
                    text = "Engine Syntax Helpers:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "• 'Standup work today at 10am' -> Category: 'Work'\n" +
                            "• 'Workout at gym tomorrow at 5pm' -> Category: 'Fitness'\n" +
                            "• 'Dinner with wife tomorrow at 7:30pm' -> Category: 'Personal'\n" +
                            "• No specified time defaults to next available hour slot.",
                    fontSize = 11.sp,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (rawText.isNotEmpty()) {
                        viewModel.addQuickParsedEvent(rawText)
                        onDismiss()
                    }
                },
                enabled = rawText.isNotEmpty(),
                modifier = Modifier.testTag("quick_add_save_btn")
            ) {
                Text("Instant Schedule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

// Global Help utility methods
fun isToday(calendar: Calendar): Boolean {
    val today = Calendar.getInstance()
    return isSameDay(calendar, today)
}

fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH) &&
            cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
            cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
}

fun getEventsForDate(date: Calendar, allEvents: List<Event>): List<Event> {
    return allEvents.filter { event ->
        val evCal = Calendar.getInstance().apply { timeInMillis = event.startTime }
        isSameDay(date, evCal)
    }
}

fun getCategoryColorHex(categoryName: String, categories: List<CalendarCategory> = emptyList()): String {
    val matched = categories.find { it.name.equals(categoryName, ignoreCase = true) }
    if (matched != null) {
        return matched.colorHex
    }
    return when (categoryName) {
        "Work" -> "#F44336"
        "Personal" -> "#4CAF50"
        "Family" -> "#FF9800"
        "Fitness" -> "#9C27B0"
        else -> "#3F51B5" // Local
    }
}

fun getColorFromHex(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        Color(0xFF3F51B5)
    }
}

fun formatTimeRange(startMs: Long, endMs: Long): String {
    val formatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return "${formatter.format(Date(startMs))} - ${formatter.format(Date(endMs))}"
}

@Composable
fun CalendarColorCustomizerDialog(
    categories: List<CalendarCategory>,
    onUpdateColor: (categoryName: String, colorHex: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategoryName by remember { mutableStateOf<String?>(null) }

    val colorsPalette = listOf(
        "#F44336" to "Tomato Red",
        "#E91E63" to "Warm Pink",
        "#9C27B0" to "Orchid Purple",
        "#673AB7" to "Royal Violet",
        "#3F51B5" to "Navy Indigo",
        "#2196F3" to "Sky Blue",
        "#03A9F4" to "Bright Teal",
        "#00BCD4" to "Turquoise",
        "#009688" to "Ocean Green",
        "#4CAF50" to "Forest Green",
        "#8BC34A" to "Lime Green",
        "#FFEB3B" to "Vibrant Yellow",
        "#FFC107" to "Amber Gold",
        "#FF9800" to "Honey Orange",
        "#FF5722" to "Sunset Red",
        "#607D8B" to "Slate Gray"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                     imageVector = Icons.Default.Palette,
                     contentDescription = "Color Customizer",
                     tint = MaterialTheme.colorScheme.primary,
                     modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Configure Calendar Colors", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessLow))
            ) {
                Text(
                    text = "Assign custom colors to specific folders or event tags to organize your dashboard views at a glance.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { category ->
                        val isEditing = selectedCategoryName == category.name
                        val catColorHex = category.colorHex
                        val catColor = getColorFromHex(catColorHex)

                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCategoryName = if (isEditing) null else category.name }
                                .testTag("category_card_${category.name}"),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = if (isEditing) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(catColor)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = category.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Source: ${category.source}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isEditing) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "Expand Colors Logo",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                if (isEditing) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Select Palette Accent:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )

                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        val rows = colorsPalette.chunked(4)
                                        rows.forEach { rowTiles ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                rowTiles.forEach { (hexVal, colorName) ->
                                                    val isSelected = catColorHex.lowercase() == hexVal.lowercase()
                                                    Box(
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(getColorFromHex(hexVal))
                                                            .clickable { 
                                                                onUpdateColor(category.name, hexVal)
                                                            }
                                                            .padding(2.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (isSelected) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = "Selected",
                                                                tint = Color.White,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                // Pad row if it has less than 4 items
                                                if (rowTiles.size < 4) {
                                                    repeat(4 - rowTiles.size) {
                                                        Spacer(modifier = Modifier.size(36.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss", fontWeight = FontWeight.Bold)
            }
        }
    )
}
