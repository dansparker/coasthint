package io.github.dansparker.coasthint.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.dansparker.coasthint.R
import io.github.dansparker.coasthint.roaddb.RoadDbInfo
import java.util.Locale

data class OfflineDataState(
    val info: RoadDbInfo? = null,
    val sizeBytes: Long = 0,
    val busy: Boolean = false,
    val error: String? = null,
)

@Composable
fun OfflineDataScreen(
    state: OfflineDataState,
    onImport: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    Page(stringResource(R.string.nav_offline), onBack) {
        Text(stringResource(R.string.offline_intro))
        val info = state.info
        if (info == null) {
            Text(stringResource(R.string.offline_none))
        } else {
            ValueRow(stringResource(R.string.offline_source), info.source)
            ValueRow(stringResource(R.string.offline_created), info.created.take(10))
            ValueRow(stringResource(R.string.offline_ways), String.format(Locale.GERMAN, "%,d", info.wayCount))
            ValueRow(stringResource(R.string.offline_size), String.format(Locale.GERMAN, "%.0f MB", state.sizeBytes / 1e6))
            val b = info.bounds
            ValueRow(
                stringResource(R.string.offline_area),
                String.format(Locale.ROOT, "%.2f–%.2f N, %.2f–%.2f E", b.minLat, b.maxLat, b.minLon, b.maxLon),
            )
        }
        if (state.busy) LinearProgressIndicator()
        state.error?.let { Text(stringResource(R.string.offline_error, it), color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onImport, enabled = !state.busy) { Text(stringResource(R.string.offline_import)) }
            if (info != null) {
                OutlinedButton(onClick = onDelete, enabled = !state.busy) { Text(stringResource(R.string.offline_delete)) }
            }
        }
    }
}
