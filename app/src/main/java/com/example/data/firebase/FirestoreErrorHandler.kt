package com.example.data.firebase

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(
    exception: Exception,
    operationType: OperationType,
    path: String?
): String {
    val auth = try {
        FirebaseAuth.getInstance()
    } catch (e: Exception) {
        null
    }
    val currentUser = auth?.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", exception.message ?: exception.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        val app = if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        } else {
            FirebaseApp.getInstance()
        } ?: return

        val debugToken = intent?.getStringExtra("FIREBASE_APPCHECK_DEBUG_TOKEN")
            ?.takeIf { it.isNotBlank() }

        if (debugToken != null) {
            val prefsName = "com.google.firebase.appcheck.debug.store.${app.persistenceKey}"
            context.applicationContext
                .getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                .edit()
                .putString("com.google.firebase.appcheck.debug.DEBUG_SECRET", debugToken)
                .commit()
        }

        val firebaseAppCheck = FirebaseAppCheck.getInstance(app)
        firebaseAppCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
    } catch (e: Exception) {
        Log.w("AppCheck", "Could not configure Firebase App Check: ${e.message}")
    }
}
