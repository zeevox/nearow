package net.zeevox.nearow.fourier

import kotlin.math.cos
import kotlin.math.sin

data class Polar(var magnitude: Double, var phase: Double) {
    fun addPhase(phaseTurn: Double) {
        phase += phaseTurn
    }

    infix fun into(complex: Complex) {
        complex.real = cos(phase) * magnitude
        complex.imaginary = sin(phase) * magnitude
    }
}