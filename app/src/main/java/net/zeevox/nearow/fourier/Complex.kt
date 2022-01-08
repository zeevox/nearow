package net.zeevox.nearow.fourier

import kotlin.math.atan2
import kotlin.math.sqrt

data class Complex(var real: Double, var imaginary: Double) {
    operator fun plus(otherReal: Double): Complex = Complex(real + otherReal, imaginary)

    infix fun into(polar: Polar) {
        polar.magnitude = sqrt(real * real + imaginary * imaginary)
        polar.phase = atan2(imaginary, real)
    }
}