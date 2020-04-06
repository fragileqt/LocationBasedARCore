package com.zatek.locationbasedar

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class AverageAngle(private val mNumberOfFrames: Int) {
    private val mValues: DoubleArray = DoubleArray(mNumberOfFrames)
    private var mCurrentIndex = 0
    private var mIsFull = false
    var average = Double.NaN
        private set

    fun putValue(d: Double) {
        mValues[mCurrentIndex] = d
        if (mCurrentIndex == mNumberOfFrames - 1) {
            mCurrentIndex = 0
            mIsFull = true
        } else {
            mCurrentIndex++
        }
        updateAverageValue()
    }

    private fun updateAverageValue() {
        var numberOfElementsToConsider = mNumberOfFrames
        if (!mIsFull) {
            numberOfElementsToConsider = mCurrentIndex + 1
        }
        if (numberOfElementsToConsider == 1) {
            average = mValues[0]
            return
        }
        // Formula: http://en.wikipedia.org/wiki/Circular_mean
        var sumSin = 0.0
        var sumCos = 0.0
        for (i in 0 until numberOfElementsToConsider) {
            val v = mValues[i]
            sumSin += sin(v)
            sumCos += cos(v)
        }
        average = atan2(sumSin, sumCos)
    }

}