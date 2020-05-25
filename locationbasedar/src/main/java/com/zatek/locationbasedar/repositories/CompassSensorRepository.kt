package com.zatek.locationbasedar.repositories

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.zatek.locationbasedar.utils.AverageAngle
import io.reactivex.subjects.BehaviorSubject


class CompassSensorRepository(val context: Context) : SensorEventListener {
    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometerSensor: Sensor
    private val magneticFieldSensor: Sensor
    private val temporaryRotationMatrix = FloatArray(9)
    private val rotationMatrix = FloatArray(9)
    private val orientationData = FloatArray(3)

    private var accelerometerData: SensorDataHolder =
        SensorDataHolder(
            0.01f,
            true
        )
    private var magneticData: SensorDataHolder =
        SensorDataHolder(
            0.01f,
            true
        )

    val magneticNorthAzimuthSubject = BehaviorSubject.create<Float>()
    val accuracySubject = BehaviorSubject.create<Int>()

    var azimuth = 0f
        private set

    var isSuspended = true
        private set

    fun onResume() {
        isSuspended = false
        sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(this, magneticFieldSensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun onPause() {
        isSuspended = true
        sensorManager.unregisterListener(this, accelerometerSensor)
        sensorManager.unregisterListener(this, magneticFieldSensor)
    }

    val avg = AverageAngle(60)

    override fun onSensorChanged(event: SensorEvent) {
        val sensorType = event.sensor.type
        if (sensorType == Sensor.TYPE_ACCELEROMETER) {
            accelerometerData.update(event.values)
        }
        else if (sensorType == Sensor.TYPE_MAGNETIC_FIELD) {
            magneticData.update(event.values)
        }
        if (accelerometerData.hasBeenInitialized && magneticData.hasBeenInitialized) {

            SensorManager.getRotationMatrix(
                temporaryRotationMatrix,
                null,
                accelerometerData.data,
                magneticData.data
            )
            SensorManager.remapCoordinateSystem(
                temporaryRotationMatrix,
                SensorManager.AXIS_X,
                SensorManager.AXIS_Z,
                rotationMatrix
            )
            SensorManager.getOrientation(rotationMatrix, orientationData)

            avg.putValue(orientationData[0].toDouble())

            azimuth = Math.toDegrees(avg.average).toFloat()

            magneticNorthAzimuthSubject.onNext(azimuth)
        }
    }


    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        accuracySubject.onNext(accuracy)
    }


    init {
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magneticFieldSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }

    data class SensorDataHolder(val bias: Float, val isFilterAllowed: Boolean) {
        private var backingFloatArray = FloatArray(3)
        private var previousTests = mutableListOf<FloatArray>()
        var hasBeenInitialized = false

        fun update(data: FloatArray) {
            if(previousTests.isEmpty()){
                previousTests.add(data)
                backingFloatArray = data
            }
            if(isFilterAllowed)
                applyFilter(data)
            else
                backingFloatArray = data
            hasBeenInitialized = true
        }
        private fun applyFilter(data: FloatArray){
            if(previousTests.isEmpty()){
                previousTests.add(
                    data.map{
                        (1 - bias) * it
                    }.toFloatArray()
                )
            }else{
                val latestData = previousTests.last()
                previousTests.clear()
                previousTests.add(
                    data.mapIndexed{ index, value ->
                        latestData[index] * bias + value * (1 - bias)
                    }.toFloatArray()
                )
            }
            backingFloatArray = previousTests.last()
        }

        val data: FloatArray get() = backingFloatArray
    }
}