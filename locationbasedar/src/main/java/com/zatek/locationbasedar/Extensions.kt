package com.zatek.locationbasedar

import android.hardware.GeomagneticField
import android.location.Location
import com.google.ar.sceneform.Camera
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3

val Float.normalizedDegrees : Int get() = (360 + this.toInt()) % 360

fun Camera.northRotation(azimuth: Float, location: Location): Quaternion {
    val quaternion = this.worldRotation

    val geoField = GeomagneticField(
        location.latitude.toFloat(),
        location.longitude.toFloat(),
        location.altitude.toFloat(),
        System.currentTimeMillis()
    )

    val direction: Float = azimuth + geoField.declination

    val rotatedToNorth = Quaternion.axisAngle(Vector3(0f,1f,0f), direction)

    return Quaternion.multiply(rotatedToNorth,quaternion.extractRotation(Vector3(0f,1f,0f)))
}

fun Location.toLocalCoordinates(currentLocation: Location): Vector3 {
    if(this.altitude == 0.0)
        this.altitude == currentLocation.altitude

    val mapped = GeoUtils.getCoordinatesInLocalWorld(
        currentLocation,
        this
    )
    return Vector3(mapped[0].toFloat(), mapped[1].toFloat(), mapped[2].toFloat())
}

fun Vector3.toWorldCoordinates(worldPosition: Vector3, worldLocation: Location): Location{
    val ecef = GeoUtils.ECEF(this.scaled(1.008f), worldLocation)
    return GeoUtils.WSG(ecef)
}

fun Quaternion.extractRotation(rotation: Vector3): Quaternion{
    return Quaternion(this).apply{
        val mag = Math.sqrt(Math.pow(x*rotation.x.toDouble(),2.0) + Math.pow(y*rotation.y.toDouble(), 2.0) + Math.pow(z*rotation.z.toDouble(),2.0) + Math.pow((w*w).toDouble(),2.0)).toFloat()
        x = x * rotation.x / mag
        y = y * rotation.y / mag
        z = z * rotation.z / mag
        w /= mag
    }
}

val List<Double>.toLocation: Location
    get() = Location("").let { location ->
        location.latitude = this[0]
        location.longitude = this[1]
        location
    }
