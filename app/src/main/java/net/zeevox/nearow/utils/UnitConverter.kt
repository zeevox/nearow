package net.zeevox.nearow.utils

import kotlin.math.pow

class UnitConverter {
    companion object {
        /**
         * Convert a speed, measured in metres per second, into the number
         * of seconds that would be required to cover 500m at this speed.
         */
        fun speedToSecondsPer500(speed: Float): Float = 500 / speed

        /**
         * Convert a speed, measured in metres per seconds, into a formatted
         * string with split in mm:ss.s/500m
         */
        fun speedToSplitFormatted(speed: Float): String {
            // if speed is slower than 10 minute pace call it zero
            if (speed < 0.8) return "0:00.0"

            val totalSeconds = speedToSecondsPer500(speed)
            val minutes: Int = ((totalSeconds % 3600) / 60).toInt()
            val seconds: Double = (totalSeconds % 60).toDouble()
            return String.format("%d:%.1f", minutes, seconds)
        }

        /**
         * Convert [speed], measured in metres per second, into a pace,
         * where pace is time in seconds over distance in meters.
         */
        fun speedToPace(speed: Float): Float = 1 / speed

        /**
         * Convert [pace] to watts, where pace is time in seconds over distance in meters.
         * For example: a 2:05/500m split = 125 seconds/500 meters or a 0.25 pace.
         * Watts are then calculated as (2.80/0.25 ^ 3) or (2.80/0.015625), which equals 179.2.
         * https://www.concept2.com/indoor-rowers/training/calculators/watts-calculator
         */
        fun paceToWatts(pace: Float): Double = 2.80 / pace.pow(3)

        fun speedToWatts(speed: Float): Double = paceToWatts(speedToPace(speed))
    }
}