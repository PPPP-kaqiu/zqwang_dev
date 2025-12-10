package com.flashpick.app.gallery

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.flashpick.app.R
import com.flashpick.app.ui.theme.FlashPickTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class VideoGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FlashPickTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GalleryApp()
                }
            }
        }
    }
}

// --- Navigation State ---
sealed class GalleryScreen {
    object Calendar : GalleryScreen()
    data class DayDetail(val dateStr: String) : GalleryScreen()
}

@Composable
fun GalleryApp() {
    var currentScreen by remember { mutableStateOf<GalleryScreen>(GalleryScreen.Calendar) }
    val context = LocalContext.current
    
    // Scan for dates with data
    val availableDates = remember {
        val rootDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "FlashPick")
        if (rootDir.exists()) {
            rootDir.listFiles()
                ?.filter { it.isDirectory && it.name.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
                ?.map { it.name }
                ?.toSet() ?: emptySet()
        } else {
            emptySet()
        }
    }

    when (val screen = currentScreen) {
        is GalleryScreen.Calendar -> {
            CalendarView(
                availableDates = availableDates,
                onDateSelected = { date -> currentScreen = GalleryScreen.DayDetail(date) }
            )
        }
        is GalleryScreen.DayDetail -> {
            DayDetailView(
                dateStr = screen.dateStr,
                onBack = { currentScreen = GalleryScreen.Calendar }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarView(
    availableDates: Set<String>,
    onDateSelected: (String) -> Unit
) {
    val calendar = remember { Calendar.getInstance() }
    var currentMonth by remember { mutableStateOf(calendar.clone() as Calendar) }
    
    val daysInMonth = remember(currentMonth.timeInMillis) {
        val c = currentMonth.clone() as Calendar
        c.set(Calendar.DAY_OF_MONTH, 1)
        val days = mutableListOf<Date?>()
        
        // Empty slots for start of week
        val dayOfWeek = c.get(Calendar.DAY_OF_WEEK) // Sun=1
        for (i in 1 until dayOfWeek) {
            days.add(null)
        }
        
        // Days
        val maxDays = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (i in 1..maxDays) {
            days.add(c.time)
            c.add(Calendar.DAY_OF_MONTH, 1)
        }
        days
    }

    val monthFormatter = SimpleDateFormat("yyyy MMMM", Locale.getDefault())
    val dayFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Memory Calendar") }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Month Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    val newCal = currentMonth.clone() as Calendar
                    newCal.add(Calendar.MONTH, -1)
                    currentMonth = newCal
                }) { Text("< Prev") }
                
                Text(
                    text = monthFormatter.format(currentMonth.time),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                TextButton(onClick = {
                    val newCal = currentMonth.clone() as Calendar
                    newCal.add(Calendar.MONTH, 1)
                    currentMonth = newCal
                }) { Text("Next >") }
            }

            // Weekday Headers
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                    Text(
                        text = day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            // Calendar Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxSize()
            ) {
                items(daysInMonth) { date ->
                    if (date != null) {
                        val dateStr = dayFormatter.format(date)
                        val hasData = availableDates.contains(dateStr)
                        val isToday = dayFormatter.format(Date()) == dateStr
                        
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        hasData -> MaterialTheme.colorScheme.primaryContainer
                                        isToday -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable(enabled = hasData) { onDateSelected(dateStr) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = SimpleDateFormat("d", Locale.getDefault()).format(date),
                                    fontWeight = if (hasData) FontWeight.Bold else FontWeight.Normal,
                                    color = if (hasData) MaterialTheme.colorScheme.onPrimaryContainer 
                                           else MaterialTheme.colorScheme.onSurface
                                )
                                if (hasData) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.aspectRatio(1f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailView(
    dateStr: String,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Video, 1 = Audio
    val tabs = listOf("Video", "Audio")
    
    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(dateStr) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            val context = LocalContext.current
            val fileList = remember(dateStr, selectedTab) {
                val rootDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "FlashPick")
                val dayDir = File(rootDir, dateStr)
                val typeDir = File(dayDir, if (selectedTab == 0) "video" else "audio")
                
                if (typeDir.exists()) {
                    typeDir.listFiles()
                        ?.filter { it.isFile && !it.name.contains("_thumb") } // Exclude thumbnails
                        ?.sortedByDescending { it.lastModified() }
                        ?.toList() ?: emptyList()
                } else {
                    emptyList()
                }
            }

            if (fileList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No ${tabs[selectedTab]} records found")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(fileList) { file ->
                        MediaListItem(file = file)
                    }
                }
            }
        }
    }
}

@Composable
fun MediaListItem(file: File) {
    val context = LocalContext.current
    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // Open file logic
                val uri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    val mimeType = when (file.extension.lowercase(Locale.US)) {
                        "mp4" -> "video/*"
                        "m4a" -> "audio/*"
                        else -> "*/*"
                    }
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatter.format(Date(file.lastModified())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
