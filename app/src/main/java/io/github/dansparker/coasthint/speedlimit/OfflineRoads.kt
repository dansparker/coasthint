package io.github.dansparker.coasthint.speedlimit

import android.content.Context
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import io.github.dansparker.coasthint.roaddb.RoadDbFile
import java.io.File

/** The one installed offline road database, shared by the service and the UI. */
object OfflineRoads {
    @Volatile
    private var instance: RoadDbFile? = null

    fun get(context: Context): RoadDbFile = instance ?: synchronized(this) {
        instance ?: RoadDbFile(File(context.applicationContext.filesDir, "roads/roads.db")) { path ->
            BundledSQLiteDriver().open(path, SQLITE_OPEN_READONLY)
        }.also { instance = it }
    }
}
