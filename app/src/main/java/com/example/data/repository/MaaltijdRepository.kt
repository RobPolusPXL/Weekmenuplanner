package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model. DEFAULT_DISH_TYPES
import com.example.data.model.DayKind
import com.example.data.model.DayPlan
import com.example.data.model.Dish
import com.example.data.model.Household
import com.example.data.model.IngredientItem
import com.example.data.model.LooseItem
import com.example.data.model.WeekChecklist
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class MaaltijdRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    // CRITICAL: Always initialize with R.string.firestore_database_id
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Gebruiker moet ingelogd zijn met Google om Firestore te gebruiken.")
    }

    // --- 1. Huishouden & Uitnodigingscode ---

    fun observeUserHousehold(uid: String): Flow<Household?> {
        val path = "households"
        return db.collection(path)
            .whereArrayContains("members", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.firstNotNullOfOrNull { Household.fromDocument(it) }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeHouseholdById(hid: String): Flow<Household?> {
        val cleanHid = hid.trim().uppercase()
        val path = "households/$cleanHid"
        return db.collection("households").document(cleanHid)
            .snapshots()
            .map { doc -> Household.fromDocument(doc) }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.GET, path)
                throw error
            }
    }

    suspend fun createHousehold(customCode: String? = null): Result<Household> {
        return try {
            val uid = requireUserId()
            val code = (customCode?.trim()?.uppercase()?.takeIf { it.length == 6 })
                ?: generateInviteCode()
            val path = "households/$code"
            val payload = mapOf(
                "members" to listOf(uid),
                "inviteCode" to code,
                "types" to DEFAULT_DISH_TYPES,
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("households").document(code).set(payload).await()
            Result.success(
                Household(
                    id = code,
                    members = listOf(uid),
                    inviteCode = code,
                    types = DEFAULT_DISH_TYPES,
                    createdBy = uid
                )
            )
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "households")
            Result.failure(e)
        }
    }

    suspend fun joinHouseholdByInviteCode(inviteCode: String): Result<String> {
        val code = inviteCode.trim().uppercase()
        val path = "households/$code"
        return try {
            val uid = requireUserId()
            if (code.length != 6) {
                return Result.failure(IllegalArgumentException("De uitnodigingscode moet exact 6 tekens bevatten."))
            }
            val docRef = db.collection("households").document(code)
            docRef.update(
                mapOf(
                    "members" to FieldValue.arrayUnion(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(code)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateHouseholdTypes(
        hid: String,
        newTypes: List<String>,
        renamedPair: Pair<String, String>? = null
    ): Result<Unit> {
        val path = "households/$hid"
        return try {
            requireUserId()
            val cleaned = newTypes.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
            if (cleaned.isEmpty()) {
                return Result.failure(IllegalArgumentException("Er moet minstens één gerechttype zijn."))
            }
            val batch = db.batch()
            val householdRef = db.collection("households").document(hid)
            batch.update(
                householdRef,
                mapOf(
                    "types" to cleaned,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (renamedPair != null) {
                val (oldType, newType) = renamedPair
                val dishesSnap = householdRef.collection("dishes")
                    .whereEqualTo("type", oldType)
                    .get()
                    .await()
                for (doc in dishesSnap.documents) {
                    batch.update(
                        doc.reference,
                        mapOf(
                            "type" to newType,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- 2. Gerechtenbibliotheek ---

    fun observeDishes(hid: String): Flow<List<Dish>> {
        val path = "households/$hid/dishes"
        return db.collection("households").document(hid).collection("dishes")
            .snapshots()
            .map { snapshot ->
                snapshot.documents
                    .mapNotNull { Dish.fromDocument(it) }
                    .sortedBy { it.name.lowercase() }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun saveDish(
        hid: String,
        existingDishId: String?,
        name: String,
        type: String,
        ingredients: List<IngredientItem>,
        recipeUrl: String?,
        note: String?,
        photoUrl: String?,
        needsNormalization: Boolean = ingredients.any { it.needsNormalization },
        pendingPhotoUpload: Boolean = false
    ): Result<Dish> {
        val dishesPath = "households/$hid/dishes"
        return try {
            val uid = requireUserId()
            val dishesCol = db.collection("households").document(hid).collection("dishes")
            val docRef = if (existingDishId.isNullOrBlank()) {
                dishesCol.document()
            } else {
                dishesCol.document(existingDishId)
            }

            val trimmedName = name.trim()
            val trimmedType = type.trim().lowercase()
            val cleanRecipeUrl = recipeUrl?.trim()?.takeIf { it.isNotEmpty() }
            val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }
            val cleanPhotoUrl = photoUrl?.trim()?.takeIf { it.isNotEmpty() }

            if (existingDishId.isNullOrBlank()) {
                val createPayload = mutableMapOf<String, Any>(
                    "name" to trimmedName,
                    "type" to trimmedType,
                    "ingredients" to ingredients.map { it.toMap() },
                    "needsNormalization" to needsNormalization,
                    "pendingPhotoUpload" to pendingPhotoUpload,
                    "createdBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanRecipeUrl != null) createPayload["recipeUrl"] = cleanRecipeUrl
                if (cleanNote != null) createPayload["note"] = cleanNote
                if (cleanPhotoUrl != null) createPayload["photoUrl"] = cleanPhotoUrl

                docRef.set(createPayload).await()
            } else {
                val updatePayload = mutableMapOf<String, Any?>(
                    "name" to trimmedName,
                    "type" to trimmedType,
                    "ingredients" to ingredients.map { it.toMap() },
                    "recipeUrl" to cleanRecipeUrl,
                    "note" to cleanNote,
                    "photoUrl" to cleanPhotoUrl,
                    "needsNormalization" to needsNormalization,
                    "pendingPhotoUpload" to pendingPhotoUpload,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            }

            val savedDish = Dish(
                id = docRef.id,
                name = trimmedName,
                type = trimmedType,
                ingredients = ingredients,
                recipeUrl = cleanRecipeUrl,
                note = cleanNote,
                photoUrl = cleanPhotoUrl,
                needsNormalization = needsNormalization,
                pendingPhotoUpload = pendingPhotoUpload,
                createdBy = uid
            )
            Result.success(savedDish)
        } catch (e: Exception) {
            val op = if (existingDishId.isNullOrBlank()) OperationType.CREATE else OperationType.UPDATE
            handleFirestoreError(e, op, dishesPath)
            Result.failure(e)
        }
    }

    /**
     * Werkt een gerecht en alle gekoppelde dagsnapshots bij na achtergrond-normalisatie of bewerking.
     */
    suspend fun updateDishAndLinkedDaySnapshots(
        hid: String,
        updatedDish: Dish,
        linkedDayIds: List<String>
    ): Result<Unit> {
        val path = "households/$hid/dishes/${updatedDish.id}"
        return try {
            val uid = requireUserId()
            val batch = db.batch()
            val householdRef = db.collection("households").document(hid)
            val dishRef = householdRef.collection("dishes").document(updatedDish.id)

            batch.update(
                dishRef,
                mapOf(
                    "ingredients" to updatedDish.ingredients.map { it.toMap() },
                    "needsNormalization" to updatedDish.needsNormalization,
                    "pendingPhotoUpload" to updatedDish.pendingPhotoUpload,
                    "photoUrl" to updatedDish.photoUrl,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            val snapshotMap = updatedDish.toSnapshot().toMap()
            for (dayId in linkedDayIds) {
                val dayRef = householdRef.collection("days").document(dayId)
                batch.update(
                    dayRef,
                    mapOf(
                        "dishSnapshot" to snapshotMap,
                        "updatedBy" to uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /** Zet alleen de sterrenwaardering (0-5) van een gerecht; raakt verder niets aan. */
    suspend fun setDishRating(hid: String, dishId: String, rating: Int): Result<Unit> {
        val path = "households/$hid/dishes/$dishId"
        return try {
            requireUserId()
            db.collection("households").document(hid).collection("dishes").document(dishId)
                .update(
                    mapOf(
                        "rating" to rating.coerceIn(0, 5),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteDish(hid: String, dishId: String): Result<Unit> {
        val path = "households/$hid/dishes/$dishId"
        return try {
            requireUserId()
            // Verwijderen van een gerecht laat bestaande weekplanningen intact (dankzij dishSnapshot)
            db.collection("households").document(hid)
                .collection("dishes").document(dishId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- 3. Weekkalender & Dagen ---

    fun observeAllDays(hid: String): Flow<Map<String, DayPlan>> {
        val path = "households/$hid/days"
        return db.collection("households").document(hid).collection("days")
            .snapshots()
            .map { snapshot ->
                snapshot.documents
                    .mapNotNull { DayPlan.fromDocument(it) }
                    .associateBy { it.dateId }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun setDayCooking(
        hid: String,
        dateId: String,
        dish: Dish,
        note: String? = null
    ): Result<Unit> {
        val path = "households/$hid/days/$dateId"
        return try {
            val uid = requireUserId()
            val payload = mutableMapOf<String, Any>(
                "kind" to DayKind.KOKEN.wireValue,
                "dishId" to dish.id,
                "dishSnapshot" to dish.toSnapshot().toMap(),
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }
            if (cleanNote != null) {
                payload["note"] = cleanNote
            }
            db.collection("households").document(hid)
                .collection("days").document(dateId)
                .set(payload)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun setDayMarker(
        hid: String,
        dateId: String,
        kind: DayKind,
        note: String?
    ): Result<Unit> {
        val path = "households/$hid/days/$dateId"
        return try {
            val uid = requireUserId()
            val payload = mutableMapOf<String, Any>(
                "kind" to kind.wireValue,
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }
            if (cleanNote != null) {
                payload["note"] = cleanNote
            }
            db.collection("households").document(hid)
                .collection("days").document(dateId)
                .set(payload)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun clearDay(hid: String, dateId: String): Result<Unit> {
        val path = "households/$hid/days/$dateId"
        return try {
            val uid = requireUserId()
            val payload = mapOf(
                "kind" to DayKind.LEEG.wireValue,
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("households").document(hid)
                .collection("days").document(dateId)
                .set(payload)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    /**
     * 6.3 "Genereer hele week": Resultaat verschijnt in één schrijfactie (batch).
     */
    suspend fun setGeneratedWeekBatch(
        hid: String,
        assignments: Map<String, Dish>
    ): Result<Unit> {
        if (assignments.isEmpty()) return Result.success(Unit)
        val path = "households/$hid/days"
        return try {
            val uid = requireUserId()
            val batch = db.batch()
            val daysCol = db.collection("households").document(hid).collection("days")
            for ((dateId, dish) in assignments) {
                val docRef = daysCol.document(dateId)
                val payload = mapOf(
                    "kind" to DayKind.KOKEN.wireValue,
                    "dishId" to dish.id,
                    "dishSnapshot" to dish.toSnapshot().toMap(),
                    "updatedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(docRef, payload)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- 4. Boodschappenlijst Afvinken (weeks/{weekStart}) ---

    fun observeAllWeeks(hid: String): Flow<Map<String, WeekChecklist>> {
        val path = "households/$hid/weeks"
        return db.collection("households").document(hid).collection("weeks")
            .snapshots()
            .map { snapshot ->
                snapshot.documents
                    .map { WeekChecklist.fromDocument(it) }
                    .associateBy { it.weekStart }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun setGroceryItemChecked(
        hid: String,
        weekStart: String,
        itemKey: String,
        checked: Boolean,
        existingChecked: Map<String, Boolean>
    ): Result<Unit> {
        val path = "households/$hid/weeks/$weekStart"
        return try {
            val uid = requireUserId()
            val updatedMap = existingChecked.toMutableMap()
            if (checked) {
                updatedMap[itemKey] = true
            } else {
                updatedMap.remove(itemKey)
            }
            val payload = mapOf(
                "checked" to updatedMap,
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("households").document(hid)
                .collection("weeks").document(weekStart)
                .set(payload)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- 5. Losse Items (looseItems/{itemId}) ---

    fun observeLooseItems(hid: String): Flow<List<LooseItem>> {
        val path = "households/$hid/looseItems"
        return db.collection("households").document(hid).collection("looseItems")
            .snapshots()
            .map { snapshot ->
                snapshot.documents
                    .mapNotNull { LooseItem.fromDocument(it) }
                    .sortedWith(
                        compareBy<LooseItem> { it.checked }
                            .thenBy { it.name.lowercase() }
                    )
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun addLooseItem(
        hid: String,
        name: String,
        quantity: Double?,
        unit: String?,
        recurring: Boolean
    ): Result<LooseItem> {
        val path = "households/$hid/looseItems"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("households").document(hid)
                .collection("looseItems").document()
            val cleanName = name.trim()
            val cleanUnit = unit?.trim()?.takeIf { it.isNotEmpty() }
            val payload = mutableMapOf<String, Any>(
                "name" to cleanName,
                "recurring" to recurring,
                "checked" to false,
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (quantity != null) payload["quantity"] = quantity
            if (cleanUnit != null) payload["unit"] = cleanUnit

            docRef.set(payload).await()
            Result.success(
                LooseItem(
                    id = docRef.id,
                    name = cleanName,
                    quantity = quantity,
                    unit = cleanUnit,
                    recurring = recurring,
                    checked = false,
                    createdBy = uid
                )
            )
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleLooseItemChecked(
        hid: String,
        itemId: String,
        checked: Boolean
    ): Result<Unit> {
        val path = "households/$hid/looseItems/$itemId"
        return try {
            requireUserId()
            db.collection("households").document(hid)
                .collection("looseItems").document(itemId)
                .update(
                    mapOf(
                        "checked" to checked,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteLooseItem(hid: String, itemId: String): Result<Unit> {
        val path = "households/$hid/looseItems/$itemId"
        return try {
            requireUserId()
            db.collection("households").document(hid)
                .collection("looseItems").document(itemId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    /**
     * 5.6 Knop "Winkelen afronden":
     * eenmalige afgevinkte losse items worden verwijderd,
     * vaste losse items worden terug onafgevinkt gezet.
     */
    suspend fun finishShopping(hid: String, currentLooseItems: List<LooseItem>): Result<Unit> {
        val checkedItems = currentLooseItems.filter { it.checked }
        if (checkedItems.isEmpty()) return Result.success(Unit)
        val path = "households/$hid/looseItems"
        return try {
            requireUserId()
            val batch = db.batch()
            val colRef = db.collection("households").document(hid).collection("looseItems")
            for (item in checkedItems) {
                val docRef = colRef.document(item.id)
                if (item.recurring) {
                    batch.update(
                        docRef,
                        mapOf(
                            "checked" to false,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    batch.delete(docRef)
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    companion object {
        private const val INVITE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        fun generateInviteCode(random: Random = Random.Default): String {
            return (1..6)
                .map { INVITE_CHARS[random.nextInt(INVITE_CHARS.length)] }
                .joinToString("")
        }
    }
}
