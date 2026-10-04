package io.github.dansparker.coasthint.speedlimit

import android.content.Context
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import io.github.dansparker.coasthint.roaddb.RoadDbLibrary
import java.io.File

/** The installed offline road databases (one per region), shared by the service and the UI. */
object OfflineRoads {
    @Volatile
    private var instance: RoadDbLibrary? = null

    fun get(context: Context): RoadDbLibrary = instance ?: synchronized(this) {
        instance ?: RoadDbLibrary(File(context.applicationContext.filesDir, "roads")) { path ->
            BundledSQLiteDriver().open(path, SQLITE_OPEN_READONLY)
        }.also { instance = it }
    }
}
