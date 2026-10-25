package com.blackhole.app.data

import android.content.Context
import com.blackhole.app.model.AppSettings
import com.blackhole.app.model.FavoriteDevice
import org.json.JSONArray
import org.json.JSONObject

object SettingsStore {
    private const val PREFS_NAME = "blackhole_settings"
    private const val KEY_ALIAS = "alias"
    private const val KEY_DEST_FOLDER = "dest_folder"
    private const val KEY_QUICK_SAVE = "quick_save"
    private const val KEY_QUICK_SAVE_MODE = "quick_save_mode"
    private const val KEY_AUTO_COPY_TEXT = "auto_copy_text"
    private const val KEY_PORT = "port"
    private const val KEY_PIN = "pin"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_SIGNALING_URL = "signaling_url"
    private const val KEY_FAVORITES = "favorites_json"

    fun load(context: Context, defaultAlias: String): AppSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val favsJson = prefs.getString(KEY_FAVORITES, "[]") ?: "[]"
        val favorites = parseFavorites(favsJson)
        val quickSaveBool = prefs.getBoolean(KEY_QUICK_SAVE, false)
        val quickSaveMode = prefs.getString(KEY_QUICK_SAVE_MODE, if (quickSaveBool) "always" else "off") ?: if (quickSaveBool) "always" else "off"
        val autoCopyText = prefs.getBoolean(KEY_AUTO_COPY_TEXT, true)

        return AppSettings(
            alias = prefs.getString(KEY_ALIAS, defaultAlias) ?: defaultAlias,
            destinationFolder = prefs.getString(KEY_DEST_FOLDER, "Download/BlackHole") ?: "Download/BlackHole",
            quickSave = quickSaveMode == "always" || quickSaveBool,
            quickSaveMode = quickSaveMode,
            autoCopyText = autoCopyText,
            port = prefs.getInt(KEY_PORT, 53317),
            pin = prefs.getString(KEY_PIN, "775") ?: "775",
            themeMode = prefs.getString(KEY_THEME_MODE, "system") ?: "system",
            favorites = favorites
        )
    }

    fun save(context: Context, settings: AppSettings) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_ALIAS, settings.alias)
            .putString(KEY_DEST_FOLDER, settings.destinationFolder)
            .putBoolean(KEY_QUICK_SAVE, settings.quickSave || settings.quickSaveMode == "always")
            .putString(KEY_QUICK_SAVE_MODE, settings.quickSaveMode)
            .putBoolean(KEY_AUTO_COPY_TEXT, settings.autoCopyText)
            .putInt(KEY_PORT, settings.port)
            .putString(KEY_PIN, settings.pin)
            .putString(KEY_THEME_MODE, settings.themeMode)
            .putString(KEY_FAVORITES, serializeFavorites(settings.favorites))
            .apply()
    }

    fun saveFavorites(context: Context, favorites: List<FavoriteDevice>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FAVORITES, serializeFavorites(favorites)).apply()
    }

    private fun parseFavorites(jsonStr: String): List<FavoriteDevice> {
        val list = mutableListOf<FavoriteDevice>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    FavoriteDevice(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        fingerprint = obj.optString("fingerprint", ""),
                        ip = obj.optString("ip", ""),
                        port = obj.optInt("port", 53317),
                        alias = obj.optString("alias", ""),
                        customAlias = obj.optString("customAlias", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun serializeFavorites(favorites: List<FavoriteDevice>): String {
        val arr = JSONArray()
        for (f in favorites) {
            val obj = JSONObject()
            obj.put("id", f.id)
            obj.put("fingerprint", f.fingerprint)
            obj.put("ip", f.ip)
            obj.put("port", f.port)
            obj.put("alias", f.alias)
            obj.put("customAlias", f.customAlias)
            arr.put(obj)
        }
        return arr.toString()
    }

    fun getSignalingUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SIGNALING_URL, "wss://share.sunilshahid.com/ws") ?: "wss://share.sunilshahid.com/ws"
    }

    fun setSignalingUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SIGNALING_URL, url).apply()
    }
}
