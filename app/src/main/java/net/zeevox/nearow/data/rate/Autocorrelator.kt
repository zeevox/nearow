package net.zeevox.nearow.data.rate

import net.zeevox.nearow.data.CircularDoubleBuffer

/** Utility class providing autocorrelation-related methods and functionality */
class Autocorrelator private constructor() {
    companion object {

        /**
         * A copy of the right half of the array is incrementally slid over the whole array. Each
         * sequential offset is scored according to the correlation of the slid and fixed data
         * points.
         *
         * @param data a ring buffer of the data to be scored
         * @return an array of offset-correlation (index-value) mappings
         */
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

        /**
         * Given an array of correlation scores stored in [frequencies] and a minimum frequency to
         * consider given by [minFreq], return the offset (index) of the best-correlated frequency.
         *
         * @param frequencies an array of offset correlations, e.g one given by [getFrequencyScores]
         * @param minFreq the minimum offset to consider, for eliminating DC and
         */
        fun getBestFrequency(frequencies: DoubleArray, minFreq: Int): Int =
            frequencies.indices.drop(minFreq).maxByOrNull { frequencies[it] } ?: -1
    }
}
