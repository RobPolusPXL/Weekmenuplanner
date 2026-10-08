package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.DayKind
import com.example.data.model.DayPlan
import com.example.data.model.Dish
import com.example.domain.MealPlannerLogic
import com.example.ui.viewmodel.MaaltijdUiState
import com.example.util.PhotoUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekCalendarScreen(
    uiState: MaaltijdUiState,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onJumpToCurrentWeek: () -> Unit,
    onGenerateWholeWeek: () -> Unit,
    onPickForMeDay: (String) -> Unit,
    onAssignDishToDay: (String, Dish) -> Unit,
    onMarkDaySpecial: (String, DayKind, String?) -> Unit,
    onClearDay: (String) -> Unit,
    onCopyPastDayToCurrentWeek: (DayPlan, String) -> Unit,
    onOpenNewDishForDay: (String) -> Unit,
    onOpenEditDish: (Dish) -> Unit
) {
    val todayIso = remember { MealPlannerLogic.today().toIsoString() }

    // Actieve dialogen / bottom sheets
    var selectedEmptyDate by remember { mutableStateOf<MealPlannerLogic.SimpleDate?>(null) }
    var selectedDetailDate by remember { mutableStateOf<MealPlannerLogic.SimpleDate?>(null) }
    var dishPickerForDateId by remember { mutableStateOf<String?>(null) }
    var copyPastDayDialogSource by remember { mutableStateOf<DayPlan?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Weekwisselaar + Genereer hele week kaart
        WeekSwitcherHeaderCard(
            weekSunday = uiState.selectedWeekSunday,
            isCurrentWeek = uiState.isCurrentWeekSelected,
            isPastWeek = uiState.isPastWeekSelected,
            onPreviousWeek = onPreviousWeek,
            onNextWeek = onNextWeek,
            onJumpToCurrentWeek = onJumpToCurrentWeek,
            onGenerateWholeWeek = onGenerateWholeWeek
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 7 rijen (zondag t/m zaterdag)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("week_days_list"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(
                items = uiState.selectedWeekDates,
                key = { it.toIsoString() }
            ) { date ->
                val dateId = date.toIsoString()
                val plan = uiState.allDays[dateId]
                val isToday = dateId == todayIso
                val isFilled = plan != null && plan.dayKind != DayKind.LEEG

                DayRowCard(
                    date = date,
                    plan = plan,
                    isToday = isToday,
                    isPastWeek = uiState.isPastWeekSelected,
                    onRowClick = {
                        if (isFilled) {
                            selectedDetailDate = date
                        } else {
                            selectedEmptyDate = date
                        }
                    },
                    onPickForMeClick = { onPickForMeDay(dateId) },
                    onCopyToCurrentWeekClick = {
                        if (plan != null && plan.dayKind == DayKind.KOKEN) {
                            copyPastDayDialogSource = plan
                        }
                    }
                )
            }
        }
    }

    // 5.3 Lege dag Bottom Sheet
    selectedEmptyDate?.let { date ->
        val dateId = date.toIsoString()
        EmptyDayBottomSheet(
            date = date,
            onDismiss = { selectedEmptyDate = null },
            onChooseFromLibrary = {
                selectedEmptyDate = null
                dishPickerForDateId = dateId
            },
            onCreateNewDishForDay = {
                selectedEmptyDate = null
                onOpenNewDishForDay(dateId)
            },
            onPickForMe = {
                selectedEmptyDate = null
                onPickForMeDay(dateId)
            },
            onMarkSpecial = { kind, note ->
                selectedEmptyDate = null
                onMarkDaySpecial(dateId, kind, note)
            }
        )
    }

    // 5.2 Dagdetail Bottom Sheet
    selectedDetailDate?.let { date ->
        val dateId = date.toIsoString()
        val plan = uiState.allDays[dateId]
        if (plan == null || plan.dayKind == DayKind.LEEG) {
            selectedDetailDate = null
        } else {
            val linkedDish = plan.dishId?.let { id -> uiState.dishes.firstOrNull { it.id == id } }
            DayDetailBottomSheet(
                date = date,
                plan = plan,
                linkedDish = linkedDish,
                isPastWeek = uiState.isPastWeekSelected,
                onDismiss = { selectedDetailDate = null },
                onEditDish = { dishToEdit ->
                    selectedDetailDate = null
                    onOpenEditDish(dishToEdit)
                },
                onChooseOtherFromLibrary = {
                    selectedDetailDate = null
                    dishPickerForDateId = dateId
                },
                onPickForMeReplace = {
                    onPickForMeDay(dateId)
                },
                onClearDay = {
                    selectedDetailDate = null
                    onClearDay(dateId)
                },
                onMarkSpecial = { kind, note ->
                    selectedDetailDate = null
                    onMarkDaySpecial(dateId, kind, note)
                },
                onCopyToCurrentWeek = {
                    selectedDetailDate = null
                    copyPastDayDialogSource = plan
                }
            )
        }
    }

    // Gerecht kiezen uit bibliotheek voor specifieke dag
    dishPickerForDateId?.let { targetDateId ->
        val targetDate = MealPlannerLogic.parseIsoDate(targetDateId)
        DishPickerBottomSheet(
            targetDate = targetDate,
            dishes = uiState.dishes,
            dishTypes = uiState.dishTypes,
            onDismiss = { dishPickerForDateId = null },
            onSelectDish = { dish ->
                dishPickerForDateId = null
                onAssignDishToDay(targetDateId, dish)
            },
            onCreateNewDish = {
                dishPickerForDateId = null
                onOpenNewDishForDay(targetDateId)
            }
        )
    }

    // 5.1 Voorbije week: "Kopieer naar huidige week" dialoog
    copyPastDayDialogSource?.let { sourcePlan ->
        CopyToCurrentWeekDialog(
            sourcePlan = sourcePlan,
            currentWeekDates = uiState.currentWeekDates,
            allDays = uiState.allDays,
            onDismiss = { copyPastDayDialogSource = null },
            onSelectTargetDateId = { targetDateId ->
                copyPastDayDialogSource = null
                onCopyPastDayToCurrentWeek(sourcePlan, targetDateId)
            }
        )
    }
}

@Composable
fun WeekSwitcherHeaderCard(
    weekSunday: MealPlannerLogic.SimpleDate,
    isCurrentWeek: Boolean,
    isPastWeek: Boolean,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onJumpToCurrentWeek: () -> Unit,
    onGenerateWholeWeek: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onPreviousWeek,
                    modifier = Modifier.testTag("prev_week_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Vorige week"
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onJumpToCurrentWeek)
                        .padding(vertical = 4.dp)
                        .testTag("week_title_jump_today")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when {
                                isCurrentWeek -> "Deze week"
                                isPastWeek -> "Voorbije week (tik voor vandaag)"
                                else -> "Volgende week (tik voor vandaag)"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = MealPlannerLogic.formatWeekHeader(weekSunday),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onNextWeek,
                    modifier = Modifier.testTag("next_week_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Volgende week"
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onGenerateWholeWeek,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("generate_whole_week_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Casino,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Genereer hele week",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun DayRowCard(
    date: MealPlannerLogic.SimpleDate,
    plan: DayPlan?,
    isToday: Boolean,
    isPastWeek: Boolean,
    onRowClick: () -> Unit,
    onPickForMeClick: () -> Unit,
    onCopyToCurrentWeekClick: () -> Unit
) {
    val dayName = MealPlannerLogic.getDutchDayName(date)
    val dateFormatted = date.toBelgianString()
    val kind = plan?.dayKind ?: DayKind.LEEG

    // Altijd een egale (ondoorzichtige) kleur: een doorzichtige kaart toont de schaduw eronder
    // als een lichtere rechthoek. Daarom rekenen we de tint vooraf uit over de achtergrondkleur.
    val baseSurface = MaterialTheme.colorScheme.surface
    val containerColor = when {
        isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f).compositeOver(baseSurface)
        kind == DayKind.KOKEN -> baseSurface
        kind != DayKind.LEEG -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f).compositeOver(baseSurface)
        else -> baseSurface
    }

    val borderColor = if (isToday) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isToday) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(18.dp)
            )
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onRowClick)
            .testTag("day_row_${date.toIsoString()}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Links: Dagnaam + datum + gerecht/markering
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = dayName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = dateFormatted,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isToday) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = "Vandaag",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    when (kind) {
                        DayKind.LEEG -> {
                            Text(
                                text = "Nog leeg — tik om te plannen",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        DayKind.KOKEN -> {
                            val dishName = plan?.dishSnapshot?.name ?: "Gepland gerecht"
                            val dishType = plan?.dishSnapshot?.type ?: ""
                            Text(
                                text = dishName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (dishType.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = dishType,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        DayKind.AFHAAL, DayKind.OPWARM, DayKind.NIET_KOKEN -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = when (kind) {
                                        DayKind.AFHAAL -> Icons.Default.Fastfood
                                        DayKind.OPWARM -> Icons.Default.Kitchen
                                        else -> Icons.Default.EventBusy
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = kind.labelNl,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (!plan?.note.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = plan!!.note!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Rechts: Knop "Kies voor mij"
                FilledTonalButton(
                    onClick = onPickForMeClick,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("pick_for_me_${date.toIsoString()}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Casino,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Kies voor mij",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // 5.1 In een voorbije week toont elke dag met een gerecht een actie "Kopieer naar huidige week"
            if (isPastWeek && kind == DayKind.KOKEN) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onCopyToCurrentWeekClick,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("copy_to_current_week_${date.toIsoString()}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Kopieer naar huidige week",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

/**
 * 5.3 Lege dag Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmptyDayBottomSheet(
    date: MealPlannerLogic.SimpleDate,
    onDismiss: () -> Unit,
    onChooseFromLibrary: () -> Unit,
    onCreateNewDishForDay: () -> Unit,
    onPickForMe: () -> Unit,
    onMarkSpecial: (DayKind, String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedSpecialKind by remember { mutableStateOf<DayKind?>(null) }
    var noteText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "${MealPlannerLogic.getDutchDayName(date)} ${date.toBelgianString()}",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Wat eten Rob & Joke deze avond?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onChooseFromLibrary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("sheet_choose_from_library"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.MenuBook, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Gerecht uit bibliotheek kiezen")
            }

            FilledTonalButton(
                onClick = onPickForMe,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("sheet_pick_for_me"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Casino, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Kies voor mij")
            }

            OutlinedButton(
                onClick = onCreateNewDishForDay,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("sheet_new_dish_for_day"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Nieuw gerecht ingeven")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            Text(
                text = "Of markeer zonder koken:",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(DayKind.AFHAAL, DayKind.OPWARM, DayKind.NIET_KOKEN).forEach { kind ->
                    FilterChip(
                        selected = selectedSpecialKind == kind,
                        onClick = {
                            selectedSpecialKind = if (selectedSpecialKind == kind) null else kind
                        },
                        label = { Text(kind.labelNl) },
                        modifier = Modifier.testTag("chip_mark_${kind.wireValue}")
                    )
                }
            }

            AnimatedVisibility(visible = selectedSpecialKind != null) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("Optionele notitie (bv. 'Frituur', 'Restje lasagne', 'Uit eten')") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("special_day_note_input"),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            selectedSpecialKind?.let { kind ->
                                onMarkSpecial(kind, noteText)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("confirm_special_day_button")
                    ) {
                        Text("Opslaan als ${selectedSpecialKind?.labelNl ?: ""}")
                    }
                }
            }
        }
    }
}

/**
 * 5.2 Dagdetail Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DayDetailBottomSheet(
    date: MealPlannerLogic.SimpleDate,
    plan: DayPlan,
    linkedDish: Dish?,
    isPastWeek: Boolean,
    onDismiss: () -> Unit,
    onEditDish: (Dish) -> Unit,
    onChooseOtherFromLibrary: () -> Unit,
    onPickForMeReplace: () -> Unit,
    onClearDay: () -> Unit,
    onMarkSpecial: (DayKind, String?) -> Unit,
    onCopyToCurrentWeek: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uriHandler = LocalUriHandler.current
    val snapshot = plan.dishSnapshot
    var showMarkSpecialSection by remember { mutableStateOf(plan.dayKind != DayKind.KOKEN) }
    var selectedSpecialKind by remember {
        mutableStateOf(
            if (plan.dayKind in listOf(DayKind.AFHAAL, DayKind.OPWARM, DayKind.NIET_KOKEN)) plan.dayKind else DayKind.AFHAAL
        )
    }
    var noteInput by remember { mutableStateOf(plan.note ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "${MealPlannerLogic.getDutchDayName(date)} ${date.toBelgianString()}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            if (plan.dayKind == DayKind.KOKEN && snapshot != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = snapshot.name,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = snapshot.type,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // Foto indien aanwezig
                val photoUrl = linkedDish?.photoUrl ?: snapshot.photoUrl
                if (!photoUrl.isNullOrBlank()) {
                    DishPhotoThumbnail(
                        photoUrl = photoUrl,
                        contentDescription = snapshot.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(RoundedCornerShape(18.dp))
                    )
                }

                // Ingrediënten met hoeveelheid
                if (snapshot.ingredients.isNotEmpty()) {
                    Text(
                        text = "Ingrediënten",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            snapshot.ingredients.forEach { ing ->
                                val amountText = MealPlannerLogic.formatQuantityForDisplay(ing.quantity, ing.unit)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "• ${ing.name.ifBlank { ing.raw }}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (amountText.isNotBlank()) {
                                        Text(
                                            text = amountText,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Receptlink
                val recipeUrl = linkedDish?.recipeUrl ?: snapshot.recipeUrl
                if (!recipeUrl.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = {
                            val formattedUrl = if (recipeUrl.startsWith("http://") || recipeUrl.startsWith("https://")) {
                                recipeUrl
                            } else {
                                "https://$recipeUrl"
                            }
                            try {
                                uriHandler.openUri(formattedUrl)
                            } catch (_: Exception) {
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open receptlink", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                // Notitie
                val note = linkedDish?.note ?: snapshot.note
                if (!note.isNullOrBlank()) {
                    Text(
                        text = "Notitie: $note",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Afhaal / Opwarmmaaltijd / Niet koken
                Text(
                    text = plan.dayKind.labelNl,
                    style = MaterialTheme.typography.headlineMedium
                )
                if (!plan.note.isNullOrBlank()) {
                    Text(
                        text = plan.note,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Acties
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (plan.dayKind == DayKind.KOKEN) {
                    val editableDish = linkedDish ?: snapshot?.let {
                        Dish(
                            id = plan.dishId ?: "",
                            name = it.name,
                            type = it.type,
                            ingredients = it.ingredients,
                            recipeUrl = it.recipeUrl,
                            note = it.note,
                            photoUrl = it.photoUrl
                        )
                    }
                    if (editableDish != null) {
                        FilledTonalButton(
                            onClick = { onEditDish(editableDish) },
                            modifier = Modifier.testTag("detail_edit_dish_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Gerecht bewerken")
                        }
                    }
                }

                FilledTonalButton(
                    onClick = onPickForMeReplace,
                    modifier = Modifier.testTag("detail_pick_for_me_replace_button")
                ) {
                    Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Kies voor mij (vervangen)")
                }

                OutlinedButton(
                    onClick = onChooseOtherFromLibrary,
                    modifier = Modifier.testTag("detail_choose_other_button")
                ) {
                    Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ander gerecht kiezen")
                }

                if (isPastWeek && plan.dayKind == DayKind.KOKEN) {
                    Button(
                        onClick = onCopyToCurrentWeek,
                        modifier = Modifier.testTag("detail_copy_to_current_week_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kopieer naar huidige week")
                    }
                }

                OutlinedButton(
                    onClick = { showMarkSpecialSection = !showMarkSpecialSection },
                    modifier = Modifier.testTag("detail_toggle_special_button")
                ) {
                    Text("Markeren als Afhaal / Opwarm / Niet koken")
                }

                TextButton(
                    onClick = onClearDay,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("detail_clear_day_button")
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Dag leegmaken")
                }
            }

            AnimatedVisibility(visible = showMarkSpecialSection) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(DayKind.AFHAAL, DayKind.OPWARM, DayKind.NIET_KOKEN).forEach { k ->
                                FilterChip(
                                    selected = selectedSpecialKind == k,
                                    onClick = { selectedSpecialKind = k },
                                    label = { Text(k.labelNl) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = noteInput,
                            onValueChange = { noteInput = it },
                            label = { Text("Optionele notitie") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { onMarkSpecial(selectedSpecialKind, noteInput) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Opslaan als ${selectedSpecialKind.labelNl}")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DishPickerBottomSheet(
    targetDate: MealPlannerLogic.SimpleDate?,
    dishes: List<Dish>,
    dishTypes: List<String>,
    onDismiss: () -> Unit,
    onSelectDish: (Dish) -> Unit,
    onCreateNewDish: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf<String?>(null) }

    val filteredDishes = remember(dishes, searchQuery, selectedTypeFilter) {
        dishes.filter { dish ->
            val matchesQuery = searchQuery.isBlank() ||
                    dish.name.contains(searchQuery, ignoreCase = true) ||
                    dish.ingredients.any { it.name.contains(searchQuery, ignoreCase = true) }
            val matchesType = selectedTypeFilter == null ||
                    dish.type.equals(selectedTypeFilter, ignoreCase = true)
            matchesQuery && matchesType
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 360.dp, max = 620.dp)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Kies een gerecht",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    if (targetDate != null) {
                        Text(
                            text = "Voor ${MealPlannerLogic.getDutchDayName(targetDate)} ${targetDate.toBelgianString()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(onClick = onCreateNewDish) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nieuw")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Zoek op naam of ingrediënt...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Wis zoekopdracht")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTypeFilter == null,
                    onClick = { selectedTypeFilter = null },
                    label = { Text("Alle types") }
                )
                dishTypes.forEach { t ->
                    FilterChip(
                        selected = selectedTypeFilter.equals(t, ignoreCase = true),
                        onClick = {
                            selectedTypeFilter = if (selectedTypeFilter.equals(t, ignoreCase = true)) null else t
                        },
                        label = { Text(t) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (filteredDishes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (dishes.isEmpty()) "Nog geen gerechten in jullie bibliotheek." else "Geen gerechten gevonden.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = onCreateNewDish) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Nieuw gerecht ingeven")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredDishes, key = { it.id }) { dish ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectDish(dish) },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = dish.name,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    if (dish.ingredients.isNotEmpty()) {
                                        Text(
                                            text = "${dish.ingredients.size} ingrediënten",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = dish.type,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 5.1 Kopieer gerecht uit een voorbije week naar een te kiezen lege dag van de huidige week.
 */
@Composable
private fun CopyToCurrentWeekDialog(
    sourcePlan: DayPlan,
    currentWeekDates: List<MealPlannerLogic.SimpleDate>,
    allDays: Map<String, DayPlan>,
    onDismiss: () -> Unit,
    onSelectTargetDateId: (String) -> Unit
) {
    val dishName = sourcePlan.dishSnapshot?.name ?: "Gerecht"
    val emptyDatesInCurrentWeek = currentWeekDates.filter { date ->
        val plan = allDays[date.toIsoString()]
        plan == null || plan.dayKind == DayKind.LEEG
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Kopieer naar huidige week",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Kies een lege dag in de huidige week voor '$dishName':",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (emptyDatesInCurrentWeek.isEmpty()) {
                    Text(
                        text = "Er zijn geen lege dagen meer in de huidige week. Maak eerst een dag leeg in de huidige week.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    emptyDatesInCurrentWeek.forEach { date ->
                        val dateId = date.toIsoString()
                        OutlinedButton(
                            onClick = { onSelectTargetDateId(dateId) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("copy_target_day_$dateId"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("${MealPlannerLogic.getDutchDayName(date)} ${date.toBelgianString()}")
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

@Composable
fun DishPhotoThumbnail(
    photoUrl: String,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    if (photoUrl.startsWith("data:image")) {
        val bitmap = remember(photoUrl) { PhotoUtils.decodeDataUrlToBitmap(photoUrl) }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = ContentScale.Crop
            )
        }
    } else {
        AsyncImage(
            model = photoUrl,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    }
}
