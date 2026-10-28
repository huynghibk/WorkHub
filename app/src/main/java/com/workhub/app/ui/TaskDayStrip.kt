package com.workhub.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workhub.app.R
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

sealed class DayStripSelection {
    data object Overdue : DayStripSelection()
    data class Day(val date: LocalDate) : DayStripSelection()
}

@Composable
fun TaskDayStrip(
    selection: DayStripSelection,
    overdueCount: Int,
    onSelect: (DayStripSelection) -> Unit,
    modifier: Modifier = Modifier,
    daysAhead: Int = 20,
) {
    val today = remember { LocalDate.now() }
    val days = remember(today, daysAhead) { (0 until daysAhead).map { today.plusDays(it.toLong()) } }
    val locale = Locale.getDefault()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OverdueChip(
            count = overdueCount,
            selected = selection is DayStripSelection.Overdue,
            onClick = { onSelect(DayStripSelection.Overdue) },
        )
        days.forEach { day ->
            val selected = (selection as? DayStripSelection.Day)?.date == day
            DayChip(
                day = day,
                selected = selected,
                locale = locale,
                onClick = { onSelect(DayStripSelection.Day(day)) },
            )
        }
    }
}

@Composable
private fun OverdueChip(
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .width(64.dp)
            .height(64.dp)
            .clip(shape)
            .background(if (selected) Color(0xFFFEE2E2) else Color(0xFFF3F4F6))
            .then(
                if (selected) Modifier.border(1.dp, Color(0xFFFECACA), shape) else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(6.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Notifications,
                contentDescription = null,
                tint = Color(0xFFDC2626),
                modifier = Modifier.size(14.dp),
            )
            Text(
                count.toString(),
                color = Color(0xFFDC2626),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        }
        Text(
            stringResource(R.string.hub_overdue),
            color = Color(0xFFDC2626),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun DayChip(
    day: LocalDate,
    selected: Boolean,
    locale: Locale,
    onClick: () -> Unit,
) {
    val weekday = day.dayOfWeek.getDisplayName(TextStyle.NARROW, locale)
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .width(44.dp)
            .height(64.dp)
            .clip(shape)
            .background(if (selected) Color(0xFFF3F4F6) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            weekday,
            color = if (selected) Color(0xFF111827) else Color(0xFF9CA3AF),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            day.dayOfMonth.toString(),
            color = if (selected) Color(0xFFDC2626) else Color(0xFF6B7280),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun dayStripTitle(selection: DayStripSelection): String {
    val locale = Locale.getDefault()
    return when (selection) {
        DayStripSelection.Overdue -> stringResource(R.string.hub_overdue)
        is DayStripSelection.Day -> {
            val today = LocalDate.now()
            val tomorrow = today.plusDays(1)
            when (selection.date) {
                today -> stringResource(R.string.hub_today)
                tomorrow -> stringResource(R.string.day_tomorrow)
                else -> {
                    val dow = selection.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                    val month = selection.date.month.getDisplayName(TextStyle.SHORT, locale)
                    "$dow, $month ${selection.date.dayOfMonth}"
                }
            }
        }
    }
}
