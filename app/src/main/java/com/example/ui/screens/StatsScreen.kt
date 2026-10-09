package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.MealPlannerLogic
import com.example.domain.StatsLogic
import com.example.ui.viewmodel.MaaltijdUiState
import java.util.Locale

private val MONTH_NAMES = listOf(
    "januari", "februari", "maart", "april", "mei", "juni",
    "juli", "augustus", "september", "oktober", "november", "december"
)

@Composable
fun StatsScreen(uiState: MaaltijdUiState) {
    val today = remember { MealPlannerLogic.today() }
    var byYear by remember { mutableStateOf(false) }
    var year by remember { mutableStateOf(today.year) }
    var month by remember { mutableStateOf(today.month) }

    val prefix = if (byYear) {
        String.format(Locale.ROOT, "%04d", year)
    } else {
        String.format(Locale.ROOT, "%04d-%02d", year, month)
    }
    val stats = remember(uiState.allDays, prefix) {
        StatsLogic.compute(uiState.allDays.values, prefix, today.toIsoString())
    }

    val canGoNext = if (byYear) year < today.year
    else year < today.year || (year == today.year && month < today.month)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Maand / Jaar
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (!byYear) {
                Button(onClick = {}, modifier = Modifier.weight(1f).testTag("stats_mode_month")) { Text("Maand") }
                OutlinedButton(onClick = { byYear = true }, modifier = Modifier.weight(1f).testTag("stats_mode_year")) { Text("Jaar") }
            } else {
                OutlinedButton(onClick = { byYear = false }, modifier = Modifier.weight(1f).testTag("stats_mode_month")) { Text("Maand") }
                Button(onClick = {}, modifier = Modifier.weight(1f).testTag("stats_mode_year")) { Text("Jaar") }
            }
        }

        // Periode kiezen
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = {
                    if (byYear) {
                        year -= 1
                    } else if (month == 1) {
                        month = 12
                        year -= 1
                    } else {
                        month -= 1
                    }
                },
                modifier = Modifier.testTag("stats_previous")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Vorige periode")
            }
            Text(
                text = if (byYear) "$year" else "${MONTH_NAMES[month - 1].replaceFirstChar { it.uppercase() }} $year",
                style = MaterialTheme.typography.titleLarge
            )
            IconButton(
                onClick = {
                    if (byYear) {
                        year += 1
                    } else if (month == 12) {
                        month = 1
                        year += 1
                    } else {
                        month += 1
                    }
                },
                enabled = canGoNext,
                modifier = Modifier.testTag("stats_next")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Volgende periode")
            }
        }

        if (stats.total == 0) {
            Text(
                text = "Nog geen gegevens voor deze periode.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile("Gekookt", stats.cooked, Modifier.weight(1f))
            StatTile("Afhaal", stats.takeaway, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile("Opwarmmaaltijd", stats.reheated, Modifier.weight(1f))
            StatTile("Niet gekookt", stats.noCook, Modifier.weight(1f))
        }

        if (stats.topDishes.isNotEmpty()) {
            BarSection(title = "Meest gegeten gerechten", items = stats.topDishes)
        }
        if (stats.types.isNotEmpty()) {
            BarSection(title = "Per type", items = stats.types)
        }
    }
}

@Composable
private fun StatTile(label: String, value: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BarSection(title: String, items: List<StatsLogic.CountedItem>) {
    val max = (items.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            items.forEach { item ->
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${item.count}×",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((item.count.toFloat() / max).coerceIn(0.03f, 1f))
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    }
}
