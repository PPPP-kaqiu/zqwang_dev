package com.flashpick.app.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.flashpick.app.data.model.VideoRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MemoryCard(
    record: VideoRecord, 
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    
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

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .bouncingClickable(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            })
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // 1. Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFF0F0F0),
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(appName.take(1), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = appName,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.LightGray
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. AI Content
            with(sharedTransitionScope) {
                // Shared Element: Title
                // Removed sharedBounds from Text to prevent layout shift jank
                Text(
                    text = record.title ?: "未命名记忆",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 28.sp
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = record.summary ?: "AI 正在思考中...",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF666666),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(8.dp)) // Reduced spacing

            // 3. Media Preview (Removed Images per request)
            if (!record.filePath.endsWith(".mp4")) {
                // Audio Visualization (Compact)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp) // Reduced height
                        .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("语音笔记", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // 4. Tags
            if (!record.keywords.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                val cleanTags = record.keywords.replace("[\\[\\]\"]".toRegex(), "").split(",")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(cleanTags.take(3)) { tag ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF5F5F5)
                        ) {
                            Text(
                                text = "#${tag.trim()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DailySummaryCard(
    summary: String?,
    isGenerating: Boolean,
    onGenerate: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "每日总结",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                if (isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = onGenerate) {
                        Text(if (summary == null) "生成总结" else "重新生成")
                    }
                }
            }
            
            if (summary != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Black,
                    lineHeight = 22.sp
                )
            }
        }
    }
}
