package net.zeevox.nearow.data.rate

import net.zeevox.nearow.data.CircularDoubleBuffer

class Autocorrelator {
    companion object {

        fun getFrequencyScores(data: CircularDoubleBuffer): DoubleArray {
            if (data.size < 10) return DoubleArray(0)

            val mean = data.average()
            val output = DoubleArray(data.size / 2)
            for (shift in output.indices) {
                var num = 0.0
                var den = 0.0
                for (index in data.indices) {
                    val xim = data[index] - mean
                    num += xim * (data[(index + shift) % data.size] - mean)
                    den += xim * xim
                }
                output[shift] = num / den
            }

            return output
        }

        fun getBestFrequency(frequencies: DoubleArray, minFreq: Int): Int =
            frequencies.indices.drop(minFreq).maxByOrNull { frequencies[it] } ?: -1
    }
}