package io.github.dansparker.coasthint.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.dansparker.coasthint.R
import java.io.File

@Composable
fun TripsScreen(trips: List<File>, onShare: (File) -> Unit, onBack: () -> Unit) {
    Page(stringResource(R.string.nav_trips), onBack) {
        if (trips.isEmpty()) Text(stringResource(R.string.trips_empty))
        trips.forEach { file ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${file.name} · ${file.length() / 1024} KB")
                OutlinedButton(onClick = { onShare(file) }) { Text(stringResource(R.string.trips_share)) }
            }
        }
    }
}
