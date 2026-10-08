package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Sectie 7 & 8:
 * - Comprimeert foto's client-side tot maximaal 1600 px aan de lange zijde voor lokale opslag,
 *   en maakt een gecomprimeerde JPEG data-URL zodat beide toestellen de foto direct kunnen zien.
 * - Als offline wordt de foto lokaal opgeslagen en in de wachtrij gezet (`pendingPhotoUpload = true`),
 *   en zodra er weer internet is automatisch gesynchroniseerd.
 */
object PhotoUtils {

    private const val MAX_LOCAL_DIMENSION_PX = 1600
    private const val MAX_SYNC_DIMENSION_PX = 720

    data class ProcessedPhoto(
        val localFileUri: String,
        val syncedDataUrl: String
    )

    suspend fun processPickedUri(context: Context, uri: Uri): ProcessedPhoto? = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null
            if (bytes.size > 15 * 1024 * 1024) return@withContext null // Safety guard
            val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
            processBitmap(context, original)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun processCameraBitmap(context: Context, bitmap: Bitmap): ProcessedPhoto? = withContext(Dispatchers.IO) {
        try {
            processBitmap(context, bitmap)
        } catch (e: Exception) {
            null
        }
    }

    private fun processBitmap(context: Context, original: Bitmap): ProcessedPhoto {
        // 1. Schaal tot maximaal 1600 px aan de lange zijde (Sectie 8)
        val scaled1600 = scaleDown(original, MAX_LOCAL_DIMENSION_PX)
        val photosDir = File(context.filesDir, "dish_photos").apply { mkdirs() }
        val localFile = File(photosDir, "dish_${UUID.randomUUID()}.jpg")
        FileOutputStream(localFile).use { out ->
            scaled1600.compress(Bitmap.CompressFormat.JPEG, 82, out)
        }

        // 2. Maak compacte gesynchroniseerde data-URL voor gedeeld huishouden
        val scaledSync = scaleDown(scaled1600, MAX_SYNC_DIMENSION_PX)
        val baos = ByteArrayOutputStream()
        scaledSync.compress(Bitmap.CompressFormat.JPEG, 72, baos)
        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        val dataUrl = "data:image/jpeg;base64,$b64"

        return ProcessedPhoto(
            localFileUri = Uri.fromFile(localFile).toString(),
            syncedDataUrl = dataUrl
        )
    }

    suspend fun convertLocalFileToSyncedDataUrl(localUriString: String): String? = withContext(Dispatchers.IO) {
        try {
            val path = Uri.parse(localUriString).path ?: return@withContext null
            val file = File(path)
            if (!file.exists()) return@withContext null
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext null
            val scaledSync = scaleDown(bitmap, MAX_SYNC_DIMENSION_PX)
            val baos = ByteArrayOutputStream()
            scaledSync.compress(Bitmap.CompressFormat.JPEG, 72, baos)
            val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            "data:image/jpeg;base64,$b64"
        } catch (e: Exception) {
            null
        }
    }

    fun decodeDataUrlToBitmap(photoUrl: String?): Bitmap? {
        if (photoUrl.isNullOrBlank() || !photoUrl.startsWith("data:image")) return null
        return try {
            val commaIndex = photoUrl.indexOf(',')
            if (commaIndex == -1) return null
            val base64Part = photoUrl.substring(commaIndex + 1)
            val bytes = Base64.decode(base64Part, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val longest = max(width, height)
        if (longest <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / longest.toFloat()
        val newW = (width * ratio).roundToInt().coerceAtLeast(1)
        val newH = (height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }
}
