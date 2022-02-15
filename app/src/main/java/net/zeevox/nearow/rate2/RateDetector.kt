package net.zeevox.nearow.rate2

import androidx.collection.CircularArray

class RateDetector(sampleRate: Int) {

    /**
     * Return smallest power of two greater than or equal to n
     */
    fun nextPowerOf2(n: Int): Int {
        var p = 1
        if (n > 0 && n and n - 1 == 0) return n
        while (p < n) p = p shl 1
        return p
    }

    private val bufferSize = nextPowerOf2(sampleRate * 10)

    private val circularBuffer: CircularArray<Double> = CircularArray()
//    private val noise: Noise = Noise.real(bufferSize)

    private val src = FloatArray(bufferSize)
    private val dst = FloatArray(bufferSize + 2)

    /*fun compute() {
        val fft = noise.fft(src, dst)

        for (i in 0 until fft.size / 2) {
            val real = fft[i * 2]
            val imaginary = fft[i * 2 + 1]
            System.out.printf("index: %d, real: %.5f, imaginary: %.5f\n", i, real, imaginary)
        }
    }*/
}