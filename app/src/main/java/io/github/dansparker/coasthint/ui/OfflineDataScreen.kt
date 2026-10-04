package io.github.dansparker.coasthint.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.dansparker.coasthint.R
import io.github.dansparker.coasthint.roaddb.InstalledRoadDb
import java.util.Locale

data class OfflineDataState(
    val installed: List<InstalledRoadDb> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

@Composable
fun OfflineDataScreen(
    state: OfflineDataState,
    onImport: () -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
) {
    Page(stringResource(R.string.nav_offline), onBack) {
        Text(stringResource(R.string.offline_intro))
        if (state.installed.isEmpty()) Text(stringResource(R.string.offline_none))
        state.installed.forEach { db ->
            Section(db.name)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val info = db.info
                    if (info == null) {
                        Text(stringResource(R.string.offline_error, db.error.orEmpty()), color = MaterialTheme.colorScheme.error)
                    } else {
                        Text(
                            stringResource(
                                R.string.offline_entry,
                                info.created.take(10),
                                String.format(Locale.GERMAN, "%,d", info.wayCount),
                                String.format(Locale.GERMAN, "%.0f", db.sizeBytes / 1e6),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                OutlinedButton(onClick = { onDelete(db.name) }, enabled = !state.busy) {
                    Text(stringResource(R.string.offline_delete))
                }
            }
        }
        if (state.busy) LinearProgressIndicator()
        state.message?.let { Text(it) }
        state.error?.let { Text(stringResource(R.string.offline_error, it), color = MaterialTheme.colorScheme.error) }
        Button(onClick = onImport, enabled = !state.busy) { Text(stringResource(R.string.offline_import)) }
    }
}
