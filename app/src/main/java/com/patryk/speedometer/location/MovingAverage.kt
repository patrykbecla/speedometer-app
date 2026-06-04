package com.patryk.speedometer.location

/** Sliding-window moving average over [windowSize] most-recent samples. */
class MovingAverage(private val windowSize: Int) {
    private val samples = ArrayDeque<Float>()

    fun add(value: Float): Float {
        samples.addLast(value)
        if (samples.size > windowSize) samples.removeFirst()
        return samples.average().toFloat()
    }

    fun current(): Float? = if (samples.isEmpty()) null else samples.average().toFloat()

    fun reset() = samples.clear()
}
