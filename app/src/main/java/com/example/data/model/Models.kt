package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue

val DEFAULT_DISH_TYPES = listOf(
    "pasta",
    "rijst",
    "aardappelen",
    "vlees",
    "vis",
    "vegetarisch",
    "soep",
    "wok",
    "ovenschotel",
    "brood/snel"
)

data class Household(
    val id: String = "",
    val members: List<String> = emptyList(),
    val inviteCode: String = "",
    val types: List<String> = DEFAULT_DISH_TYPES,
    val createdBy: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    companion object {
        fun fromDocument(doc: DocumentSnapshot): Household? {
            if (!doc.exists()) return null
            val data = doc.data ?: return null
            val members = (data["members"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val types = (data["types"] as? List<*>)?.mapNotNull { it as? String }?.ifEmpty { DEFAULT_DISH_TYPES }
                ?: DEFAULT_DISH_TYPES
            val inviteCode = (data["inviteCode"] as? String) ?: doc.id
            val createdBy = (data["createdBy"] as? String) ?: ""
            val createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            return Household(
                id = doc.id,
                members = members,
                inviteCode = inviteCode,
                types = types,
                createdBy = createdBy,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        }
    }
}

data class IngredientItem(
    val raw: String = "",
    val name: String = "",
    val quantity: Double? = null,
    val unit: String? = null,
    val needsNormalization: Boolean = false
) {
    fun toMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>(
            "raw" to raw,
            "name" to name,
            "quantity" to quantity,
            "unit" to unit,
            "needsNormalization" to needsNormalization
        )
        return map
    }

    companion object {
        fun fromMap(map: Map<*, *>): IngredientItem {
            val raw = (map["raw"] as? String) ?: ""
            val name = (map["name"] as? String) ?: raw.trim().lowercase()
            val quantity = (map["quantity"] as? Number)?.toDouble()
            val unit = (map["unit"] as? String)?.takeIf { it.isNotBlank() }
            val needsNormalization = (map["needsNormalization"] as? Boolean) ?: false
            return IngredientItem(
                raw = raw,
                name = name,
                quantity = quantity,
                unit = unit,
                needsNormalization = needsNormalization
            )
        }
    }
}

data class Dish(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val ingredients: List<IngredientItem> = emptyList(),
    val recipeUrl: String? = null,
    val note: String? = null,
    val photoUrl: String? = null,
    val needsNormalization: Boolean = false,
    val pendingPhotoUpload: Boolean = false,
    val createdBy: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toSnapshot(): DishSnapshot = DishSnapshot(
        name = name,
        type = type,
        ingredients = ingredients,
        recipeUrl = recipeUrl,
        note = note,
        photoUrl = photoUrl
    )

    companion object {
        fun fromDocument(doc: DocumentSnapshot): Dish? {
            if (!doc.exists()) return null
            val data = doc.data ?: return null
            val name = (data["name"] as? String) ?: return null
            val type = (data["type"] as? String) ?: "pasta"
            val rawIngredients = (data["ingredients"] as? List<*>) ?: emptyList<Any>()
            val ingredients = rawIngredients.mapNotNull { item ->
                (item as? Map<*, *>)?.let { IngredientItem.fromMap(it) }
            }
            val recipeUrl = (data["recipeUrl"] as? String)?.takeIf { it.isNotBlank() }
            val note = (data["note"] as? String)?.takeIf { it.isNotBlank() }
            val photoUrl = (data["photoUrl"] as? String)?.takeIf { it.isNotBlank() }
            val needsNormalization = (data["needsNormalization"] as? Boolean)
                ?: ingredients.any { it.needsNormalization }
            val pendingPhotoUpload = (data["pendingPhotoUpload"] as? Boolean) ?: false
            val createdBy = (data["createdBy"] as? String) ?: ""
            val createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)

            return Dish(
                id = doc.id,
                name = name,
                type = type,
                ingredients = ingredients,
                recipeUrl = recipeUrl,
                note = note,
                photoUrl = photoUrl,
                needsNormalization = needsNormalization,
                pendingPhotoUpload = pendingPhotoUpload,
                createdBy = createdBy,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        }
    }
}

data class DishSnapshot(
    val name: String = "",
    val type: String = "",
    val ingredients: List<IngredientItem> = emptyList(),
    val recipeUrl: String? = null,
    val note: String? = null,
    val photoUrl: String? = null
) {
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "name" to name,
            "type" to type,
            "ingredients" to ingredients.map { it.toMap() }
        )
        if (!recipeUrl.isNullOrBlank()) map["recipeUrl"] = recipeUrl
        if (!note.isNullOrBlank()) map["note"] = note
        if (!photoUrl.isNullOrBlank()) map["photoUrl"] = photoUrl
        return map
    }

    companion object {
        fun fromMap(map: Map<*, *>): DishSnapshot {
            val name = (map["name"] as? String) ?: ""
            val type = (map["type"] as? String) ?: ""
            val rawIngredients = (map["ingredients"] as? List<*>) ?: emptyList<Any>()
            val ingredients = rawIngredients.mapNotNull { item ->
                (item as? Map<*, *>)?.let { IngredientItem.fromMap(it) }
            }
            val recipeUrl = (map["recipeUrl"] as? String)?.takeIf { it.isNotBlank() }
            val note = (map["note"] as? String)?.takeIf { it.isNotBlank() }
            val photoUrl = (map["photoUrl"] as? String)?.takeIf { it.isNotBlank() }
            return DishSnapshot(
                name = name,
                type = type,
                ingredients = ingredients,
                recipeUrl = recipeUrl,
                note = note,
                photoUrl = photoUrl
            )
        }
    }
}

enum class DayKind(val wireValue: String, val labelNl: String) {
    LEEG("leeg", "Leeg"),
    KOKEN("koken", "Koken"),
    AFHAAL("afhaal", "Afhaal"),
    OPWARM("opwarm", "Opwarmmaaltijd"),
    NIET_KOKEN("niet_koken", "Niet koken");

    companion object {
        fun fromWire(value: String?): DayKind =
            entries.firstOrNull { it.wireValue == value } ?: LEEG
    }
}

data class DayPlan(
    val dateId: String = "", // yyyy-MM-dd
    val kind: String = DayKind.LEEG.wireValue,
    val dishId: String? = null,
    val dishSnapshot: DishSnapshot? = null,
    val note: String? = null,
    val updatedBy: String = "",
    val updatedAt: Timestamp? = null
) {
    val dayKind: DayKind
        get() = DayKind.fromWire(kind)

    companion object {
        fun fromDocument(doc: DocumentSnapshot): DayPlan? {
            if (!doc.exists()) return null
            val data = doc.data ?: return null
            val kind = (data["kind"] as? String) ?: DayKind.LEEG.wireValue
            val dishId = (data["dishId"] as? String)?.takeIf { it.isNotBlank() }
            val snapshotMap = data["dishSnapshot"] as? Map<*, *>
            val dishSnapshot = snapshotMap?.let { DishSnapshot.fromMap(it) }
            val note = (data["note"] as? String)?.takeIf { it.isNotBlank() }
            val updatedBy = (data["updatedBy"] as? String) ?: ""
            val updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            return DayPlan(
                dateId = doc.id,
                kind = kind,
                dishId = dishId,
                dishSnapshot = dishSnapshot,
                note = note,
                updatedBy = updatedBy,
                updatedAt = updatedAt
            )
        }
    }
}

data class WeekChecklist(
    val weekStart: String = "", // yyyy-MM-dd (zondag)
    val checked: Map<String, Boolean> = emptyMap(),
    val updatedBy: String = "",
    val updatedAt: Timestamp? = null
) {
    companion object {
        fun fromDocument(doc: DocumentSnapshot, defaultWeekStart: String = doc.id): WeekChecklist {
            if (!doc.exists()) return WeekChecklist(weekStart = defaultWeekStart)
            val data = doc.data ?: return WeekChecklist(weekStart = defaultWeekStart)
            val rawChecked = data["checked"] as? Map<*, *> ?: emptyMap<Any, Any>()
            val checkedMap = mutableMapOf<String, Boolean>()
            for ((k, v) in rawChecked) {
                val keyStr = k as? String ?: continue
                val boolVal = v as? Boolean ?: false
                checkedMap[keyStr] = boolVal
            }
            val updatedBy = (data["updatedBy"] as? String) ?: ""
            val updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            return WeekChecklist(
                weekStart = doc.id,
                checked = checkedMap,
                updatedBy = updatedBy,
                updatedAt = updatedAt
            )
        }
    }
}

data class LooseItem(
    val id: String = "",
    val name: String = "",
    val quantity: Double? = null,
    val unit: String? = null,
    val recurring: Boolean = false,
    val checked: Boolean = false,
    val createdBy: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    companion object {
        fun fromDocument(doc: DocumentSnapshot): LooseItem? {
            if (!doc.exists()) return null
            val data = doc.data ?: return null
            val name = (data["name"] as? String) ?: return null
            val quantity = (data["quantity"] as? Number)?.toDouble()
            val unit = (data["unit"] as? String)?.takeIf { it.isNotBlank() }
            val recurring = (data["recurring"] as? Boolean) ?: false
            val checked = (data["checked"] as? Boolean) ?: false
            val createdBy = (data["createdBy"] as? String) ?: ""
            val createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            return LooseItem(
                id = doc.id,
                name = name,
                quantity = quantity,
                unit = unit,
                recurring = recurring,
                checked = checked,
                createdBy = createdBy,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        }
    }
}

data class AggregatedGroceryItem(
    val itemKey: String, // name|unit
    val name: String,
    val totalQuantity: Double?,
    val canonicalUnit: String?,
    val displayAmount: String,
    val checked: Boolean,
    val dishNames: List<String>
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
