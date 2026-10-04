package io.github.dansparker.coasthint.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.dansparker.coasthint.R
import io.github.dansparker.coasthint.core.CoastMode
import io.github.dansparker.coasthint.core.CoastSettings
import io.github.dansparker.coasthint.settings.AppSettings
import java.util.Locale

private fun fmt(value: Double, decimals: Int, unit: String) = String.format(Locale.GERMAN, "%.${decimals}f %s", value, unit)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    locked: Boolean,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    val enabled = !locked
    val coast = settings.coast
    val effective = settings.effectiveCoast
    fun updateCoast(transform: (CoastSettings) -> CoastSettings) =
        onUpdate { it.copy(coast = transform(it.coast)) }

    Page(stringResource(R.string.nav_settings), onBack) {
        if (locked) Text(stringResource(R.string.settings_locked), color = MaterialTheme.colorScheme.error)

        Section(stringResource(R.string.settings_section_coasting))
        Text(stringResource(R.string.settings_mode))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            CoastMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = coast.mode == mode,
                    onClick = { updateCoast { it.copy(mode = mode) } },
                    shape = SegmentedButtonDefaults.itemShape(index, CoastMode.entries.size),
                    enabled = enabled,
                ) {
                    Text(stringResource(if (mode == CoastMode.ENGINE_BRAKING) R.string.mode_engine_braking else R.string.mode_sailing))
                }
            }
        }
        DecelerationRow(
            label = stringResource(R.string.settings_decel_engine),
            manual = coast.engineBrakingDecelerationMps2,
            calibrated = effective.engineBrakingDecelerationMps2.takeIf { settings.measurements(CoastMode.ENGINE_BRAKING).isNotEmpty() },
            range = 0.2..1.2,
            enabled = enabled,
            onChange = { v -> updateCoast { it.copy(engineBrakingDecelerationMps2 = v) } },
        )
        DecelerationRow(
            label = stringResource(R.string.settings_decel_sailing),
            manual = coast.sailingDecelerationMps2,
            calibrated = effective.sailingDecelerationMps2.takeIf { settings.measurements(CoastMode.SAILING).isNotEmpty() },
            range = 0.1..0.8,
            enabled = enabled,
            onChange = { v -> updateCoast { it.copy(sailingDecelerationMps2 = v) } },
        )
        val quadraticAvailable = when (coast.mode) {
            CoastMode.ENGINE_BRAKING -> effective.engineBrakingQuadratic
            CoastMode.SAILING -> effective.sailingQuadratic
        } != null
        SwitchRow(
            stringResource(R.string.settings_quadratic),
            checked = coast.useQuadraticModel && quadraticAvailable,
            enabled = enabled && quadraticAvailable,
        ) { checked -> updateCoast { it.copy(useQuadraticModel = checked) } }
        if (!quadraticAvailable) {
            Text(stringResource(R.string.settings_quadratic_unavailable), style = MaterialTheme.typography.bodySmall)
        }
        SliderRow(stringResource(R.string.settings_reaction), coast.reactionTimeS, 0.0..4.0, 0.5, { fmt(it, 1, "s") }, enabled) { v ->
            updateCoast { it.copy(reactionTimeS = v) }
        }
        SliderRow(stringResource(R.string.settings_margin), coast.marginM, 0.0..100.0, 10.0, { fmt(it, 0, "m") }, enabled) { v ->
            updateCoast { it.copy(marginM = v) }
        }
        SliderRow(stringResource(R.string.settings_tolerance), coast.speedToleranceKmh, 0.0..20.0, 1.0, { fmt(it, 0, "km/h") }, enabled) { v ->
            updateCoast { it.copy(speedToleranceKmh = v) }
        }

        Section(stringResource(R.string.settings_section_targets))
        TargetRow(stringResource(R.string.settings_target_turn), coast.turnTargetKmh, 10.0..50.0, enabled) { v ->
            updateCoast { it.copy(turnTargetKmh = v) }
        }
        TargetRow(stringResource(R.string.settings_target_sharp), coast.sharpTurnTargetKmh, 5.0..40.0, enabled) { v ->
            updateCoast { it.copy(sharpTurnTargetKmh = v) }
        }
        TargetRow(stringResource(R.string.settings_target_roundabout), coast.roundaboutTargetKmh, 15.0..50.0, enabled) { v ->
            updateCoast { it.copy(roundaboutTargetKmh = v) }
        }
        SwitchRow(stringResource(R.string.settings_slight_enabled), coast.slightTurnTargetKmh != null, enabled) { on ->
            updateCoast { it.copy(slightTurnTargetKmh = if (on) DEFAULT_SLIGHT_TURN_KMH else null) }
        }
        coast.slightTurnTargetKmh?.let { target ->
            TargetRow(stringResource(R.string.settings_target_slight), target, 30.0..90.0, enabled) { v ->
                updateCoast { it.copy(slightTurnTargetKmh = v) }
            }
        }

        Section(stringResource(R.string.settings_section_output))
        val output = settings.output
        SwitchRow(stringResource(R.string.settings_tone), output.tone, enabled) { on ->
            onUpdate { it.copy(output = it.output.copy(tone = on)) }
        }
        SwitchRow(stringResource(R.string.settings_speech), output.speech, enabled) { on ->
            onUpdate { it.copy(output = it.output.copy(speech = on)) }
        }
        SwitchRow(stringResource(R.string.settings_vibration), output.vibration, enabled) { on ->
            onUpdate { it.copy(output = it.output.copy(vibration = on)) }
        }

        Section(stringResource(R.string.settings_section_data))
        SwitchRow(stringResource(R.string.settings_trip_log), settings.tripLogEnabled, enabled) { on ->
            onUpdate { it.copy(tripLogEnabled = on) }
        }
        var server by remember(settings.overpassServer) { mutableStateOf(settings.overpassServer) }
        OutlinedTextField(
            value = server,
            onValueChange = { server = it },
            label = { Text(stringResource(R.string.settings_overpass)) },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onUpdate { it.copy(overpassServer = server.trim()) } },
                enabled = enabled && server.trim() != settings.overpassServer && server.startsWith("https://"),
            ) { Text(stringResource(R.string.settings_save)) }
        }
    }
}

private const val DEFAULT_SLIGHT_TURN_KMH = 60.0

@Composable
private fun DecelerationRow(
    label: String,
    manual: Double,
    calibrated: Double?,
    range: ClosedFloatingPointRange<Double>,
    enabled: Boolean,
    onChange: (Double) -> Unit,
) {
    if (calibrated != null) {
        ValueRow(label, stringResource(R.string.settings_calibrated, fmt(calibrated, 2, "m/s²")))
    } else {
        SliderRow(label, manual, range, 0.05, { fmt(it, 2, "m/s²") }, enabled, onChange)
    }
}

@Composable
private fun TargetRow(
    label: String,
    value: Double?,
    range: ClosedFloatingPointRange<Double>,
    enabled: Boolean,
    onChange: (Double) -> Unit,
) {
    SliderRow(label, value ?: range.start, range, 5.0, { fmt(it, 0, "km/h") }, enabled, onChange)
}
