package com.flashpick.app.ui.screens.settings

import android.app.Application
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.flashpick.app.data.model.AppUsageStat
import com.flashpick.app.overlay.OverlayService

@Composable
fun SettingsSheet(
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
    onSyncFiles: () -> Unit,
    onAnalyzeAll: () -> Unit,
    onResetOverlay: () -> Unit,
    onShowManual: () -> Unit,
    onUpdateOverlayDebug: (Int, Int) -> Unit
) {
    var preSeconds by remember { mutableFloatStateOf(5f) }
    var postSeconds by remember { mutableFloatStateOf(5f) }
    var menuRadius by remember { mutableFloatStateOf(60f) }
    var menuSize by remember { mutableFloatStateOf(180f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Row {
                TextButton(onClick = { onSyncFiles() }) {
                    Text("找回")
                }
                TextButton(onClick = { onAnalyzeAll() }) {
                    Text("一键分析")
                }
            }
        }
        
        HorizontalDivider()
        
        Text("权限管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        
        PermissionRow("麦克风权限", runtimeGranted, onRequestRuntime)
        PermissionRow("悬浮窗权限", overlayGranted, onRequestOverlay)
        PermissionRow("无障碍服务 (监控应用)", accessibilityEnabled, onOpenAccessibility)
        PermissionRow("录屏权限", recorderPermissionGranted, onRequestRecorder)
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedButton(
            onClick = onManageWhitelist,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("管理白名单应用")
        }
        
        HorizontalDivider()

        Text("界面设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedButton(
            onClick = onResetOverlay,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("重置桌宠位置 (找回消失的宠物)")
        }
        OutlinedButton(
            onClick = onShowManual,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("功能介绍 / 使用手册")
        }
        
        HorizontalDivider()

        Text("桌宠调试 (Overlay Debug)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("菜单半径 (Radius): ${menuRadius.toInt()} dp", modifier = Modifier.weight(1f))
            }
            Slider(
                value = menuRadius,
                onValueChange = { menuRadius = it },
                onValueChangeFinished = { onUpdateOverlayDebug(menuRadius.toInt(), menuSize.toInt()) },
                valueRange = 40f..150f,
                steps = 10
            )
        }

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("窗口大小 (Size): ${menuSize.toInt()} dp", modifier = Modifier.weight(1f))
            }
            Slider(
                value = menuSize,
                onValueChange = { menuSize = it },
                onValueChangeFinished = { onUpdateOverlayDebug(menuRadius.toInt(), menuSize.toInt()) },
                valueRange = 150f..350f,
                steps = 19
            )
        }
        
        HorizontalDivider()
        
        Text("录制时长", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("保留前 ${preSeconds.toInt()} 秒", modifier = Modifier.weight(1f))
            }
            Slider(
                value = preSeconds,
                onValueChange = { preSeconds = it },
                onValueChangeFinished = { onUpdateCaptureWindow(preSeconds.toLong() * 1000, postSeconds.toLong() * 1000) },
                valueRange = 1f..10f,
                steps = 9
            )
        }

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("保留后 ${postSeconds.toInt()} 秒", modifier = Modifier.weight(1f))
            }
            Slider(
                value = postSeconds,
                onValueChange = { postSeconds = it },
                onValueChangeFinished = { onUpdateCaptureWindow(preSeconds.toLong() * 1000, postSeconds.toLong() * 1000) },
                valueRange = 1f..10f,
                steps = 9
            )
        }
    }
}

@Composable
fun PermissionRow(label: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray)
        TextButton(onClick = onClick, enabled = !granted) {
            Text(if (granted) "已开启" else "开启")
        }
    }
}

@Composable
fun UserManualSheet() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("FlashPick 使用手册", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        
        SectionTitle("🌟 核心亮点：灵动桌宠 (Luppy)")
        BodyText("FlashPick 的灵魂是一只居住在你屏幕上的白色小团子。它不仅可爱，更是你操控记忆的控制台。")
        
        SubTitle("1. 交互指南")
        BulletPoint("👁️ 眼神交流：它会随机眨眼，还会好奇地盯着你点击的方向。")
        BulletPoint("👆 单击 (Single Tap)：唤出功能菜单（录制2s/10s/语音）。")
        BulletPoint("✌️ 双击 (Double Tap)：极速保存刚刚发生的精彩瞬间。")
        BulletPoint("🤏 拖拽 (Drag)：按住可拖动，拖到边缘可半隐藏。")

        SubTitle("2. 功能菜单")
        BulletPoint("⏱️ 2s/10s：回溯录制过去 2秒/10秒 的画面。")
        BulletPoint("🎙️ Mic：长按开启语音笔记，松开结束。")

        SectionTitle("📅 记忆回溯：流体记忆流")
        BodyText("每一条记录都以精美的卡片形式展示，包含 AI 标题、智能摘要和动态封面。")
        
        SubTitle("记忆详情")
        BulletPoint("🎬 视频回放：内置高清播放器。")
        BulletPoint("✨ 高光时刻：AI 自动提取的关键帧。")
        BulletPoint("📝 深度解析：AI 针对内容生成的总结与标签。")
        BulletPoint("🔗 链接回溯：一键跳转回录制时的 App 或网页。")

        SectionTitle("🔍 智能搜索与洞察")
        BulletPoint("全局搜索：输入关键词瞬间找到相关记忆。")
        BulletPoint("数据洞察：查看本周最常记录的 App 和兴趣分布。")

        SectionTitle("⚙️ 常见问题")
        BulletPoint("❓ 桌宠不见了？去设置里点击“重置桌宠位置”。")
        BulletPoint("❓ 双击没反应？请确保录屏权限已授予。")
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
}

@Composable
fun SubTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
fun BodyText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
}

@Composable
fun BulletPoint(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text("• ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
    }
}

@Composable
fun InsightsSheet(stats: List<AppUsageStat>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "本周记忆洞察",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        HorizontalDivider()
        
        if (stats.isEmpty()) {
            Text("暂无数据，多记录一些吧！", color = Color.Gray)
        } else {
            // Visualize stats (Simple Bar Chart)
            val maxCount = stats.maxOfOrNull { it.count } ?: 1
            
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(stats) { stat ->
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stat.appName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("${stat.count} 条", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        // Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFF0F0F0))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(stat.count.toFloat() / maxCount)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Black)
                            )
                        }
                    }
                }
            }
        }
    }
}

