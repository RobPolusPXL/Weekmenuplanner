package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.ShoppingCartCheckout
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AggregatedGroceryItem
import com.example.data.model.DayKind
import com.example.data.model.LooseItem
import com.example.domain.MealPlannerLogic
import com.example.ui.viewmodel.MaaltijdUiState

/**
 * 5.6 Boodschappenlijst:
 * - Toont de lijst van de geselecteerde week (standaard de huidige week, met dezelfde weekwisselaar).
 * - Automatisch berekend uit alle dagen met kind = 'koken' van die week (geen "genereer" knop).
 * - Items zijn afvinkbaar. Afgevinkte items zakken onderaan.
 * - Sectie "Losse items": handmatig toevoegen (naam, optioneel hoeveelheid), per item de keuze eenmalig of vast.
 * - Knop "Winkelen afronden": eenmalige afgevinkte losse items worden verwijderd, vaste losse items worden terug onafgevinkt gezet.
 */
@Composable
fun GroceryListScreen(
    uiState: MaaltijdUiState,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onJumpToCurrentWeek: () -> Unit,
    onToggleAggregatedItem: (String, Boolean) -> Unit,
    onAddLooseItem: (String, String, String, Boolean) -> Unit,
    onToggleLooseItem: (String, Boolean) -> Unit,
    onDeleteLooseItem: (String) -> Unit,
    onFinishShopping: () -> Unit
) {
    var looseName by remember { mutableStateOf("") }
    var looseQuantity by remember { mutableStateOf("") }
    var looseUnit by remember { mutableStateOf("") }
    var looseRecurring by remember { mutableStateOf(false) }

    val aggregatedItems = uiState.aggregatedGroceryItems
    val cookingDaysCount = uiState.selectedWeekPlans.values.count { it.dayKind == DayKind.KOKEN }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("grocery_list_column"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
    ) {
        // 1. Weekwisselaar bovenaan
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onPreviousWeek,
                        modifier = Modifier.testTag("grocery_prev_week_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Vorige week")
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onJumpToCurrentWeek)
                            .padding(vertical = 4.dp)
                            .testTag("grocery_week_title")
                    ) {
                        Text(
                            text = if (uiState.isCurrentWeekSelected) {
                                "Boodschappen deze week ($cookingDaysCount kookdag(en))"
                            } else {
                                "Boodschappen gekozen week ($cookingDaysCount kookdag(en))"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = MealPlannerLogic.formatWeekHeader(uiState.selectedWeekSunday),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    IconButton(
                        onClick = onNextWeek,
                        modifier = Modifier.testTag("grocery_next_week_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Volgende week")
                    }
                }
            }
        }

        // 2. Automatische lijst uit geplande kookdagen
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Van geplande gerechten (${aggregatedItems.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                val checkedCount = aggregatedItems.count { it.checked }
                if (aggregatedItems.isNotEmpty()) {
                    Text(
                        text = "$checkedCount/${aggregatedItems.size} afgevinkt",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (aggregatedItems.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Text(
                        text = if (cookingDaysCount == 0) {
                            "Er zijn nog geen kookdagen gepland in deze week. Plan een gerecht in het tabblad Week om automatisch de boodschappenlijst te vullen."
                        } else {
                            "De geplande gerechten van deze week hebben geen ingrediënten."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(
                items = aggregatedItems,
                key = { "agg_${it.itemKey}" }
            ) { item ->
                AggregatedGroceryRow(
                    item = item,
                    onToggleChecked = { newChecked ->
                        onToggleAggregatedItem(item.itemKey, newChecked)
                    }
                )
            }
        }

        // 3. Sectie "Losse items"
        item {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Losse items (${uiState.looseItems.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Kies per item of het eenmalig of vast (terugkerend) is.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Toevoegformulier los item
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = looseName,
                            onValueChange = { looseName = it },
                            label = { Text("Item naam") },
                            placeholder = { Text("bv. Melk, Keukenrol...") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1.5f)
                                .testTag("loose_item_name_input")
                        )
                        OutlinedTextField(
                            value = looseQuantity,
                            onValueChange = { looseQuantity = it },
                            label = { Text("Hoev.") },
                            placeholder = { Text("2") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(0.7f)
                                .testTag("loose_item_qty_input")
                        )
                        OutlinedTextField(
                            value = looseUnit,
                            onValueChange = { looseUnit = it },
                            label = { Text("Eenh.") },
                            placeholder = { Text("pak") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(0.7f)
                                .testTag("loose_item_unit_input")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !looseRecurring,
                                onClick = { looseRecurring = false },
                                label = { Text("Eenmalig") },
                                modifier = Modifier.testTag("chip_loose_eenmalig")
                            )
                            FilterChip(
                                selected = looseRecurring,
                                onClick = { looseRecurring = true },
                                label = { Text("Vast") },
                                leadingIcon = {
                                    Icon(Icons.Default.Loop, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                modifier = Modifier.testTag("chip_loose_vast")
                            )
                        }

                        Button(
                            onClick = {
                                if (looseName.isNotBlank()) {
                                    onAddLooseItem(looseName, looseQuantity, looseUnit, looseRecurring)
                                    looseName = ""
                                    looseQuantity = ""
                                    looseUnit = ""
                                }
                            },
                            enabled = looseName.isNotBlank(),
                            modifier = Modifier.testTag("add_loose_item_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Toevoegen")
                        }
                    }
                }
            }
        }

        if (uiState.looseItems.isNotEmpty()) {
            items(
                items = uiState.looseItems,
                key = { "loose_${it.id}" }
            ) { loose ->
                LooseItemRow(
                    item = loose,
                    onToggle = { checked -> onToggleLooseItem(loose.id, checked) },
                    onDelete = { onDeleteLooseItem(loose.id) }
                )
            }
        }

        // 4. Knop "Winkelen afronden"
        item {
            val anyCheckedLoose = uiState.looseItems.any { it.checked }
            Spacer(modifier = Modifier.height(6.dp))
            FilledTonalButton(
                onClick = onFinishShopping,
                enabled = anyCheckedLoose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("finish_shopping_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.ShoppingCartCheckout, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Winkelen afronden (eenmalige wissen, vaste resetten)",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun AggregatedGroceryRow(
    item: AggregatedGroceryItem,
    onToggleChecked: (Boolean) -> Unit
) {
    val containerColor = if (item.checked) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onToggleChecked(!item.checked) }
            .testTag("grocery_item_${item.itemKey}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (item.checked) 0.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.checked,
                onCheckedChange = onToggleChecked
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (item.checked) FontWeight.Normal else FontWeight.SemiBold,
                        textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (item.checked) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                if (item.dishNames.isNotEmpty()) {
                    Text(
                        text = item.dishNames.joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (item.displayAmount.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (item.checked) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                ) {
                    Text(
                        text = item.displayAmount,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (item.checked) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LooseItemRow(
    item: LooseItem,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val containerColor = if (item.checked) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onToggle(!item.checked) }
            .testTag("loose_item_${item.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (item.checked) 0.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.checked,
                onCheckedChange = onToggle
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (item.checked) FontWeight.Normal else FontWeight.Medium,
                            textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (item.recurring) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = if (item.recurring) "Vast" else "Eenmalig",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item.recurring) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                val qtyStr = item.quantity?.let { MealPlannerLogic.formatNumberBelgian(it) } ?: ""
                val amountLabel = listOf(qtyStr, item.unit ?: "").filter { it.isNotBlank() }.joinToString(" ")
                if (amountLabel.isNotBlank()) {
                    Text(
                        text = amountLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_loose_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Verwijder los item",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
