package com.cakeshop.manager.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SectionCard(title: String, subtitle: String? = null, action: @Composable (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                action?.invoke()
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
fun KpiTile(title: String, big: String, line1: String, line2: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .clickable { onClick() }
            .then(if (selected) Modifier.padding(0.dp) else Modifier)
            .padding(16.dp)
            .fillMaxWidth()
    ) {
        Text(title, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Text(big, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(6.dp))
        Text(line1, color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp)
        Text(line2, color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp)
    }
}

@Composable
fun ListRow(title: String, subtitle: String? = null, trailing: String? = null, trailingColor: Color? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) Text(trailing, fontWeight = FontWeight.SemiBold, color = trailingColor ?: MaterialTheme.colorScheme.onSurface)
    }
    HorizontalDivider()
}

@Composable
fun Pill(text: String, bg: Color, ink: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(text, color = ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

data class BarPoint(val label: String, val value: Double)

@Composable
fun SimpleBarChart(points: List<BarPoint>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(180.dp)) {
        if (points.isEmpty()) return@Canvas
        val max = (points.maxOf { it.value }).coerceAtLeast(1.0)
        val barAreaW = size.width / points.size
        val barW = (barAreaW * 0.55f).coerceAtMost(46f)
        val baseY = size.height - 24f
        points.forEachIndexed { i, p ->
            val h = ((size.height - 40f) * (p.value / max)).toFloat().coerceAtLeast(0f)
            val x = i * barAreaW + (barAreaW - barW) / 2
            drawRoundRect(
                color = color.copy(alpha = if (i == points.size - 1) 1f else 0.55f),
                topLeft = Offset(x, baseY - h),
                size = androidx.compose.ui.geometry.Size(barW, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
            )
        }
        drawLine(Color.Gray.copy(alpha = 0.3f), Offset(0f, baseY), Offset(size.width, baseY), strokeWidth = Stroke.DefaultMiter)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        if (points.size <= 8) {
            points.forEach { Text(it.label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            Text(points.first().label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(points.last().label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun EmptyState(title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
