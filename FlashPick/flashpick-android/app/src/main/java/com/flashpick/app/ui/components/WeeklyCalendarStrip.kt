package com.flashpick.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyCalendarStrip(
    selectedDate: String,
    onDateSelected: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onInsightsClick: () -> Unit
) {
    val context = LocalContext.current
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    
    var baseDate by remember {
        mutableStateOf(Date())
    }
    
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = baseDate.time)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            colors = DatePickerDefaults.colors(
                containerColor = Color.White,
            ),
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            baseDate = Date(millis)
                            onDateSelected(format.format(Date(millis)))
                        }
                        showDatePicker = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Black)
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePicker = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)
                ) {
                    Text("取消")
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = Color.White,
                    titleContentColor = Color.Black,
                    headlineContentColor = Color.Black,
                    weekdayContentColor = Color.Gray,
                    subheadContentColor = Color.Gray,
                    yearContentColor = Color.Black,
                    currentYearContentColor = Color.Black,
                    selectedYearContentColor = Color.White,
                    selectedYearContainerColor = Color.Black,
                    dayContentColor = Color.Black,
                    disabledDayContentColor = Color.LightGray,
                    selectedDayContentColor = Color.White,
                    selectedDayContainerColor = Color.Black,
                    todayContentColor = Color.Black,
                    todayDateBorderColor = Color.Black
                )
            )
        }
    }

    val dates = remember(baseDate) {
        val cal = Calendar.getInstance()
        cal.time = baseDate
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val weekDates = mutableListOf<Date>()
        for (i in 0 until 7) {
            weekDates.add(cal.time)
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        weekDates
    }

    Card(
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val displayMonth = SimpleDateFormat("MMM yyyy", Locale.US).format(dates[0])
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showDatePicker = true }
                        .padding(4.dp)
                ) {
                    Text(
                        text = displayMonth,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.DateRange, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
            
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val cal = Calendar.getInstance()
                            cal.time = baseDate
                            cal.add(Calendar.DAY_OF_YEAR, -7)
                            baseDate = cal.time
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Prev Week", tint = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    IconButton(
                        onClick = {
                            val cal = Calendar.getInstance()
                            cal.time = baseDate
                            cal.add(Calendar.DAY_OF_YEAR, 7)
                            baseDate = cal.time
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Next Week", tint = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    IconButton(
                        onClick = onInsightsClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "Insights", tint = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.Gray)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            dates.forEach { date ->
                val dateStr = format.format(date)
                val isSelected = dateStr == selectedDate
                val dayNum = SimpleDateFormat("d", Locale.US).format(date)
                val dayName = SimpleDateFormat("EEE", Locale.CHINA).format(date)
                
                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) Color.Black else Color.Transparent,
                    label = "bgColor"
                )
                val dayTextColor by animateColorAsState(
                    targetValue = if (isSelected) Color.Gray else Color.LightGray,
                    label = "dayTextColor"
                )
                val numTextColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else Color.DarkGray,
                    label = "numTextColor"
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .bouncingClickable { onDateSelected(dateStr) }
                        .background(backgroundColor)
                        .padding(vertical = 8.dp, horizontal = 12.dp)
                ) {
                    Text(
                        text = dayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = dayTextColor,
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = dayNum,
                        style = MaterialTheme.typography.titleMedium,
                        color = numTextColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
