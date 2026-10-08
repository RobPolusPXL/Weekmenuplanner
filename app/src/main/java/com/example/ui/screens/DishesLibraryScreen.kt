package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Dish
import com.example.data.model.IngredientItem
import com.example.domain.MealPlannerLogic
import com.example.ui.viewmodel.MaaltijdUiState
import com.example.ui.viewmodel.MaaltijdViewModel
import com.example.util.PhotoUtils
import kotlinx.coroutines.launch

/**
 * 5.5 Gerechtenbibliotheek + Types beheren
 */
@Composable
fun DishesLibraryScreen(
    uiState: MaaltijdUiState,
    onOpenNewDish: () -> Unit,
    onOpenEditDish: (Dish) -> Unit,
    onDeleteDish: (Dish) -> Unit,
    onAddType: (String) -> Unit,
    onRenameType: (String, String) -> Unit,
    onDeleteType: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf<String?>(null) }
    var viewingDish by remember { mutableStateOf<Dish?>(null) }
    var showManageTypesDialog by remember { mutableStateOf(false) }

    val filteredDishes = remember(uiState.dishes, searchQuery, selectedTypeFilter) {
        uiState.dishes.filter { dish ->
            val matchesQuery = searchQuery.isBlank() ||
                    dish.name.contains(searchQuery, ignoreCase = true) ||
                    dish.ingredients.any {
                        it.name.contains(searchQuery, ignoreCase = true) ||
                                it.raw.contains(searchQuery, ignoreCase = true)
                    }
            val matchesType = selectedTypeFilter == null ||
                    dish.type.equals(selectedTypeFilter, ignoreCase = true)
            matchesQuery && matchesType
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenNewDish,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nieuw gerecht") },
                modifier = Modifier.testTag("fab_new_dish")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Gerechtenbibliotheek",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "${uiState.dishes.size} gerecht(en) alfabetisch",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = { showManageTypesDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("manage_types_button")
                ) {
                    Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Types (${uiState.dishTypes.size})")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Zoekveld
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Zoek gerecht of ingrediënt...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Wis zoekveld")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dishes_search_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Filter op type
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTypeFilter == null,
                    onClick = { selectedTypeFilter = null },
                    label = { Text("Alle") }
                )
                uiState.dishTypes.forEach { typeName ->
                    FilterChip(
                        selected = selectedTypeFilter.equals(typeName, ignoreCase = true),
                        onClick = {
                            selectedTypeFilter = if (selectedTypeFilter.equals(typeName, ignoreCase = true)) {
                                null
                            } else {
                                typeName
                            }
                        },
                        label = { Text(typeName) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (filteredDishes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Restaurant,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (uiState.dishes.isEmpty()) {
                                "Jullie bibliotheek is nog leeg."
                            } else {
                                "Geen gerechten gevonden voor deze filter."
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Voeg jullie favoriete gerechten toe zodat 'Kies voor mij' en de automatische boodschappenlijst kunnen werken.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("dishes_library_list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(filteredDishes, key = { it.id }) { dish ->
                        DishLibraryItemCard(
                            dish = dish,
                            onClick = { viewingDish = dish }
                        )
                    }
                }
            }
        }
    }

    // Detail van aangeklikt gerecht (met bewerken en verwijderen)
    viewingDish?.let { currentDish ->
        // Haal actuele versie op uit lijst
        val latestDish = uiState.dishes.firstOrNull { it.id == currentDish.id } ?: currentDish
        DishLibraryDetailDialog(
            dish = latestDish,
            onDismiss = { viewingDish = null },
            onEdit = {
                viewingDish = null
                onOpenEditDish(latestDish)
            },
            onDelete = {
                viewingDish = null
                onDeleteDish(latestDish)
            }
        )
    }

    if (showManageTypesDialog) {
        ManageDishTypesDialog(
            dishTypes = uiState.dishTypes,
            dishes = uiState.dishes,
            onDismiss = { showManageTypesDialog = false },
            onAddType = onAddType,
            onRenameType = onRenameType,
            onDeleteType = onDeleteType
        )
    }
}

@Composable
private fun DishLibraryItemCard(
    dish: Dish,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("dish_item_${dish.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!dish.photoUrl.isNullOrBlank()) {
                DishPhotoThumbnail(
                    photoUrl = dish.photoUrl,
                    contentDescription = dish.name,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dish.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                val ingSummary = dish.ingredients.take(4).joinToString(", ") { it.name.ifBlank { it.raw } }
                Text(
                    text = if (ingSummary.isNotBlank()) ingSummary else "Geen ingrediënten opgegeven",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (dish.needsNormalization || dish.ingredients.any { it.needsNormalization }) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SyncProblem,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Wacht op normalisatie (offline opgeslagen)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = dish.type,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun DishLibraryDetailDialog(
    dish: Dish,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(text = dish.name, style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = dish.type,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!dish.photoUrl.isNullOrBlank()) {
                    DishPhotoThumbnail(
                        photoUrl = dish.photoUrl,
                        contentDescription = dish.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                }

                if (dish.ingredients.isNotEmpty()) {
                    Text(
                        text = "Ingrediënten (${dish.ingredients.size}):",
                        style = MaterialTheme.typography.titleSmall
                    )
                    dish.ingredients.forEach { ing ->
                        val amount = MealPlannerLogic.formatQuantityForDisplay(ing.quantity, ing.unit)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "• ${ing.name.ifBlank { ing.raw }}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            if (amount.isNotBlank()) {
                                Text(
                                    text = amount,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                if (!dish.recipeUrl.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = {
                            val url = if (dish.recipeUrl.startsWith("http")) dish.recipeUrl else "https://${dish.recipeUrl}"
                            try {
                                uriHandler.openUri(url)
                            } catch (_: Exception) {
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open receptlink")
                    }
                }

                if (!dish.note.isNullOrBlank()) {
                    Text(
                        text = "Notitie: ${dish.note}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (confirmDelete) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Weet je zeker dat je '${dish.name}' wilt verwijderen? Bestaande weekplanningen blijven intact.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onDelete,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.testTag("confirm_delete_dish_button")
                            ) {
                                Text("Ja, definitief verwijderen")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onEdit,
                modifier = Modifier.testTag("dialog_edit_dish_button")
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Bewerken")
            }
        },
        dismissButton = {
            Row {
                if (!confirmDelete) {
                    TextButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("dialog_delete_dish_button")
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Verwijderen")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Sluiten")
                }
            }
        }
    )
}

@Composable
private fun ManageDishTypesDialog(
    dishTypes: List<String>,
    dishes: List<Dish>,
    onDismiss: () -> Unit,
    onAddType: (String) -> Unit,
    onRenameType: (String, String) -> Unit,
    onDeleteType: (String) -> Unit
) {
    var newTypeInput by remember { mutableStateOf("") }
    var editingType by remember { mutableStateOf<String?>(null) }
    var renameInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Gerechttypes beheren", style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Types die nog in gebruik zijn door gerechten kunnen pas verwijderd worden nadat die gerechten een ander type hebben gekregen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newTypeInput,
                        onValueChange = { newTypeInput = it },
                        label = { Text("Nieuw type") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (newTypeInput.isNotBlank()) {
                                onAddType(newTypeInput)
                                newTypeInput = ""
                            }
                        },
                        enabled = newTypeInput.isNotBlank()
                    ) {
                        Text("Toevoegen")
                    }
                }

                HorizontalDivider()

                dishTypes.forEach { t ->
                    val countInUse = dishes.count { it.type.equals(t, ignoreCase = true) }
                    if (editingType == t) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = renameInput,
                                onValueChange = { renameInput = it },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    onRenameType(t, renameInput)
                                    editingType = null
                                }
                            ) {
                                Text("Opslaan")
                            }
                            TextButton(onClick = { editingType = null }) {
                                Text("Annuleer")
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = t, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "$countInUse gerecht(en)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row {
                                IconButton(
                                    onClick = {
                                        editingType = t
                                        renameInput = t
                                    }
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Hernoem $t", modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { onDeleteType(t) },
                                    enabled = countInUse == 0 && dishTypes.size > 1
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Verwijder $t",
                                        tint = if (countInUse == 0 && dishTypes.size > 1) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                        },
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Sluiten")
            }
        }
    )
}

/**
 * 5.4 Gerecht bewerken / nieuw
 * - Velden: naam (verplicht), type (verplicht, dropdown met "nieuw type toevoegen"),
 *   ingrediënten (dynamische lijst, per regel één vrij tekstveld zoals "2 uien" of "500 g gehakt"),
 *   receptlink, notitie, foto (camera of galerij).
 * - De gebruiker kan genormaliseerde waarden (name, quantity, unit) in het bewerkscherm nakijken en aanpassen (6.6).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DishEditorScreen(
    existingDish: Dish?,
    dishTypes: List<String>,
    assignToDateIdAfterSave: String?,
    isSaving: Boolean,
    onPreviewNormalize: suspend (List<String>) -> List<IngredientItem>,
    onSaveDish: (
        existingDishId: String?,
        name: String,
        type: String,
        draftIngredients: List<MaaltijdViewModel.DraftIngredientLine>,
        recipeUrl: String?,
        note: String?,
        processedPhoto: PhotoUtils.ProcessedPhoto?,
        existingPhotoUrl: String?,
        assignToDateIdAfterSave: String?
    ) -> Unit,
    onCancel: () -> Unit
) {
    BackHandler(onBack = onCancel)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember(existingDish) { mutableStateOf(existingDish?.name ?: "") }
    var selectedType by remember(existingDish, dishTypes) {
        mutableStateOf(existingDish?.type ?: dishTypes.firstOrNull() ?: "pasta")
    }
    var typeDropdownExpanded by remember { mutableStateOf(false) }
    var showAddNewTypeInline by remember { mutableStateOf(false) }
    var newTypeInlineText by remember { mutableStateOf("") }

    var recipeUrl by remember(existingDish) { mutableStateOf(existingDish?.recipeUrl ?: "") }
    var note by remember(existingDish) { mutableStateOf(existingDish?.note ?: "") }
    var existingPhotoUrl by remember(existingDish) { mutableStateOf(existingDish?.photoUrl) }
    var processedPhoto by remember { mutableStateOf<PhotoUtils.ProcessedPhoto?>(null) }

    var showNormalizedInspector by remember { mutableStateOf(existingDish != null && existingDish.ingredients.isNotEmpty()) }
    var isNormalizingPreview by remember { mutableStateOf(false) }

    val draftLines = remember(existingDish) {
        val initial = existingDish?.ingredients?.map { ing ->
            MaaltijdViewModel.DraftIngredientLine(
                raw = ing.raw,
                normalizedName = ing.name,
                quantityText = ing.quantity?.let { MealPlannerLogic.formatNumberBelgian(it) } ?: "",
                unitText = ing.unit ?: "",
                manuallyEdited = !ing.needsNormalization && ing.name.isNotBlank()
            )
        } ?: listOf(MaaltijdViewModel.DraftIngredientLine())
        mutableStateListOf(*initial.ifEmpty { listOf(MaaltijdViewModel.DraftIngredientLine()) }.toTypedArray())
    }

    // Zero-permission Android Photo Picker (Galerij)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val processed = PhotoUtils.processPickedUri(context, uri)
                if (processed != null) {
                    processedPhoto = processed
                }
            }
        }
    }

    // Camera (TakePicturePreview vereist geen CAMERA runtime-permissie op Android)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            scope.launch {
                val processed = PhotoUtils.processCameraBitmap(context, bitmap)
                if (processed != null) {
                    processedPhoto = processed
                }
            }
        }
    }

    Scaffold(
        // Titelbalk: statusBarsPadding() zorgt dat de inhoud onder de statusbalk (batterij, klok) begint.
        // De Surface tekent wel nog achter de statusbalk, zodat de kleur doorloopt (edge-to-edge).
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.testTag("editor_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Terug")
                    }
                    Column {
                        Text(
                            text = if (existingDish == null) "Nieuw gerecht" else "Gerecht bewerken",
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (!assignToDateIdAfterSave.isNullOrBlank()) {
                            val targetDate = MealPlannerLogic.parseIsoDate(assignToDateIdAfterSave)
                            Text(
                                text = "Wordt meteen gepland op ${targetDate?.let { MealPlannerLogic.getDutchDayName(it) + " " + it.toBelgianString() } ?: assignToDateIdAfterSave}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        // Actiebalk onderaan: Opslaan staat binnen duimbereik en kan nooit onder de statusbalk belanden.
        // navigationBarsPadding() houdt de knoppen boven de gesture-/navigatiebalk van Android.
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("editor_cancel_button")
                    ) {
                        Text("Annuleren")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSaveDish(
                                existingDish?.id,
                                name,
                                selectedType,
                                draftLines.toList(),
                                recipeUrl,
                                note,
                                processedPhoto,
                                existingPhotoUrl,
                                assignToDateIdAfterSave
                            )
                        },
                        enabled = !isSaving && name.isNotBlank() && selectedType.isNotBlank(),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("save_dish_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Opslaan")
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Naam (verplicht)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Naam van het gerecht *") },
                placeholder = { Text("bv. Spaghetti Bolognaise") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dish_name_input")
            )

            // 2. Type (verplicht, dropdown met "nieuw type toevoegen")
            ExposedDropdownMenuBox(
                expanded = typeDropdownExpanded,
                onExpandedChange = { typeDropdownExpanded = !typeDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = selectedType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Type hoofdbestanddeel *") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeDropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .testTag("dish_type_dropdown")
                )
                ExposedDropdownMenu(
                    expanded = typeDropdownExpanded,
                    onDismissRequest = { typeDropdownExpanded = false }
                ) {
                    dishTypes.forEach { t ->
                        DropdownMenuItem(
                            text = { Text(t) },
                            onClick = {
                                selectedType = t
                                typeDropdownExpanded = false
                            }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Nieuw type toevoegen...", color = MaterialTheme.colorScheme.primary)
                            }
                        },
                        onClick = {
                            typeDropdownExpanded = false
                            showAddNewTypeInline = true
                        }
                    )
                }
            }

            AnimatedVisibility(visible = showAddNewTypeInline) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newTypeInlineText,
                        onValueChange = { newTypeInlineText = it },
                        label = { Text("Nieuw type naam") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val clean = newTypeInlineText.trim().lowercase()
                            if (clean.isNotEmpty()) {
                                selectedType = clean
                                showAddNewTypeInline = false
                                newTypeInlineText = ""
                            }
                        }
                    ) {
                        Text("Kies")
                    }
                }
            }

            // 3. Ingrediënten (dynamische lijst, per regel één vrij tekstveld zoals "2 uien" of "500 g gehakt")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ingrediënten",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Eén ingrediënt per regel (bv. '2 uien', '500 g gehakt'). Gemini normaliseert automatisch bij opslaan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    draftLines.forEachIndexed { index, draft ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = draft.raw,
                                    onValueChange = { newRaw ->
                                        draftLines[index] = draft.copy(
                                            raw = newRaw,
                                            manuallyEdited = false
                                        )
                                    },
                                    placeholder = { Text("bv. 2 uien of 500 g gehakt") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("ingredient_raw_input_$index")
                                )
                                IconButton(
                                    onClick = {
                                        if (draftLines.size > 1) {
                                            draftLines.removeAt(index)
                                        } else {
                                            draftLines[0] = MaaltijdViewModel.DraftIngredientLine()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Verwijder regel"
                                    )
                                }
                            }

                            // 6.6 De gebruiker kan genormaliseerde waarden in het bewerkscherm nakijken en aanpassen
                            AnimatedVisibility(visible = showNormalizedInspector && draft.raw.isNotBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp, start = 4.dp, end = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedTextField(
                                        value = draft.normalizedName,
                                        onValueChange = { newName ->
                                            draftLines[index] = draft.copy(
                                                normalizedName = newName,
                                                manuallyEdited = true
                                            )
                                        },
                                        label = { Text("Naam (enkelvoud)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.4f)
                                    )
                                    OutlinedTextField(
                                        value = draft.quantityText,
                                        onValueChange = { newQty ->
                                            draftLines[index] = draft.copy(
                                                quantityText = newQty,
                                                manuallyEdited = true
                                            )
                                        },
                                        label = { Text("Hoev.") },
                                        singleLine = true,
                                        modifier = Modifier.weight(0.8f)
                                    )
                                    OutlinedTextField(
                                        value = draft.unitText,
                                        onValueChange = { newUnit ->
                                            draftLines[index] = draft.copy(
                                                unitText = newUnit,
                                                manuallyEdited = true
                                            )
                                        },
                                        label = { Text("Eenheid") },
                                        singleLine = true,
                                        modifier = Modifier.weight(0.8f)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { draftLines.add(MaaltijdViewModel.DraftIngredientLine()) },
                            modifier = Modifier.testTag("add_ingredient_line_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Regel toevoegen")
                        }

                        FilledTonalButton(
                            onClick = {
                                showNormalizedInspector = true
                                val nonBlank = draftLines.map { it.raw.trim() }
                                if (nonBlank.any { it.isNotEmpty() }) {
                                    scope.launch {
                                        isNormalizingPreview = true
                                        val nonEmptyIndices = draftLines.indices.filter { draftLines[it].raw.isNotBlank() }
                                        val rawInputs = nonEmptyIndices.map { draftLines[it].raw.trim() }
                                        val normalized = onPreviewNormalize(rawInputs)
                                        nonEmptyIndices.zip(normalized).forEach { (lineIdx, item) ->
                                            draftLines[lineIdx] = draftLines[lineIdx].copy(
                                                normalizedName = item.name,
                                                quantityText = item.quantity?.let { MealPlannerLogic.formatNumberBelgian(it) } ?: "",
                                                unitText = item.unit ?: "",
                                                manuallyEdited = true
                                            )
                                        }
                                        isNormalizingPreview = false
                                    }
                                }
                            },
                            enabled = !isNormalizingPreview
                        ) {
                            if (isNormalizingPreview) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Normalisatie nakijken")
                        }
                    }
                }
            }

            // 4. Receptlink (optioneel)
            OutlinedTextField(
                value = recipeUrl,
                onValueChange = { recipeUrl = it },
                label = { Text("Receptlink (optioneel)") },
                placeholder = { Text("https://...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dish_recipe_url_input")
            )

            // 5. Notitie (optioneel)
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Notitie (optioneel)") },
                placeholder = { Text("Tips, bereidingstijd of variatie...") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dish_note_input")
            )

            // 6. Foto (camera of galerij, gecomprimeerd tot max 1600px)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Foto van het gerecht (optioneel)",
                        style = MaterialTheme.typography.titleSmall
                    )

                    val activePhotoPreview = processedPhoto?.syncedDataUrl ?: existingPhotoUrl
                    if (!activePhotoPreview.isNullOrBlank()) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            DishPhotoThumbnail(
                                photoUrl = activePhotoPreview,
                                contentDescription = name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(170.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                            IconButton(
                                onClick = {
                                    processedPhoto = null
                                    existingPhotoUrl = null
                                },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Verwijder foto",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(6.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Galerij")
                        }

                        OutlinedButton(
                            onClick = { cameraLauncher.launch(null) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Camera")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
