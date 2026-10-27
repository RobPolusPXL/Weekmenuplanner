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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.PantryItem
import com.example.domain.GroceryDepartments
import com.example.domain.MealPlannerLogic
import com.example.domain.PantryLogic
import com.example.ui.viewmodel.MaaltijdUiState

@Composable
fun PantryScreen(
    uiState: MaaltijdUiState,
    onAddItem: (text: String, minText: String) -> Unit,
    onUpdateItem: (item: PantryItem, addText: String, exactText: String, minText: String) -> Unit,
    onDeleteItem: (PantryItem) -> Unit,
    onAddToGroceryList: (PantryItem) -> Unit
) {
    var newText by remember { mutableStateOf("") }
    var newMin by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<PantryItem?>(null) }

    val pantryItems = uiState.pantryItems
    val lowItems = uiState.lowStockItems
    val reserved = uiState.reservedStockByKey

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("pantry_column"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
    ) {
        item(key = "pantry_intro") {
            Column {
                Text("Voorraad (${pantryItems.size})", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Wat in de voorraad zit komt niet op de boodschappenlijst; geplande gerechten " +
                        "trekken het automatisch af. Zonder hoeveelheid (bv. \"zout\") telt iets als altijd in huis.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (lowItems.isNotEmpty()) {
            item(key = "pantry_low") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Bijna op (${lowItems.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        lowItems.forEach { low ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${low.name.replaceFirstChar { it.uppercase() }} — nog ${freeText(low)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { onAddToGroceryList(low) },
                                    modifier = Modifier.testTag("pantry_low_to_list_${low.id}")
                                ) {
                                    Icon(
                                        Icons.Default.AddShoppingCart,
                                        contentDescription = "Op boodschappenlijst",
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "pantry_add") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newText,
                        onValueChange = { newText = it },
                        label = { Text("Product + hoeveelheid") },
                        placeholder = { Text("bv. 1 kg rijst, 6 eieren, zout") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pantry_add_input")
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newMin,
                            onValueChange = { newMin = it },
                            label = { Text("Minimumvoorraad (optioneel)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pantry_add_min_input")
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = {
                                onAddItem(newText, newMin)
                                newText = ""
                                newMin = ""
                            },
                            enabled = newText.isNotBlank(),
                            modifier = Modifier.testTag("pantry_add_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Toevoegen")
                        }
                    }
                    Text(
                        text = "Bestaat het product al, dan wordt het aangevuld. Minimum in g, ml of stuk. Daaronder krijg je een melding.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (pantryItems.isEmpty() && uiState.isPantryLoaded) {
            item(key = "pantry_empty") {
                Text(
                    text = "Nog niets in de voorraad.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }

        GroceryDepartments.group(pantryItems) { it.name }.forEach { (dept, deptItems) ->
            item(key = "pantry_head_${dept.name}") {
                Text(
                    text = dept.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            items(items = deptItems, key = { "pantry_${it.id}" }) { item ->
                PantryRow(
                    item = item,
                    reserved = reserved[item.key] ?: 0.0,
                    onClick = { editing = item }
                )
            }
        }
    }

    editing?.let { item ->
        PantryEditDialog(
            item = item,
            reserved = reserved[item.key] ?: 0.0,
            onDismiss = { editing = null },
            onSave = { addText, exactText, minText ->
                onUpdateItem(item, addText, exactText, minText)
                editing = null
            },
            onDelete = {
                onDeleteItem(item)
                editing = null
            }
        )
    }
}

private fun freeText(item: PantryItem): String {
    val q = item.quantity ?: return "in huis"
    return PantryLogic.format(q, item.unit).ifBlank { "0" }
}

@Composable
private fun PantryRow(item: PantryItem, reserved: Double, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("pantry_item_${item.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                val sub = buildList {
                    if (reserved > 0.0001) add("${PantryLogic.format(reserved, item.unit)} gepland")
                    item.minQuantity?.let { add("min. ${PantryLogic.format(it, item.unit)}") }
                }
                if (sub.isNotEmpty()) {
                    Text(
                        text = sub.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (item.isLow) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                }
            ) {
                Text(
                    text = freeText(item),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (item.isLow) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun PantryEditDialog(
    item: PantryItem,
    reserved: Double,
    onDismiss: () -> Unit,
    onSave: (addText: String, exactText: String, minText: String) -> Unit,
    onDelete: () -> Unit
) {
    val unitLabel = item.unit?.let { " ($it)" } ?: ""
    var addText by remember { mutableStateOf("") }
    var exactText by remember { mutableStateOf("") }
    var minText by remember {
        mutableStateOf(item.minQuantity?.let { MealPlannerLogic.formatNumberBelgian(it) } ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.name.replaceFirstChar { it.uppercase() },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete, modifier = Modifier.testTag("pantry_delete_button")) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Verwijderen",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = buildString {
                        append("Vrij: ${freeText(item)}")
                        if (reserved > 0.0001) append(" · gepland: ${PantryLogic.format(reserved, item.unit)}")
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = addText,
                    onValueChange = { addText = it },
                    label = { Text("Aanvullen met$unitLabel") },
                    supportingText = { Text("Negatief = verbruikt, bv. -200") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pantry_edit_add_input")
                )
                OutlinedTextField(
                    value = exactText,
                    onValueChange = { exactText = it },
                    label = { Text("Of exact vrij instellen op$unitLabel") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pantry_edit_exact_input")
                )
                OutlinedTextField(
                    value = minText,
                    onValueChange = { minText = it },
                    label = { Text("Minimumvoorraad$unitLabel") },
                    supportingText = { Text("Melding als het minder wordt. Leeg = pas als het op is.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pantry_edit_min_input")
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(addText, exactText, minText) },
                modifier = Modifier.testTag("pantry_edit_save")
            ) { Text("Opslaan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuleren") }
        }
    )
}
