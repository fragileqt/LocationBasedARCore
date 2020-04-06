package com.zatek.locationbasedar

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import io.reactivex.subjects.BehaviorSubject


class CompassSensorManager(context: Context) : SensorEventListener {
    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometerSensor: Sensor
    private val magneticFieldSensor: Sensor
    private val temporaryRotationMatrix = FloatArray(9)
    private val rotationMatrix = FloatArray(9)
    private val orientationData = FloatArray(3)
    private var accelerometerData: SensorDataHolder = SensorDataHolder(.25f)
    private var magneticData: SensorDataHolder = SensorDataHolder(0f)

    val magneticNorthAzimuthSubject = BehaviorSubject.create<Float>()

    var azimuth = 0f
        private set

    var isSuspended = true
        private set

    fun onResume() {
        isSuspended = false
        sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, magneticFieldSensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun onPause() {
        isSuspended = true
        sensorManager.unregisterListener(this, accelerometerSensor)
        sensorManager.unregisterListener(this, magneticFieldSensor)
    }
    val avg = AverageAngle(10)
    override fun onSensorChanged(event: SensorEvent) {
        val sensorType = event.sensor.type
        if (sensorType == Sensor.TYPE_ACCELEROMETER) accelerometerData.update(event.values)
        else if (sensorType == Sensor.TYPE_MAGNETIC_FIELD) magneticData.update(event.values)
        if (accelerometerData.hasBeenInitialized && magneticData.hasBeenInitialized) {
            SensorManager.getRotationMatrix(
                temporaryRotationMatrix,
                null,
                accelerometerData.data,
                magneticData.data
            )
            configureDeviceAngle()
            SensorManager.getOrientation(rotationMatrix, orientationData)
            avg.putValue(orientationData[0].toDouble())
            azimuth = Math.toDegrees(avg.average).toFloat()
            magneticNorthAzimuthSubject.onNext(azimuth)
        }
    }

    private fun configureDeviceAngle() {
        when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_0 -> SensorManager.remapCoordinateSystem(
                temporaryRotationMatrix,
                SensorManager.AXIS_Z,
                SensorManager.AXIS_Y,
                rotationMatrix
            )
            Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(
                temporaryRotationMatrix,
                SensorManager.AXIS_Y,
                SensorManager.AXIS_MINUS_Z,
                rotationMatrix
            )
            Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(
                temporaryRotationMatrix,
                SensorManager.AXIS_MINUS_Z,
                SensorManager.AXIS_MINUS_Y,
                rotationMatrix
            )
            Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(
                temporaryRotationMatrix,
                SensorManager.AXIS_MINUS_Y,
                SensorManager.AXIS_Z,
                rotationMatrix
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}


    companion object {
        fun isDeviceCompatible(context: Context): Boolean {
            return (context.packageManager != null && context.packageManager.hasSystemFeature(
                PackageManager.FEATURE_SENSOR_ACCELEROMETER
            )
                    && context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_COMPASS))
        }
    }

    init {
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magneticFieldSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }

    data class SensorDataHolder(val bias: Float) {
        private var backingFloatArray = FloatArray(3)
        var hasBeenInitialized = false

        fun update(data: FloatArray) {
            if (!hasBeenInitialized) backingFloatArray = data
            else {
                backingFloatArray[0] = backingFloatArray[0] * bias + data[0] * (1 - bias)
                backingFloatArray[1] = backingFloatArray[1] * bias + data[1] * (1 - bias)
                backingFloatArray[2] = backingFloatArray[2] * bias + data[2] * (1 - bias)
            }
            hasBeenInitialized = true
        }

        val data: FloatArray get() = backingFloatArray
    }
}