package net.zeevox.nearow.fourier

/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Olav Holten
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in<br>
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */


/**
 * Initialise a new sliding DFT processor
 * @param sampleCount The number of samples to process
 */
class SlidingDFT(private val sampleCount: Int, private val componentsPerSample: Int) {

    // Nyquist Theorem, maximal possible identifiable frequency is half the sample frequency
    private val frequencyCount: Int = sampleCount / 2

    val sliderFrequencies: Array<Frequency> =
        (0..(frequencyCount + 1) * componentsPerSample).map {
            Frequency(
                frequencyCount,
                (it.toDouble() / componentsPerSample),
                componentsPerSample
            )
        }.toTypedArray()

    private var realSum = 0.0


    fun slide(value: Double) {
        val change: Double = (value - realSum) / sampleCount

        sliderFrequencies.map { it.slide(change) }
        this.realSum = sliderFrequencies.sumOf { it.complex.real }
    }

    fun getMaximallyCorrelatedFrequency(): Frequency? =
        sliderFrequencies.maxByOrNull { it.polar.magnitude }


    inner class Frequency constructor(
        totalBinCount: Int,
        val wavelength: Double,
        componentsPerSample: Int
    ) {
        private val turnDegrees: Double = kotlin.math.PI / totalBinCount * wavelength

        var complex: Complex = Complex(0.0, 0.0)
        var polar: Polar = Polar(0.0, 0.0)

        private val multiplier: Double =
            (if (wavelength == 0.0 || wavelength == totalBinCount.toDouble()) 1.0 else 2.0) / componentsPerSample

        fun slide(change: Double) {
            complex += change * multiplier
            complex into polar
            polar.addPhase(turnDegrees)
            polar into complex
        }

    }

}