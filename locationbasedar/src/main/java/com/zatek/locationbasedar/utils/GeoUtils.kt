package com.zatek.locationbasedar.utils

import android.location.Location
import com.google.ar.sceneform.math.Vector3
import kotlin.math.*


object GeoUtils {

    fun convertGpsToECEF(
        location: Location
    ): Vector3 {
        val a = 6378137.0
        val b = 6356752.3142
        val e = 1 - b.pow(2.0) / a.pow(2.0)
        val n = a / sqrt(
            1.0 - e * sin(
                Math.toRadians(location.latitude)
            ).pow(2.0)
        )
        val cosLatRad = cos(Math.toRadians(location.latitude))
        val cosLongiRad = cos(Math.toRadians(location.longitude))
        val sinLatRad = sin(Math.toRadians(location.latitude))
        val sinLongiRad = sin(Math.toRadians(location.longitude))
        val x = (n + location.altitude) * cosLatRad * cosLongiRad
        val y = (n + location.altitude) * cosLatRad * sinLongiRad
        val z = (b.pow(2.0) / a.pow(2.0) * n + location.altitude) * sinLatRad
        val ecef = Vector3()
        ecef.x = x.toFloat()
        ecef.y = y.toFloat()
        ecef.z = z.toFloat()
        return ecef
    }


    fun convertECEFtoENU(
        ecefUser: Vector3,
        ecefPOI: Vector3,
        lat: Double,
        longi: Double
    ): Enu {
        val cosLatRad = cos(Math.toRadians(lat))
        val cosLongiRad = cos(Math.toRadians(longi))
        val sinLatRad = sin(Math.toRadians(lat))
        val sinLongiRad = sin(Math.toRadians(longi))
        val vector: MutableList<Double> = ArrayList()
        vector.add((ecefUser.x - ecefPOI.x).toDouble())
        vector.add((ecefUser.y - ecefPOI.y).toDouble())
        vector.add((ecefUser.z - ecefPOI.z).toDouble())
        val e = vector[0] * -sinLongiRad + vector[1] * cosLongiRad
        val n =
            vector[0] * -sinLatRad * cosLongiRad + vector[1] * -sinLatRad * sinLongiRad + vector[2] * cosLatRad
        val u =
            vector[0] * cosLatRad * cosLongiRad + vector[1] * cosLatRad * sinLongiRad + vector[2] * sinLatRad
        return Enu(e, n, u)
    }

    fun getCoordinatesInLocalWorld(
        currentLocation: Location,
        targetLocation: Location
    ): List<Double> {
        try {
            val myECEFLocation =
                convertGpsToECEF(
                    currentLocation
                )
            val targetECEFLocation =
                convertGpsToECEF(
                    targetLocation
                )
            val enuLocation =
                convertECEFtoENU(
                    targetECEFLocation,
                    myECEFLocation,
                    targetLocation.latitude,
                    targetLocation.longitude
                )
            return listOf(enuLocation.e, enuLocation.u, enuLocation.n * -1)
        } catch (e: Exception) {
            print(e)
        }
        return listOf(0.0, 0.0, 0.0)
    }

    fun enuToECEF(
        poiPositionDefault: Vector3,
        worldLocation: Location
    ): Vector3 {
        val myEcef = convertGpsToECEF(
            worldLocation
        )
        val poiPosition = Vector3(
            poiPositionDefault.x,
            poiPositionDefault.y,
            poiPositionDefault.z
        )

        val cosLat = cos(Math.toRadians(worldLocation.latitude))
        val cosLon = cos(Math.toRadians(worldLocation.longitude))
        val sinLat = sin(Math.toRadians(worldLocation.latitude))
        val sinLon = sin(Math.toRadians(worldLocation.longitude))

        val x = (-1f * sinLon) * poiPosition.x + (-1f * sinLat * cosLon) * poiPosition.y + (cosLat * cosLon) * poiPosition.z
        val y = cosLon * poiPosition.x + (-1f * sinLat * sinLon) * poiPosition.y + (cosLat * sinLon)*poiPosition.z
        val z =  (cosLat) * poiPosition.y + (sinLat) * poiPosition.z

        return Vector3.add(Vector3(
            x.toFloat(),
            y.toFloat(),
            z.toFloat()
        ),myEcef)
    }

    fun ecefToGeo(ecef: Vector3): Location {

        val a = 6378137.0
        val b = 6356752.3142

        val e = sqrt(a.pow(2) - b.pow(2)) / a

        val r = sqrt(ecef.x.pow(2) + ecef.y.pow(2))

        val e2 = (a.pow(2) - b.pow(2)) / b.pow(2)

        val F = 54.0 * b.pow(2) * ecef.z.pow(2)

        val G = r.pow(2) + (1.0 - e.pow(2)) * ecef.z.pow(2) - e.pow(2) * (a.pow(2) - b.pow(2))

        val c = (e.pow(4) * F * r.pow(2)) / G.pow(3)

        val s = (1.0 + c + sqrt(c.pow(2) + 2.0 * c)).root(3)

        val P = F / (3.0 * (s + 1.0 / s + 1.0).pow(2) * G.pow(2))

        val Q = sqrt(1.0 + 2.0 * e.pow(4) * P)

        val r01 = (-1.0 * P * e.pow(2) * r) / (1.0 + Q)
        val r02 = (1.0 / 2.0) * (a.pow(2)) * (1.0 + 1.0 / Q)
        val r03 = (P * (1.0 - e.pow(2)) * ecef.z.pow(2)) / (Q * (1.0 + Q))
        val r04 = (1.0 / 2.0) * P * r.pow(2)
        val r0 = r01 +
                sqrt(
                    r02 - r03 - r04
                )

        val U = sqrt((r - e.pow(2) * r0).pow(2) + ecef.z.pow(2))

        val V = sqrt((r - e.pow(2) * r0).pow(2) + (1.0 - e.pow(2)) * ecef.z.pow(2))

        val z0 = (b.pow(2) * ecef.z) / (a * V)

        val h = U * (1.0 - b.pow(2) / (a * V))

        val lat = atan((ecef.z + e2 * z0) / r)
        val lon = atan2(ecef.y, ecef.x)

        return Location("").apply {
            latitude = Math.toDegrees(lat)
            longitude = Math.toDegrees(lon.toDouble())
            altitude = h
        }
    }
}

private fun Double.root(i: Int): Double {
    return this.pow(1.0 / i.toDouble())
}

data class Enu(
    val e: Double,
    val n: Double,
    val u: Double
)
