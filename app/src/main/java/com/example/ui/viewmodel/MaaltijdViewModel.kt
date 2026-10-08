package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.IngredientNormalizerService
import com.example.data.model.AggregatedGroceryItem
import com.example.data.model.DEFAULT_DISH_TYPES
import com.example.data.model.DayKind
import com.example.data.model.DayPlan
import com.example.data.model.Dish
import com.example.data.model.Household
import com.example.data.model.IngredientItem
import com.example.data.model.LooseItem
import com.example.data.model.WeekChecklist
import com.example.data.repository.MaaltijdRepository
import com.example.domain.MealPlannerLogic
import com.example.util.PhotoUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class MaaltijdUiState(
    val isLoadingHousehold: Boolean = true,
    val household: Household? = null,
    val dishes: List<Dish> = emptyList(),
    val allDays: Map<String, DayPlan> = emptyMap(),
    val allWeeks: Map<String, WeekChecklist> = emptyMap(),
    val looseItems: List<LooseItem> = emptyList(),
    val selectedWeekSunday: MealPlannerLogic.SimpleDate = MealPlannerLogic.getSundayOfWeek(),
    val currentWeekSunday: MealPlannerLogic.SimpleDate = MealPlannerLogic.getSundayOfWeek(),
    val isOnline: Boolean = true,
    val isSyncingBackground: Boolean = false,
    val isSavingDish: Boolean = false,
    val isBusyHousehold: Boolean = false,
    val snackbarMessage: String? = null,
    val errorBanner: String? = null,
    val navigateToCreateDishForDateId: String? = null
) {
    val dishTypes: List<String>
        get() = household?.types?.ifEmpty { DEFAULT_DISH_TYPES } ?: DEFAULT_DISH_TYPES

    val selectedWeekDates: List<MealPlannerLogic.SimpleDate>
        get() = MealPlannerLogic.getWeekDates(selectedWeekSunday)

    val selectedWeekDateIds: List<String>
        get() = selectedWeekDates.map { it.toIsoString() }

    val currentWeekDates: List<MealPlannerLogic.SimpleDate>
        get() = MealPlannerLogic.getWeekDates(currentWeekSunday)

    val isPastWeekSelected: Boolean
        get() = selectedWeekSunday < currentWeekSunday

    val isCurrentWeekSelected: Boolean
        get() = selectedWeekSunday == currentWeekSunday

    val selectedWeekPlans: Map<String, DayPlan>
        get() = selectedWeekDateIds.mapNotNull { id -> allDays[id]?.let { id to it } }.toMap()

    val selectedWeekStartIso: String
        get() = selectedWeekSunday.toIsoString()

    val selectedWeekCheckedMap: Map<String, Boolean>
        get() = allWeeks[selectedWeekStartIso]?.checked ?: emptyMap()

    /**
     * 5.6 De lijst wordt automatisch berekend uit alle dagen met kind = 'koken' van die week.
     */
    val aggregatedGroceryItems: List<AggregatedGroceryItem>
        get() = MealPlannerLogic.aggregateGroceryList(
            weekDays = selectedWeekPlans.values,
            checkedMap = selectedWeekCheckedMap
        )

    val pendingNormalizationCount: Int
        get() = dishes.count { dish -> dish.needsNormalization || dish.ingredients.any { it.needsNormalization } }

    val pendingPhotoUploadCount: Int
        get() = dishes.count { it.pendingPhotoUpload }
}

class MaaltijdViewModel(
    private val repository: MaaltijdRepository,
    private val currentUserId: String,
    onlineFlow: Flow<Boolean>
) : ViewModel() {

    private val _uiState = MutableStateFlow(MaaltijdUiState())
    val uiState: StateFlow<MaaltijdUiState> = _uiState.asStateFlow()

    private var householdSubJob: Job? = null
    private var backgroundSyncJob: Job? = null

    init {
        // Observe connectivity
        viewModelScope.launch {
            onlineFlow.collect { online ->
                val wasOffline = !_uiState.value.isOnline
                _uiState.value = _uiState.value.copy(isOnline = online)
                if (online && wasOffline) {
                    triggerBackgroundNormalizationAndPhotoSync()
                }
            }
        }

        // Observe user's household
        viewModelScope.launch {
            repository.observeUserHousehold(currentUserId)
                .catch { e ->
                    Log.w(TAG, "Error observing household", e)
                    _uiState.value = _uiState.value.copy(
                        isLoadingHousehold = false,
                        errorBanner = e.localizedMessage ?: "Kon huishouden niet laden."
                    )
                }
                .collect { household ->
                    val prevHid = _uiState.value.household?.id
                    _uiState.value = _uiState.value.copy(
                        isLoadingHousehold = false,
                        household = household,
                        errorBanner = null
                    )
                    if (household != null && household.id != prevHid) {
                        subscribeToHouseholdCollections(household.id)
                    } else if (household == null) {
                        householdSubJob?.cancel()
                    }
                }
        }
    }

    private fun subscribeToHouseholdCollections(hid: String) {
        householdSubJob?.cancel()
        householdSubJob = viewModelScope.launch {
            combine(
                repository.observeDishes(hid),
                repository.observeAllDays(hid),
                repository.observeAllWeeks(hid),
                repository.observeLooseItems(hid)
            ) { dishes, days, weeks, looseItems ->
                Quadruple(dishes, days, weeks, looseItems)
            }
                .catch { e ->
                    Log.w(TAG, "Error observing household subcollections", e)
                    _uiState.value = _uiState.value.copy(
                        errorBanner = e.localizedMessage ?: "Fout bij synchroniseren van gegevens."
                    )
                }
                .collect { (dishes, days, weeks, looseItems) ->
                    _uiState.value = _uiState.value.copy(
                        dishes = dishes,
                        allDays = days,
                        allWeeks = weeks,
                        looseItems = looseItems
                    )
                    if (_uiState.value.isOnline &&
                        (dishes.any { it.needsNormalization || it.ingredients.any { ing -> ing.needsNormalization } } ||
                                dishes.any { it.pendingPhotoUpload })
                    ) {
                        triggerBackgroundNormalizationAndPhotoSync()
                    }
                }
        }
    }

    /**
     * 6.6 & 7: Zodra er verbinding is, draait een achtergrondtaak die alle gerechten met
     * needsNormalization alsnog normaliseert en foto's in de wachtrij uploadt.
     * De lijst en de dagsnapshots worden daarna bijgewerkt.
     */
    private fun triggerBackgroundNormalizationAndPhotoSync() {
        if (backgroundSyncJob?.isActive == true) return
        val state = _uiState.value
        val hid = state.household?.id ?: return
        if (!state.isOnline) return

        val dishesToSync = state.dishes.filter { dish ->
            dish.needsNormalization ||
                    dish.ingredients.any { it.needsNormalization } ||
                    dish.pendingPhotoUpload
        }
        if (dishesToSync.isEmpty()) return

        backgroundSyncJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncingBackground = true)
            try {
                for (dish in dishesToSync) {
                    if (!_uiState.value.isOnline) break

                    var updatedIngredients = dish.ingredients
                    var stillNeedsNorm = dish.needsNormalization

                    if (dish.needsNormalization || dish.ingredients.any { it.needsNormalization }) {
                        val rawLines = dish.ingredients.map { it.raw }
                        val normalized = IngredientNormalizerService.normalizeIngredients(
                            rawLines = rawLines,
                            isOnline = true
                        )
                        if (normalized.none { it.needsNormalization }) {
                            updatedIngredients = normalized
                            stillNeedsNorm = false
                        }
                    }

                    var updatedPhotoUrl = dish.photoUrl
                    var stillPendingPhoto = dish.pendingPhotoUpload
                    if (dish.pendingPhotoUpload && !dish.photoUrl.isNullOrBlank()) {
                        if (dish.photoUrl.startsWith("file:")) {
                            val syncedDataUrl = PhotoUtils.convertLocalFileToSyncedDataUrl(dish.photoUrl)
                            if (syncedDataUrl != null) {
                                updatedPhotoUrl = syncedDataUrl
                                stillPendingPhoto = false
                            }
                        } else {
                            stillPendingPhoto = false
                        }
                    }

                    if (!stillNeedsNorm || !stillPendingPhoto) {
                        val updatedDish = dish.copy(
                            ingredients = updatedIngredients,
                            needsNormalization = stillNeedsNorm,
                            photoUrl = updatedPhotoUrl,
                            pendingPhotoUpload = stillPendingPhoto
                        )
                        val linkedDayIds = _uiState.value.allDays.values
                            .filter { it.dayKind == DayKind.KOKEN && it.dishId == dish.id }
                            .map { it.dateId }
                        repository.updateDishAndLinkedDaySnapshots(hid, updatedDish, linkedDayIds)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background sync failed", e)
            } finally {
                _uiState.value = _uiState.value.copy(isSyncingBackground = false)
            }
        }
    }

    // --- Huishouden acties ---

    fun createNewHousehold() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusyHousehold = true, errorBanner = null)
            val result = repository.createHousehold()
            _uiState.value = _uiState.value.copy(isBusyHousehold = false)
            result.onSuccess { hh ->
                showSnackbar("Huishouden aangemaakt! Uitnodigingscode: ${hh.inviteCode}")
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    errorBanner = e.localizedMessage ?: "Kon huishouden niet aanmaken."
                )
            }
        }
    }

    fun joinHouseholdWithCode(inviteCode: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusyHousehold = true, errorBanner = null)
            val result = repository.joinHouseholdByInviteCode(inviteCode)
            _uiState.value = _uiState.value.copy(isBusyHousehold = false)
            result.onSuccess { code ->
                // Explicitly subscribe right away while query snapshot updates
                subscribeToHouseholdCollections(code)
                showSnackbar("Welkom! Je bent nu lid van huishouden $code.")
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    errorBanner = "Kon niet deelnemen met code '${inviteCode.uppercase()}'. Controleer of de code klopt en of het huishouden nog geen 2 leden telt."
                )
            }
        }
    }

    // --- Weeknavigatie ---

    fun selectPreviousWeek() {
        val current = _uiState.value.selectedWeekSunday
        _uiState.value = _uiState.value.copy(
            selectedWeekSunday = MealPlannerLogic.addDays(current, -7)
        )
    }

    fun selectNextWeek() {
        val current = _uiState.value.selectedWeekSunday
        _uiState.value = _uiState.value.copy(
            selectedWeekSunday = MealPlannerLogic.addDays(current, 7)
        )
    }

    fun jumpToCurrentWeek() {
        val nowSunday = MealPlannerLogic.getSundayOfWeek()
        _uiState.value = _uiState.value.copy(
            selectedWeekSunday = nowSunday,
            currentWeekSunday = nowSunday
        )
    }

    // --- 6.2 "Kies voor mij" (per dag) ---

    fun pickDishForSingleDay(dateId: String, onEmptyLibraryNavigate: () -> Unit) {
        val state = _uiState.value
        val hid = state.household?.id ?: return
        val weekDateIds = MealPlannerLogic.getWeekDates(
            MealPlannerLogic.getSundayOfWeek(MealPlannerLogic.parseIsoDate(dateId) ?: MealPlannerLogic.today())
        ).map { it.toIsoString() }
        val weekPlans = weekDateIds.mapNotNull { id -> state.allDays[id]?.let { id to it } }.toMap()

        when (val pickResult = MealPlannerLogic.pickDishForDay(dateId, weekPlans, state.dishes)) {
            MealPlannerLogic.PickResult.EmptyLibrary -> {
                showSnackbar("Voeg eerst gerechten toe")
                onEmptyLibraryNavigate()
            }
            is MealPlannerLogic.PickResult.Chosen -> {
                viewModelScope.launch {
                    val res = repository.setDayCooking(hid, dateId, pickResult.dish)
                    res.onSuccess {
                        if (pickResult.ruleIgnored) {
                            showSnackbar("Geen vrij type meer, regel genegeerd voor deze dag")
                        }
                    }.onFailure { e ->
                        showSnackbar(e.localizedMessage ?: "Kon gerecht niet inplannen.")
                    }
                }
            }
        }
    }

    // --- 6.3 "Genereer hele week" ---

    fun generateWholeSelectedWeek(onEmptyLibraryNavigate: () -> Unit) {
        val state = _uiState.value
        val hid = state.household?.id ?: return
        if (state.dishes.isEmpty()) {
            showSnackbar("Voeg eerst gerechten toe")
            onEmptyLibraryNavigate()
            return
        }

        val weekDateIds = state.selectedWeekDateIds
        val existingPlans = state.selectedWeekPlans
        val result = MealPlannerLogic.generateWholeWeek(
            weekDateIds = weekDateIds,
            existingWeekPlans = existingPlans,
            allDishes = state.dishes
        )

        if (result.assignments.isEmpty()) {
            showSnackbar("Alle dagen in deze week zijn al ingevuld.")
            return
        }

        viewModelScope.launch {
            val writeRes = repository.setGeneratedWeekBatch(hid, result.assignments)
            writeRes.onSuccess {
                if (result.anyRuleIgnored) {
                    showSnackbar("Week gegenereerd (geen vrij type meer op sommige dagen, regel genegeerd).")
                } else {
                    showSnackbar("${result.assignments.size} lege dag(en) ingevuld!")
                }
            }.onFailure { e ->
                showSnackbar(e.localizedMessage ?: "Kon week niet genereren.")
            }
        }
    }

    // --- Dag plannen / markeren / leegmaken / kopiëren naar huidige week ---

    fun assignDishToDay(dateId: String, dish: Dish) {
        val hid = _uiState.value.household?.id ?: return
        viewModelScope.launch {
            repository.setDayCooking(hid, dateId, dish)
                .onFailure { e -> showSnackbar(e.localizedMessage ?: "Fout bij plannen van gerecht.") }
        }
    }

    fun markDaySpecial(dateId: String, kind: DayKind, note: String?) {
        val hid = _uiState.value.household?.id ?: return
        viewModelScope.launch {
            repository.setDayMarker(hid, dateId, kind, note)
                .onFailure { e -> showSnackbar(e.localizedMessage ?: "Fout bij markeren van dag.") }
        }
    }

    fun clearDay(dateId: String) {
        val hid = _uiState.value.household?.id ?: return
        viewModelScope.launch {
            repository.clearDay(hid, dateId)
                .onFailure { e -> showSnackbar(e.localizedMessage ?: "Fout bij leegmaken van dag.") }
        }
    }

    /**
     * 5.1 In een voorbije week toont elke dag een actie "Kopieer naar huidige week",
     * die het gerecht op een te kiezen lege dag van de huidige week zet.
     */
    fun copyPastDayToCurrentWeekDay(sourceDay: DayPlan, targetCurrentWeekDateId: String) {
        val hid = _uiState.value.household?.id ?: return
        val snapshot = sourceDay.dishSnapshot ?: return
        // Probeer het huidige gerecht uit de bibliotheek te gebruiken als het nog bestaat, anders de snapshot
        val libraryDish = sourceDay.dishId?.let { id -> _uiState.value.dishes.firstOrNull { it.id == id } }
        val dishToPlan = libraryDish ?: Dish(
            id = sourceDay.dishId ?: "copied_${System.currentTimeMillis()}",
            name = snapshot.name,
            type = snapshot.type,
            ingredients = snapshot.ingredients,
            recipeUrl = snapshot.recipeUrl,
            note = snapshot.note,
            photoUrl = snapshot.photoUrl
        )
        viewModelScope.launch {
            repository.setDayCooking(hid, targetCurrentWeekDateId, dishToPlan)
                .onSuccess {
                    val targetDate = MealPlannerLogic.parseIsoDate(targetCurrentWeekDateId)
                    val dayLabel = targetDate?.let { MealPlannerLogic.getDutchDayName(it) } ?: targetCurrentWeekDateId
                    showSnackbar("'${dishToPlan.name}' gekopieerd naar $dayLabel (huidige week)!")
                }
                .onFailure { e ->
                    showSnackbar(e.localizedMessage ?: "Kopiëren naar huidige week mislukt.")
                }
        }
    }

    // --- Gerechtenbibliotheek & Gerecht opslaan met Gemini normalisatie ---

    /**
     * Slaat een gerecht op:
     * - Als de gebruiker nieuwe/gewijzigde ruwe ingrediëntregels heeft die nog niet handmatig
     *   aangepast zijn, normaliseert Gemini (Firebase AI Logic) deze.
     * - Indien offline of bij falen zet het `name = lowercase getrimde raw, quantity = null, unit = null, needsNormalization = true`.
     * - Als `assignToDateIdAfterSave != null` (vanuit Lege dag -> Nieuw gerecht ingeven, 5.3),
     *   wordt het gerecht na opslaan direct op die dag gezet!
     */
    fun saveDishWithNormalization(
        existingDishId: String?,
        name: String,
        type: String,
        draftIngredients: List<DraftIngredientLine>,
        recipeUrl: String?,
        note: String?,
        processedPhoto: PhotoUtils.ProcessedPhoto?,
        existingPhotoUrl: String?,
        assignToDateIdAfterSave: String? = null,
        prepMinutes: Int? = null,
        onSavedSuccess: () -> Unit
    ) {
        val state = _uiState.value
        val hid = state.household?.id ?: run {
            // Zonder huishouden is er geen pad om naar te schrijven. Vroeger keerde de functie hier stil terug,
            // waardoor Opslaan leek te "doen niets". Nu krijgt de gebruiker een duidelijke melding.
            showSnackbar("Opslaan mislukt: er is nog geen huishouden geladen. Probeer het zo opnieuw.")
            return
        }
        if (name.isBlank() || type.isBlank()) {
            showSnackbar("Naam en type zijn verplicht.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingDish = true)
            try {
                // Voeg nieuw type automatisch toe aan huishouden indien nog niet aanwezig
                val cleanType = type.trim().lowercase()
                if (cleanType !in state.dishTypes.map { it.lowercase() }) {
                    repository.updateHouseholdTypes(hid, state.dishTypes + cleanType)
                }

                // Eén call per opgeslagen gerecht met alle raw-regels in één array (tenzij alle regels handmatig zijn aangepast)
                val nonEmptyDrafts = draftIngredients.filter { it.raw.isNotBlank() }
                val allRawLines = nonEmptyDrafts.map { it.raw.trim() }
                val needsAiCall = nonEmptyDrafts.any { !it.manuallyEdited || it.normalizedName.isBlank() }

                val aiNormalizedList: List<IngredientItem> = if (needsAiCall && allRawLines.isNotEmpty()) {
                    IngredientNormalizerService.normalizeIngredients(
                        rawLines = allRawLines,
                        isOnline = _uiState.value.isOnline
                    )
                } else {
                    emptyList()
                }

                val finalIngredients = nonEmptyDrafts.mapIndexed { idx, draft ->
                    if (draft.manuallyEdited && draft.normalizedName.isNotBlank()) {
                        val qty = draft.quantityText.replace(',', '.').trim().toDoubleOrNull()
                        val unit = draft.unitText.trim().lowercase().takeIf { it.isNotEmpty() }
                        val converted = MealPlannerLogic.convertToCanonicalUnit(qty, unit)
                        IngredientItem(
                            raw = draft.raw.trim(),
                            name = draft.normalizedName.trim().lowercase(),
                            quantity = converted.quantity,
                            unit = if (converted.quantity != null) converted.canonicalUnit else null,
                            needsNormalization = false
                        )
                    } else {
                        aiNormalizedList.getOrNull(idx) ?: MealPlannerLogic.createOfflineFallbackIngredient(draft.raw)
                    }
                }

                val isOnlineNow = _uiState.value.isOnline
                val finalPhotoUrl = when {
                    processedPhoto != null -> if (isOnlineNow) processedPhoto.syncedDataUrl else processedPhoto.localFileUri
                    else -> existingPhotoUrl
                }
                val pendingPhoto = processedPhoto != null && !isOnlineNow
                val needsNorm = finalIngredients.any { it.needsNormalization }

                val saveRes = repository.saveDish(
                    hid = hid,
                    existingDishId = existingDishId,
                    name = name,
                    type = cleanType,
                    ingredients = finalIngredients,
                    recipeUrl = recipeUrl,
                    note = note,
                    photoUrl = finalPhotoUrl,
                    needsNormalization = needsNorm,
                    pendingPhotoUpload = pendingPhoto,
                    prepMinutes = prepMinutes
                )

                saveRes.onSuccess { savedDish ->
                    if (!assignToDateIdAfterSave.isNullOrBlank()) {
                        repository.setDayCooking(hid, assignToDateIdAfterSave, savedDish)
                    } else if (!existingDishId.isNullOrBlank()) {
                        // Werk ook gekoppelde dagen van huidige/toekomstige weken bij met de nieuwe snapshot
                        val linkedDays = _uiState.value.allDays.values
                            .filter { it.dayKind == DayKind.KOKEN && it.dishId == savedDish.id }
                            .map { it.dateId }
                        if (linkedDays.isNotEmpty()) {
                            repository.updateDishAndLinkedDaySnapshots(hid, savedDish, linkedDays)
                        }
                    }

                    if (needsNorm) {
                        showSnackbar("Gerecht opgeslagen (ingrediënten worden genormaliseerd zodra er verbinding is).")
                    } else {
                        showSnackbar("Gerecht '${savedDish.name}' opgeslagen!")
                    }
                    onSavedSuccess()
                }.onFailure { e ->
                    showSnackbar(e.localizedMessage ?: "Fout bij opslaan van gerecht.")
                }
            } finally {
                _uiState.value = _uiState.value.copy(isSavingDish = false)
            }
        }
    }

    suspend fun previewNormalizeIngredients(rawLines: List<String>): List<IngredientItem> {
        return IngredientNormalizerService.normalizeIngredients(
            rawLines = rawLines,
            isOnline = _uiState.value.isOnline
        )
    }

    fun setDishRating(dish: Dish, rating: Int) {
        val hid = _uiState.value.household?.id ?: return
        viewModelScope.launch {
            repository.setDishRating(hid, dish.id, rating)
                .onFailure { showSnackbar(it.localizedMessage ?: "Waardering opslaan mislukt.") }
        }
    }

    fun deleteDish(dish: Dish) {
        val hid = _uiState.value.household?.id ?: return
        viewModelScope.launch {
            repository.deleteDish(hid, dish.id)
                .onSuccess {
                    showSnackbar("'${dish.name}' verwijderd uit bibliotheek (bestaande weekplanningen blijven behouden).")
                }
                .onFailure { e ->
                    showSnackbar(e.localizedMessage ?: "Kon gerecht niet verwijderen.")
                }
        }
    }

    // --- Types beheren (toevoegen, hernoemen, verwijderen met blokkering indien in gebruik) ---

    fun addDishType(newType: String) {
        val state = _uiState.value
        val hid = state.household?.id ?: return
        val clean = newType.trim().lowercase()
        if (clean.isEmpty()) return
        if (clean in state.dishTypes.map { it.lowercase() }) {
            showSnackbar("Type '$clean' bestaat al.")
            return
        }
        viewModelScope.launch {
            repository.updateHouseholdTypes(hid, state.dishTypes + clean)
                .onSuccess { showSnackbar("Type '$clean' toegevoegd.") }
                .onFailure { e -> showSnackbar(e.localizedMessage ?: "Kon type niet toevoegen.") }
        }
    }

    fun renameDishType(oldType: String, newType: String) {
        val state = _uiState.value
        val hid = state.household?.id ?: return
        val cleanNew = newType.trim().lowercase()
        if (cleanNew.isEmpty() || cleanNew == oldType.lowercase()) return
        val updatedList = state.dishTypes.map { if (it.equals(oldType, ignoreCase = true)) cleanNew else it }.distinct()
        viewModelScope.launch {
            repository.updateHouseholdTypes(hid, updatedList, renamedPair = oldType to cleanNew)
                .onSuccess { showSnackbar("Type '$oldType' hernoemd naar '$cleanNew'.") }
                .onFailure { e -> showSnackbar(e.localizedMessage ?: "Kon type niet hernoemen.") }
        }
    }

    fun deleteDishType(typeToRemove: String) {
        val state = _uiState.value
        val hid = state.household?.id ?: return
        val dishesUsingType = state.dishes.count { it.type.equals(typeToRemove, ignoreCase = true) }
        if (dishesUsingType > 0) {
            showSnackbar("Kan '$typeToRemove' niet verwijderen: nog in gebruik bij $dishesUsingType gerecht(en).")
            return
        }
        if (state.dishTypes.size <= 1) {
            showSnackbar("Er moet minstens één gerechttype overblijven.")
            return
        }
        val updatedList = state.dishTypes.filterNot { it.equals(typeToRemove, ignoreCase = true) }
        viewModelScope.launch {
            repository.updateHouseholdTypes(hid, updatedList)
                .onSuccess { showSnackbar("Type '$typeToRemove' verwijderd.") }
                .onFailure { e -> showSnackbar(e.localizedMessage ?: "Kon type niet verwijderen.") }
        }
    }

    // --- 5.6 Boodschappenlijst & Losse items ---

    fun toggleAggregatedItemChecked(itemKey: String, checked: Boolean) {
        val state = _uiState.value
        val hid = state.household?.id ?: return
        val weekStart = state.selectedWeekStartIso
        val currentMap = state.selectedWeekCheckedMap
        viewModelScope.launch {
            repository.setGroceryItemChecked(hid, weekStart, itemKey, checked, currentMap)
                .onFailure { e -> showSnackbar(e.localizedMessage ?: "Kon afvinkstatus niet bijwerken.") }
        }
    }

    fun addLooseItem(name: String, quantityText: String, unitText: String, recurring: Boolean) {
        val hid = _uiState.value.household?.id ?: return
        if (name.isBlank()) return
        val qty = quantityText.replace(',', '.').trim().toDoubleOrNull()
        val unit = unitText.trim().takeIf { it.isNotEmpty() }
        viewModelScope.launch {
            repository.addLooseItem(hid, name, qty, unit, recurring)
                .onFailure { e -> showSnackbar(e.localizedMessage ?: "Kon los item niet toevoegen.") }
        }
    }

    fun toggleLooseItemChecked(itemId: String, checked: Boolean) {
        val hid = _uiState.value.household?.id ?: return
        viewModelScope.launch {
            repository.toggleLooseItemChecked(hid, itemId, checked)
                .onFailure { e -> showSnackbar(e.localizedMessage ?: "Kon los item niet afvinken.") }
        }
    }

    fun deleteLooseItem(itemId: String) {
        val hid = _uiState.value.household?.id ?: return
        viewModelScope.launch {
            repository.deleteLooseItem(hid, itemId)
                .onFailure { e -> showSnackbar(e.localizedMessage ?: "Kon los item niet verwijderen.") }
        }
    }

    fun finishShopping() {
        val state = _uiState.value
        val hid = state.household?.id ?: return
        val checkedLoose = state.looseItems.filter { it.checked }
        if (checkedLoose.isEmpty()) {
            showSnackbar("Geen afgevinkte losse items om af te ronden.")
            return
        }
        viewModelScope.launch {
            repository.finishShopping(hid, state.looseItems)
                .onSuccess {
                    val removedCount = checkedLoose.count { !it.recurring }
                    val resetCount = checkedLoose.count { it.recurring }
                    showSnackbar("Winkelen afgerond ($removedCount eenmalig verwijderd, $resetCount vast teruggezet).")
                }
                .onFailure { e ->
                    showSnackbar(e.localizedMessage ?: "Kon winkelen niet afronden.")
                }
        }
    }

    fun showSnackbar(message: String) {
        _uiState.value = _uiState.value.copy(snackbarMessage = message)
    }

    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    data class DraftIngredientLine(
        val raw: String = "",
        val normalizedName: String = "",
        val quantityText: String = "",
        val unitText: String = "",
        val manuallyEdited: Boolean = false
    )

    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )

    private companion object {
        const val TAG = "MaaltijdVM"
    }
}
