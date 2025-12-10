package com.flashpick.app.ui.screens.home

import android.app.Application
import android.os.Environment
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.flashpick.app.data.AppDatabase
import com.flashpick.app.data.model.AppUsageStat
import com.flashpick.app.data.model.DailySummary
import com.flashpick.app.data.model.VideoRecord
import com.flashpick.app.data.repository.VlmRepository
import com.flashpick.app.worker.VideoAnalysisWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.videoRecordDao()
    private val summaryDao = db.dailySummaryDao()
    private val workManager = WorkManager.getInstance(application)

    private val _selectedDate = MutableStateFlow(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    private val _records = MutableStateFlow<List<VideoRecord>>(emptyList())
    val records: StateFlow<List<VideoRecord>> = _records.asStateFlow()

    private val _selectedRecord = MutableStateFlow<VideoRecord?>(null)
    val selectedRecord: StateFlow<VideoRecord?> = _selectedRecord.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _usageStats = MutableStateFlow<List<AppUsageStat>>(emptyList())
    val usageStats: StateFlow<List<AppUsageStat>> = _usageStats.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val dailySummary: StateFlow<String?> = selectedDate
        .flatMapLatest { date -> summaryDao.getSummary(date) }
        .map { it?.content }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isGeneratingSummary = MutableStateFlow(false)
    val isGeneratingSummary: StateFlow<Boolean> = _isGeneratingSummary.asStateFlow()

    init {
        // Observe records for the current date or search query
        viewModelScope.launch {
            // Combine flows or just react to both
            launch {
                selectedDate.collectLatest { 
                    if (_searchQuery.value.isEmpty()) fetchRecords(it)
                }
            }
            launch {
                searchQuery.collectLatest { query ->
                    if (query.isNotEmpty()) {
                        searchRecords(query)
                    } else {
                        fetchRecords(selectedDate.value)
                    }
                }
            }
        }
        
        // Load insights initially (e.g. for the last 7 days)
        loadInsights()
    }

    fun onDateSelected(date: String) {
        _selectedDate.value = date
        _searchQuery.value = "" // Clear search when date is selected
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onRecordSelected(record: VideoRecord?) {
        _selectedRecord.value = record
    }

    fun updateRecord(record: VideoRecord, newTitle: String, newSummary: String, newUrl: String) {
        viewModelScope.launch {
            dao.updateRecordDetails(record.id, newTitle, newSummary, newUrl)
            // Update selected record to reflect changes immediately in UI
            _selectedRecord.value = record.copy(title = newTitle, summary = newSummary, url = newUrl)
        }
    }

    private suspend fun searchRecords(query: String) {
        dao.searchRecords(query).collectLatest { dbList ->
            _records.value = dbList
        }
    }

    private suspend fun fetchRecords(dateStr: String) {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = format.parse(dateStr)
        if (date != null) {
            val start = date.time
            val end = start + 24 * 60 * 60 * 1000 - 1
            dao.getRecordsByDateRange(start, end).collectLatest { dbList ->
                _records.value = dbList
            }
        }
    }

    fun loadInsights() {
        val oneWeekAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000
        viewModelScope.launch {
            dao.getAppUsageStats(oneWeekAgo).collectLatest {
                _usageStats.value = it
            }
        }
    }

    fun syncFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val rootDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "FlashPick")
                var addedCount = 0
                
                if (rootDir.exists()) {
                    rootDir.listFiles()?.forEach { dayDir ->
                        if (dayDir.isDirectory) {
                            val videoDir = File(dayDir, "video")
                            if (videoDir.exists()) {
                                videoDir.listFiles { f -> f.extension == "mp4" }?.forEach { videoFile ->
                                    val existing = dao.getByPath(videoFile.absolutePath)
                                    if (existing == null) {
                                        val thumbFile = File(videoFile.parent, "${videoFile.nameWithoutExtension}_thumb.jpg")
                                        val thumbPath = if (thumbFile.exists()) thumbFile.absolutePath else ""
                                        
                                        val newRecord = VideoRecord(
                                            filePath = videoFile.absolutePath,
                                            thumbnailPath = thumbPath,
                                            createdAt = videoFile.lastModified(),
                                            durationMs = 0,
                                            sizeBytes = videoFile.length(),
                                            sourcePackage = "Restored",
                                            appName = "Restored"
                                        )
                                        dao.insert(newRecord)
                                        addedCount++
                                    }
                                }
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    val msg = if (addedCount > 0) "已找回 ${addedCount} 条记忆" else "没有发现新文件"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun generateDailySummary() {
        if (_records.value.isEmpty()) return
        
        viewModelScope.launch(Dispatchers.IO) {
            _isGeneratingSummary.value = true
            val records = _records.value
            val content = StringBuilder()
            content.append("今天是 ${selectedDate.value}，用户记录了 ${records.size} 条内容。\n")
            records.forEachIndexed { index, record ->
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(record.createdAt))
                content.append("${index + 1}. [$time] ${record.appName}: ${record.title ?: "未知"} - ${record.summary ?: ""}\n")
            }
            
            val prompt = "请根据以下用户的一天屏幕记录，生成一段简短的每日总结（100字以内），概括用户今天的主要活动和兴趣点。请用第二人称“你”。\n\n$content"
            
            val summary = VlmRepository.chatWithText(prompt)
            
            _isGeneratingSummary.value = false
            
            // Save to DB
            if (summary.isNotEmpty()) {
                val entity = DailySummary(
                    date = selectedDate.value,
                    content = summary,
                    updatedAt = System.currentTimeMillis()
                )
                summaryDao.insert(entity)
            }
        }
    }

    fun reAnalyze(record: VideoRecord, showToast: Boolean = true) {
        val inputData = Data.Builder()
            .putString("video_path", record.filePath)
            .putString("source_package", record.sourcePackage)
            .putString("app_name", record.appName)
            .putLong("trigger_time_ms", 0L)
            .putString("url", record.url)
            .build()

        val request = OneTimeWorkRequestBuilder<VideoAnalysisWorker>()
            .setInputData(inputData)
            .build()

        workManager.enqueue(request)
        if (showToast) {
            Toast.makeText(getApplication(), "已触发 AI 重新分析...", Toast.LENGTH_SHORT).show()
        }
    }

    fun analyzeAll() {
        viewModelScope.launch(Dispatchers.IO) {
            val unprocessed = dao.getUnprocessedRecords()
            if (unprocessed.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "所有记忆都已分析完成", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "开始后台分析 ${unprocessed.size} 条记忆...", Toast.LENGTH_SHORT).show()
            }
            
            unprocessed.forEach { record ->
                reAnalyze(record, showToast = false)
            }
        }
    }
}

