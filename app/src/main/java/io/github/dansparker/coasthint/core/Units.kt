package io.github.dansparker.coasthint.core

private const val KMH_PER_MPS = 3.6

fun kmhToMps(kmh: Double): Double = kmh / KMH_PER_MPS

fun mpsToKmh(mps: Double): Double = mps * KMH_PER_MPS
