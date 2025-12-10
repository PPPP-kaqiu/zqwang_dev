package com.flashpick.app.ui.screens.detail

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.flashpick.app.data.model.VideoRecord
import com.flashpick.app.ui.components.AppInfoCache
import com.flashpick.app.ui.components.FullScreenImageViewer
import com.flashpick.app.ui.components.VideoPlayer
import com.flashpick.app.utils.ShareUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun NoteDetailScreen(
    record: VideoRecord, 
    onBack: () -> Unit,
    onReAnalyze: (VideoRecord) -> Unit,
    onUpdateRecord: (String, String, String) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isPlaying by remember { mutableStateOf(false) }
    var showFullImage by remember { mutableStateOf<String?>(null) }
    
    // Edit State
    var isEditing by remember { mutableStateOf(false) }
    var titleText by remember(record.title) { mutableStateOf(record.title ?: "") }
    var summaryText by remember(record.summary) { mutableStateOf(record.summary ?: "") }
    var urlText by remember(record.url) { mutableStateOf(record.url ?: "") }

    // Use Cache to prevent Main Thread Jank during scroll
    var appName by remember { mutableStateOf(AppInfoCache.getCachedName(record.sourcePackage) ?: record.sourcePackage) }
    
    LaunchedEffect(record.sourcePackage) {
        if (AppInfoCache.getCachedName(record.sourcePackage) == null) {
            withContext(Dispatchers.IO) {
                val name = AppInfoCache.resolveName(context, record.sourcePackage)
                withContext(Dispatchers.Main) {
                    appName = name
                }
            }
        }
    }
    val timeStr = remember(record.createdAt) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(record.createdAt))
    }

    if (showFullImage != null) {
        FullScreenImageViewer(imagePath = showFullImage!!, onDismiss = { showFullImage = null })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(appName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(timeStr, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Edit/Save Button
                    IconButton(onClick = { 
                        if (isEditing) {
                             onUpdateRecord(titleText, summaryText, urlText)
                             isEditing = false
                        } else {
                             isEditing = true
                        }
                    }) {
                        Icon(
                            if (isEditing) Icons.Default.Check else Icons.Default.Edit, 
                            contentDescription = if (isEditing) "Save" else "Edit"
                        )
                    }

                    // Re-analyze Button
                    IconButton(onClick = { onReAnalyze(record) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Re-analyze")
                    }
                    
                    IconButton(onClick = { 
                        scope.launch {
                            ShareUtils.shareRecordAsImage(context, record)
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                }
            )
        },
        containerColor = Color(0xFFF8F9FA)
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Video Player
            item {
                with(sharedTransitionScope) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp) // Slightly taller
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black)
                            .sharedElement(
                                rememberSharedContentState(key = "image-${record.id}"),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                    ) {
                        if (isPlaying) {
                            VideoPlayer(
                                uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", File(record.filePath))
                            )
                        } else {
                            Box(modifier = Modifier.clickable { isPlaying = true }) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(File(record.thumbnailPath))
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow, 
                                        contentDescription = "Play", 
                                        tint = Color.White,
                                        modifier = Modifier.size(64.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Title & Summary
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (isEditing) {
                            OutlinedTextField(
                                value = titleText,
                                onValueChange = { titleText = it },
                                label = { Text("标题") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = summaryText,
                                onValueChange = { summaryText = it },
                                label = { Text("摘要") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = urlText,
                                onValueChange = { urlText = it },
                                label = { Text("来源链接 (URL)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        } else {
                            with(sharedTransitionScope) {
                                Text(
                                    text = record.title ?: "未命名记忆",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = record.summary ?: "AI 正在思考中...",
                                style = MaterialTheme.typography.bodyLarge,
                                lineHeight = 24.sp,
                                color = Color(0xFF444444)
                            )
                            
                            if (!record.url.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(record.url))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("回溯原链接")
                                }
                            }
                        }
                    }
                }
            }

            // 3. Keywords
            if (!record.keywords.isNullOrEmpty()) {
                item {
                    val tags = remember(record.keywords) {
                        try {
                            val listType = object : TypeToken<List<String>>() {}.type
                            Gson().fromJson<List<String>>(record.keywords, listType)
                        } catch (e: Exception) {
                            record.keywords.replace("[\\[\\]\"]".toRegex(), "").split(",")
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(tags) { tag ->
                            SuggestionChip(
                                onClick = { },
                                label = { Text("#${tag.trim()}") },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = Color.White
                                ),
                                border = null
                            )
                        }
                    }
                }
            }

            // 4. Keyframes Strip
            if (!record.keyFrames.isNullOrEmpty()) {
                item {
                    Column {
                        Text(
                            "高光时刻", 
                            style = MaterialTheme.typography.titleMedium, 
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                        )
                        val frames = remember(record.keyFrames) {
                            try {
                                val listType = object : TypeToken<List<String>>() {}.type
                                Gson().fromJson<List<String>>(record.keyFrames, listType)
                            } catch (e: Exception) {
                                emptyList<String>()
                            }
                        }
                        
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(frames) { framePath ->
                                Box(
                                    modifier = Modifier
                                        .height(140.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.LightGray)
                                        .clickable { 
                                            showFullImage = framePath
                                        }
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(File(framePath))
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxHeight()
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

