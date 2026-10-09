package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.firebase.configureAppCheck
import com.example.data.model.Dish
import com.example.data.repository.MaaltijdRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.HouseholdInfoDialog
import com.example.ui.auth.HouseholdSetupScreen
import com.example.ui.auth.signOut
import com.example.ui.screens.DishEditorScreen
import com.example.ui.screens.DishesLibraryScreen
import com.example.ui.screens.GroceryListScreen
import com.example.ui.screens.WeekCalendarScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MaaltijdViewModel
import com.example.util.NetworkMonitor
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Mandatory App Check initialization with intent before setContent / ViewModels
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AppRootNavigation()
            }
        }
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

enum class MainTab(
    val route: String,
    val titleNl: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    WEEK("week", "Week", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    GERECHTEN("gerechten", "Gerechten", Icons.Filled.MenuBook, Icons.Outlined.MenuBook),
    BOODSCHAPPEN("boodschappen", "Boodschappen", Icons.Filled.ShoppingCart, Icons.Outlined.ShoppingCart)
}

@Composable
fun AppRootNavigation() {
    val auth = remember { FirebaseAuth.getInstance() }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(
            onAuthSuccess = {}
        )
    } else {
        AuthenticatedAppContent(
            currentUserId = user.uid,
            userEmail = user.email
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatedAppContent(
    currentUserId: String,
    userEmail: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    // Keyed by currentUserId so switching accounts creates a fresh ViewModel instance
    val viewModel: MaaltijdViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val onlineFlow = NetworkMonitor.observeOnlineStatus(app)
                MaaltijdViewModel(
                    repository = MaaltijdRepository(db),
                    currentUserId = currentUserId,
                    onlineFlow = onlineFlow
                )
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    var activeTab by remember { mutableStateOf(MainTab.WEEK) }
    var showHouseholdDialog by remember { mutableStateOf(false) }

    // State voor 5.4 Gerecht bewerken / nieuw
    var isEditorOpen by remember { mutableStateOf(false) }
    var editingDish by remember { mutableStateOf<Dish?>(null) }
    var assignToDateIdOnSave by remember { mutableStateOf<String?>(null) }

    when {
        uiState.isLoadingHousehold -> {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.padding(8.dp))
                        Text(
                            text = "Huishouden laden...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        uiState.household == null -> {
            HouseholdSetupScreen(
                userEmail = userEmail,
                isBusy = uiState.isBusyHousehold,
                errorMessage = uiState.errorBanner,
                onCreateHousehold = { viewModel.createNewHousehold() },
                onJoinHousehold = { code -> viewModel.joinHouseholdWithCode(code) },
                onSignOut = {
                    signOut(
                        credentialManager = credentialManager,
                        onSignOutComplete = {},
                        scope = scope
                    )
                }
            )
        }
        isEditorOpen -> {
            // De editor staat buiten de hoofd-Scaffold, dus had hij geen SnackbarHost: foutmeldingen bij
            // opslaan (bv. PERMISSION_DENIED) werden getoond aan niemand. Deze Box legt er één overheen.
            Box(modifier = Modifier.fillMaxSize()) {
                DishEditorScreen(
                    existingDish = editingDish,
                    dishTypes = uiState.dishTypes,
                    assignToDateIdAfterSave = assignToDateIdOnSave,
                    isSaving = uiState.isSavingDish,
                    onPreviewNormalize = { lines -> viewModel.previewNormalizeIngredients(lines) },
                    onImportRecipe = { url -> viewModel.importRecipeFromUrl(url) },
                    onSaveDish = { existingId, name, type, drafts, recipeUrl, note, photo, existingPhotoUrl, assignDateId, prepMinutes ->
                        viewModel.saveDishWithNormalization(
                            existingDishId = existingId,
                            name = name,
                            type = type,
                            draftIngredients = drafts,
                            recipeUrl = recipeUrl,
                            note = note,
                            processedPhoto = photo,
                            existingPhotoUrl = existingPhotoUrl,
                            assignToDateIdAfterSave = assignDateId,
                            prepMinutes = prepMinutes,
                            onSavedSuccess = {
                                isEditorOpen = false
                                editingDish = null
                                assignToDateIdOnSave = null
                            }
                        )
                    },
                    onCancel = {
                        isEditorOpen = false
                        editingDish = null
                        assignToDateIdOnSave = null
                    }
                )
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 88.dp)
                )
            }
        }
        else -> {
            val household = uiState.household!!
            val uncheckedGroceriesCount = uiState.aggregatedGroceryItems.count { !it.checked } +
                    uiState.looseItems.count { !it.checked }

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Maaltijdplanner",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                // 7. Discreet offline-icoon en "synchroniseren"-indicator
                                AnimatedVisibility(visible = !uiState.isOnline) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.errorContainer
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CloudOff,
                                                contentDescription = "Offline",
                                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Offline",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                }
                                AnimatedVisibility(
                                    visible = uiState.isSyncingBackground ||
                                            uiState.pendingNormalizationCount > 0 ||
                                            uiState.pendingPhotoUploadCount > 0
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Sync,
                                                contentDescription = "Synchroniseren",
                                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (uiState.isSyncingBackground) "Synchroniseren..." else "Wachtrij",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        actions = {
                            FilledTonalButton(
                                onClick = { showHouseholdDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .testTag("household_info_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.People,
                                    contentDescription = "Huishouden en uitnodigingscode",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (household.members.size >= 2) {
                                        "Rob & Joke (2/2)"
                                    } else {
                                        "Code: ${household.inviteCode}"
                                    },
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                },
                bottomBar = {
                    NavigationBar {
                        MainTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { activeTab = tab },
                                icon = {
                                    if (tab == MainTab.BOODSCHAPPEN && uncheckedGroceriesCount > 0) {
                                        BadgedBox(
                                            badge = {
                                                Badge {
                                                    Text(uncheckedGroceriesCount.toString())
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.titleNl
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.titleNl
                                        )
                                    }
                                },
                                label = { Text(tab.titleNl) },
                                modifier = Modifier.testTag("nav_tab_${tab.route}")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (activeTab) {
                        MainTab.WEEK -> {
                            WeekCalendarScreen(
                                uiState = uiState,
                                onPreviousWeek = { viewModel.selectPreviousWeek() },
                                onNextWeek = { viewModel.selectNextWeek() },
                                onJumpToCurrentWeek = { viewModel.jumpToCurrentWeek() },
                                onGenerateWholeWeek = {
                                    viewModel.generateWholeSelectedWeek(
                                        onEmptyLibraryNavigate = {
                                            editingDish = null
                                            assignToDateIdOnSave = null
                                            isEditorOpen = true
                                        }
                                    )
                                },
                                onPickForMeDay = { dateId ->
                                    viewModel.pickDishForSingleDay(
                                        dateId = dateId,
                                        onEmptyLibraryNavigate = {
                                            editingDish = null
                                            assignToDateIdOnSave = dateId
                                            isEditorOpen = true
                                        }
                                    )
                                },
                                onAssignDishToDay = { dateId, dish ->
                                    viewModel.assignDishToDay(dateId, dish)
                                },
                                onMarkDaySpecial = { dateId, kind, note ->
                                    viewModel.markDaySpecial(dateId, kind, note)
                                },
                                onClearDay = { dateId ->
                                    viewModel.clearDay(dateId)
                                },
                                onSwapDays = { dateA, dateB -> viewModel.swapDays(dateA, dateB) },
                                onCopyPastDayToCurrentWeek = { sourcePlan, targetDateId ->
                                    viewModel.copyPastDayToCurrentWeekDay(sourcePlan, targetDateId)
                                },
                                onOpenNewDishForDay = { dateId ->
                                    editingDish = null
                                    assignToDateIdOnSave = dateId
                                    isEditorOpen = true
                                },
                                onOpenEditDish = { dish ->
                                    editingDish = dish
                                    assignToDateIdOnSave = null
                                    isEditorOpen = true
                                }
                            )
                        }
                        MainTab.GERECHTEN -> {
                            DishesLibraryScreen(
                                uiState = uiState,
                                onOpenNewDish = {
                                    editingDish = null
                                    assignToDateIdOnSave = null
                                    isEditorOpen = true
                                },
                                onOpenEditDish = { dish ->
                                    editingDish = dish
                                    assignToDateIdOnSave = null
                                    isEditorOpen = true
                                },
                                onDeleteDish = { dish ->
                                    viewModel.deleteDish(dish)
                                },
                                onRateDish = { dish, rating -> viewModel.setDishRating(dish, rating) },
                                onAddType = { newType -> viewModel.addDishType(newType) },
                                onRenameType = { oldType, newType -> viewModel.renameDishType(oldType, newType) },
                                onDeleteType = { typeToRemove -> viewModel.deleteDishType(typeToRemove) }
                            )
                        }
                        MainTab.BOODSCHAPPEN -> {
                            GroceryListScreen(
                                uiState = uiState,
                                onPreviousWeek = { viewModel.selectPreviousWeek() },
                                onNextWeek = { viewModel.selectNextWeek() },
                                onJumpToCurrentWeek = { viewModel.jumpToCurrentWeek() },
                                onToggleAggregatedItem = { itemKey, checked ->
                                    viewModel.toggleAggregatedItemChecked(itemKey, checked)
                                },
                                onAddLooseItem = { name, qty, unit, recurring ->
                                    viewModel.addLooseItem(name, qty, unit, recurring)
                                },
                                onToggleLooseItem = { itemId, checked ->
                                    viewModel.toggleLooseItemChecked(itemId, checked)
                                },
                                onDeleteLooseItem = { itemId ->
                                    viewModel.deleteLooseItem(itemId)
                                },
                                onFinishShopping = {
                                    viewModel.finishShopping()
                                }
                            )
                        }
                    }
                }
            }

            if (showHouseholdDialog) {
                HouseholdInfoDialog(
                    household = household,
                    userEmail = userEmail,
                    onDismiss = { showHouseholdDialog = false },
                    onSignOut = {
                        signOut(
                            credentialManager = credentialManager,
                            onSignOutComplete = {},
                            scope = scope
                        )
                    }
                )
            }
        }
    }
}
