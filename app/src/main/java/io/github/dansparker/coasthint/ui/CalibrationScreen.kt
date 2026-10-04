package io.github.dansparker.coasthint.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.dansparker.coasthint.R
import io.github.dansparker.coasthint.core.CalibrationResult
import io.github.dansparker.coasthint.core.Calibration
import io.github.dansparker.coasthint.core.CoastMeasurement
import io.github.dansparker.coasthint.core.CoastMode
import io.github.dansparker.coasthint.core.mpsToKmh
import io.github.dansparker.coasthint.service.LiveState
import io.github.dansparker.coasthint.settings.AppSettings
import java.util.Locale

private fun accel(value: Double) = String.format(Locale.GERMAN, "%.2f m/s²", value)
private fun kmh(mps: Double) = String.format(Locale.GERMAN, "%.0f km/h", mpsToKmh(mps))

@Composable
fun CalibrationScreen(
    state: LiveState,
    settings: AppSettings,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClear: (CoastMode) -> Unit,
    onBack: () -> Unit,
) {
    val calibration = state.calibration
    val mode = if (calibration.active) calibration.mode else settings.coast.mode
    val modeName = stringResource(if (mode == CoastMode.ENGINE_BRAKING) R.string.mode_engine_braking else R.string.mode_sailing)

    Page(stringResource(R.string.nav_calibration), onBack) {
        Text(stringResource(R.string.calibration_intro))
        Text(stringResource(R.string.calibration_mode, modeName), style = MaterialTheme.typography.titleMedium)

        if (!state.running) {
            Text(stringResource(R.string.calibration_needs_service), color = MaterialTheme.colorScheme.error)
        } else if (calibration.active) {
            Text(
                stringResource(
                    R.string.calibration_running,
                    calibration.samples,
                    state.driving?.let { kmh(it.speedMps) } ?: "–",
                ),
            )
            Button(onClick = onStop) { Text(stringResource(R.string.calibration_stop)) }
        } else {
            Button(onClick = onStart) { Text(stringResource(R.string.calibration_start)) }
        }

        when (val result = calibration.lastResult) {
            is CalibrationResult.Success -> Text(successText(result.measurement))
            is CalibrationResult.Failure -> Text(
                stringResource(R.string.calibration_failed, reasonText(result.reason)),
                color = MaterialTheme.colorScheme.error,
            )
            null -> Unit
        }

        Section(modeName)
        val measurements = settings.measurements(mode)
        if (measurements.isEmpty()) {
            Text(stringResource(R.string.calibration_none))
        } else {
            Text(
                stringResource(
                    R.string.calibration_measurements,
                    measurements.size,
                    accel(Calibration.averageDeceleration(measurements)!!),
                ),
            )
            measurements.forEach { Text(successText(it), style = MaterialTheme.typography.bodySmall) }
            Calibration.fitQuadratic(measurements)?.let { q ->
                Text(
                    stringResource(
                        R.string.calibration_quadratic,
                        String.format(Locale.GERMAN, "%.3f", q.c0),
                        String.format(Locale.GERMAN, "%.5f", q.c2),
                    ),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onClear(mode) }, enabled = !calibration.active) {
                    Text(stringResource(R.string.calibration_clear))
                }
            }
        }
    }
}

@Composable
private fun successText(m: CoastMeasurement) = stringResource(
    R.string.calibration_success,
    accel(m.decelerationMps2),
    kmh(m.startSpeedMps),
    kmh(m.endSpeedMps),
    String.format(Locale.GERMAN, "%.0f s", m.durationS),
)

@Composable
private fun reasonText(reason: CalibrationResult.Reason) = stringResource(
    when (reason) {
        CalibrationResult.Reason.TOO_SHORT -> R.string.calibration_reason_too_short
        CalibrationResult.Reason.TOO_LITTLE_SLOWDOWN -> R.string.calibration_reason_too_little_slowdown
        CalibrationResult.Reason.NOT_SLOWING_DOWN -> R.string.calibration_reason_not_slowing_down
        CalibrationResult.Reason.TOO_NOISY -> R.string.calibration_reason_too_noisy
    },
)
