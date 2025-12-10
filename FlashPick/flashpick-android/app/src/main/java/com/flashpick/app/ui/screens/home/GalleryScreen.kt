package com.flashpick.app.ui.screens.home

import android.app.Application
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flashpick.app.overlay.OverlayService
import com.flashpick.app.ui.components.*
import com.flashpick.app.ui.screens.detail.NoteDetailScreen
import com.flashpick.app.ui.screens.settings.InsightsSheet
import com.flashpick.app.ui.screens.settings.SettingsSheet
import com.flashpick.app.ui.screens.settings.UserManualSheet

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun GalleryScreen(
    runtimeGranted: Boolean,
    overlayGranted: Boolean,
    accessibilityEnabled: Boolean,
    recorderPermissionGranted: Boolean,
    onRequestRuntime: () -> Unit,
    onRequestOverlay: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onRequestRecorder: () -> Unit,
    onManageWhitelist: () -> Unit,
    onUpdateCaptureWindow: (Long, Long) -> Unit,
    onUpdateOverlayDebug: (Int, Int) -> Unit,
    viewModel: GalleryViewModel = viewModel()
) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val records by viewModel.records.collectAsState()
    val selectedRecord by viewModel.selectedRecord.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val dailySummary by viewModel.dailySummary.collectAsState()
    val isGeneratingSummary by viewModel.isGeneratingSummary.collectAsState()
    
    // Settings Sheet State
    var showSettings by remember { mutableStateOf(false) }
    var showInsights by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    
    val sheetState = rememberModalBottomSheetState()
    val insightsSheetState = rememberModalBottomSheetState()
    val manualSheetState = rememberModalBottomSheetState()

    val usageStats by viewModel.usageStats.collectAsState()

    SharedTransitionLayout {
        AnimatedContent(
            targetState = selectedRecord,
            transitionSpec = {
                if (targetState != null) {
                    slideInHorizontally(spring(stiffness = Spring.StiffnessLow)) { it } + fadeIn(spring(stiffness = Spring.StiffnessLow)) togetherWith
                    slideOutHorizontally(spring(stiffness = Spring.StiffnessLow)) { -it / 3 } + fadeOut(spring(stiffness = Spring.StiffnessLow))
                } else {
                    slideInHorizontally(spring(stiffness = Spring.StiffnessLow)) { -it / 3 } + fadeIn(spring(stiffness = Spring.StiffnessLow)) togetherWith
                    slideOutHorizontally(spring(stiffness = Spring.StiffnessLow)) { it } + fadeOut(spring(stiffness = Spring.StiffnessLow))
                }
            },
            label = "GalleryNavigation"
        ) { record ->
            if (record != null) {
                NoteDetailScreen(
                    record = record,
                    onBack = { viewModel.onRecordSelected(null) },
                    onReAnalyze = { viewModel.reAnalyze(it) },
                    onUpdateRecord = { title, summary, url -> viewModel.updateRecord(record, title, summary, url) },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@AnimatedContent
                )
            } else {
                Scaffold(
                    containerColor = Color(0xFFF8F9FA),
                    topBar = {
                        Column(modifier = Modifier.background(Color.White)) {
                             GallerySearchBar(
                                query = searchQuery,
                                onQueryChange = { viewModel.onSearchQueryChanged(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            WeeklyCalendarStrip(
                                selectedDate = selectedDate,
                                onDateSelected = { viewModel.onDateSelected(it) },
                                onSettingsClick = { showSettings = true },
                                onInsightsClick = { 
                                    viewModel.loadInsights()
                                    showInsights = true 
                                }
                            )
                        }
                    }
                ) { padding ->
                    Box(modifier = Modifier.padding(padding)) {
                        if (records.isEmpty()) {
                            EmptyState(if (searchQuery.isNotEmpty()) "未找到相关记忆" else "今日暂无记忆")
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                item {
                                    DailySummaryCard(
                                        summary = dailySummary,
                                        isGenerating = isGeneratingSummary,
                                        onGenerate = { viewModel.generateDailySummary() }
                                    )
                                }
                                items(
                                    items = records,
                                    key = { it.id },
                                    contentType = { "record" }
                                ) { recordItem ->
                                    MemoryCard(
                                        record = recordItem,
                                        onClick = { viewModel.onRecordSelected(recordItem) },
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                        animatedVisibilityScope = this@AnimatedContent
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = sheetState,
            containerColor = Color.White
        ) {
            SettingsSheet(
                runtimeGranted, overlayGranted, accessibilityEnabled, recorderPermissionGranted,
                onRequestRuntime, onRequestOverlay, onOpenAccessibility, onRequestRecorder,
                onManageWhitelist, onUpdateCaptureWindow, 
                onSyncFiles = { viewModel.syncFiles() },
                onAnalyzeAll = { viewModel.analyzeAll() },
                onResetOverlay = {
                    val context = viewModel.getApplication<Application>()
                    val intent = Intent(context, OverlayService::class.java)
                    context.stopService(intent)
                    context.getSharedPreferences("flashpick_overlay_prefs", android.content.Context.MODE_PRIVATE)
                        .edit().clear().apply()
                    if (android.provider.Settings.canDrawOverlays(context)) {
                        ContextCompat.startForegroundService(context, intent)
                    }
                    Toast.makeText(context, "位置已重置", Toast.LENGTH_SHORT).show()
                },
                onShowManual = { 
                    showSettings = false
                    showManual = true 
                },
                onUpdateOverlayDebug = onUpdateOverlayDebug
            )
        }
    }

    if (showManual) {
        ModalBottomSheet(
            onDismissRequest = { showManual = false },
            sheetState = manualSheetState,
            containerColor = Color.White
        ) {
            UserManualSheet()
        }
    }

    if (showInsights) {
        ModalBottomSheet(
            onDismissRequest = { showInsights = false },
            sheetState = insightsSheetState,
            containerColor = Color.White
        ) {
            InsightsSheet(stats = usageStats)
        }
    }
}

