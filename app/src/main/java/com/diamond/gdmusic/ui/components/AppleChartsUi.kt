package com.diamond.gdmusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diamond.gdmusic.data.AppleChartRegions
import com.diamond.gdmusic.data.AppleCharts

@Composable
fun AppleChartCover(token: String, modifier: Modifier = Modifier) {
    val parts = token.split(':')
    val country = parts.getOrNull(1).orEmpty()
    val palette = listOf(0xFF8E7794, 0xFF658A89, 0xFF8B8364, 0xFF6886A0,
        0xFFAA7A7F, 0xFF7C8D72, 0xFF997D69, 0xFF7C81A3)
    val index = (parts.getOrNull(2)?.toIntOrNull() ?: 0).mod(palette.size)
    Box(modifier.background(Color(palette[index])).padding(10.dp), contentAlignment = Alignment.Center) {
        Text("${AppleCharts.countryName(country)}\n热门歌曲", color = Color.White,
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun AppleCountryPicker(initial: List<String>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var selected by remember { mutableStateOf(initial.toSet()) }
    var query by remember { mutableStateOf("") }
    val allCountries = remember { AppleChartRegions.supported().sortedBy(AppleCharts::countryName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择国家或地区（${selected.size}/10）") },
        text = {
            Column {
                OutlinedTextField(value = query, onValueChange = { query = it },
                    placeholder = { Text("搜索国家或地区") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Text("最多选择 10 个；取消选择会移除该榜单缓存。",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(allCountries.filter {
                        query.isBlank() || AppleCharts.countryName(it).contains(query.trim(), true) || it.contains(query.trim(), true)
                    }, key = { it }) { country ->
                        val checked = country in selected
                        val enabled = checked || selected.size < 10
                        fun toggle() { selected = if (checked) selected - country else selected + country }
                        ListItem(
                            headlineContent = { Text(AppleCharts.countryName(country)) },
                            trailingContent = { Checkbox(checked, onCheckedChange = { toggle() }, enabled = enabled) },
                            modifier = Modifier.clickable(enabled = enabled) { toggle() }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selected.toList()) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
