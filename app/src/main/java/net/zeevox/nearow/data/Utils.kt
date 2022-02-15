package net.zeevox.nearow.data

import kotlin.math.sqrt

class Utils {
    companion object {
        /**
         * Return smallest power of two greater than or equal to n
         */
        fun nextPowerOf2(n: Int): Int {
            // base case already power of 2
            if (n > 0 && n and n - 1 == 0) return n

            // increment through powers of two until find one larger than n
            var p = 1
            while (p < n) p = p shl 1
            return p
        }

        fun magnitude(triple: DoubleArray): Double =
            sqrt(triple[0] * triple[0] + triple[1] * triple[1] + triple[2] * triple[2])

    }
}