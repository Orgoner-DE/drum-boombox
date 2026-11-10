package de.spaze.drumboombox

import android.content.Context
import java.io.File

class MappingStore(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): MappingDocument? {
        val raw = prefs.getString(KEY, null) ?: return null
        return try {
            MappingJson.fromJson(raw)
        } catch (_: Exception) {
            null
        }
    }

    fun save(doc: MappingDocument) {
        prefs.edit().putString(KEY, MappingJson.toJson(doc)).commit()
    }

    fun saveProject(raw: String) {
        prefs.edit().putString(PROJECT_KEY, raw).commit()
        File(context.filesDir, "last-project.json").writeText(raw)
    }

    fun loadProject(): String? {
        val file = File(context.filesDir, "last-project.json")
        if (file.isFile) return file.readText()
        return prefs.getString(PROJECT_KEY, null)
    }

    companion object {
        private const val PREFS = "drumboombox_mapping"
        private const val KEY = "document_v1"
        private const val PROJECT_KEY = "project_v1"
    }
}
