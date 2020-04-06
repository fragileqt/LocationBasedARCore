package com.zatek.locationbasedar

import android.location.Location
import android.opengl.Matrix
import com.google.ar.sceneform.math.Vector3
import kotlin.math.*


object GeoUtils {

    fun convertGpsToECEF(
        lat: Double,
        longi: Double,
        alt: Float
    ): Vector3 {
        val a = 6378137.0
        val b = 6356752.3142
        val e = 1 - b.pow(2.0) / a.pow(2.0)
        val n = a / sqrt(
            1.0 - e * sin(
                Math.toRadians(lat)
            ).pow(2.0)
        )
        val cosLatRad = cos(Math.toRadians(lat))
        val cosLongiRad = cos(Math.toRadians(longi))
        val sinLatRad = sin(Math.toRadians(lat))
        val sinLongiRad = sin(Math.toRadians(longi))
        val x = (n + alt) * cosLatRad * cosLongiRad
        val y = (n + alt) * cosLatRad * sinLongiRad
        val z = (b.pow(2.0) / a.pow(2.0) * n + alt) * sinLatRad
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
            val myECEFLocation = convertGpsToECEF(
                currentLocation.latitude,
                currentLocation.longitude,
                currentLocation.altitude.toFloat()
            )
            val targetECEFLocation = convertGpsToECEF(
                targetLocation.latitude,
                targetLocation.longitude,
                targetLocation.altitude.toFloat()
            )
            val enuLocation = convertECEFtoENU(
                myECEFLocation,
                targetECEFLocation,
                targetLocation.latitude,
                targetLocation.longitude
            )
            return listOf(enuLocation.e * -1, enuLocation.u, enuLocation.n)
        } catch (e: Exception) {
            print(e)
        }
        return listOf(0.0, 0.0, 0.0)
    }

    fun enuToECEF(
        poiPositionDefault: Vector3,
        worldPosition: Vector3,
        worldLocation: Location
    ): Vector3 {
        val myEcef = convertGpsToECEF(
            worldLocation.latitude,
            worldLocation.longitude,
            worldLocation.altitude.toFloat()
        )
        val myEnu = convertECEFtoENU(myEcef,myEcef,worldLocation.latitude,worldLocation.longitude)
        val poiPosition = Vector3(
            poiPositionDefault.x - worldPosition.x,
            myEnu.u.toFloat(),
            poiPositionDefault.z - worldPosition.z
        )



        val result = FloatArray(4)
        val cosLat = cos(Math.toRadians(worldLocation.latitude))
        val cosLon = cos(Math.toRadians(worldLocation.longitude))
        val sinLat = sin(Math.toRadians(worldLocation.latitude))
        val sinLon = sin(Math.toRadians(worldLocation.longitude))
        val first = floatArrayOf(
            (-1f * sinLon).toFloat(),
            (-1f * sinLat * cosLon).toFloat(),
            (cosLat * cosLon).toFloat(),
            0f,
            cosLon.toFloat(),
            (-1f * sinLat * sinLon).toFloat(),
            (cosLat * sinLon).toFloat(),
            0f,
            0f,
            (cosLat).toFloat(),
            (sinLat).toFloat(),
            0f,
            0f,
            0f,
            0f,
            0f
        )
        Matrix.multiplyMV(
            result,
            0,
            first,
            0,
            floatArrayOf(poiPosition.x*-1,poiPosition.z ,poiPosition.y,0f),
            0
        )
        return Vector3.add(Vector3(
            result[0],
            result[1],
            result[2]
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

    fun WSG(ecef: Vector3): Location{
        val xPos = ecef.x.toDouble()
        val yPos = ecef.y.toDouble()
        val zPos = ecef.z.toDouble()

        val a = 6378137.0
        val erf = 1.0/298.257223563
        val b = a*
                (1.0-erf)


        val erfs = 2*erf-Math.pow(erf, 2.0)

        val asq = Math.pow(a, 2.0)
        val bsq = Math.pow(b, 2.0)

        val ep = Math.sqrt((asq - bsq) / bsq)

        val p =
            Math.sqrt(Math.pow(xPos, 2.0) + Math.pow(yPos, 2.0))

        val th = Math.atan2(
            a *
                    zPos, b * p
        )

        var long = Math.atan2(yPos, xPos)

        val lat = Math.atan2(
            zPos + Math.pow(ep, 2.0) *
                    b * Math.pow(
                Math.sin(th),
                3.0
            ),
            p - erfs *
                    a *
                    Math.pow(Math.cos(th), 3.0)
        )

        val n: Double = a /
                Math.sqrt(
                    1 - erfs *
                            Math.pow(Math.sin(lat), 2.0)
                )


        val alt = p / Math.cos(lat) - n
        long = long % (2 * Math.PI)

        return Location("").apply {
            latitude  = Math.toDegrees(lat)
            longitude = Math.toDegrees(long)
            altitude = alt
        }
    }

    fun ECEF(
        eastNorthUp: Vector3,
        refPt: Location
    ) :Vector3 { //ENU translation requires a reference point in the
//Earth-Centered, Earth-Fixed frame
        val ecef = Vector3()
        val refECEF = convertGpsToECEF(refPt.latitude, refPt.longitude, refPt.altitude.toFloat())
        ecef.x = (-1 * Math.sin(refECEF.x.toDouble()) *
                    eastNorthUp.x -
                    Math.cos(refPt.longitude) *
                    Math.sin(refPt.latitude) *
                    eastNorthUp.y +
                    Math.cos(refPt.longitude) *
                    Math.cos(refPt.latitude) *
                    eastNorthUp.z +
                    refECEF.x).toFloat()
        ecef.y = (
            Math.cos(refPt.longitude) *
                    eastNorthUp.x -
                    Math.sin(refPt.longitude) *
                    Math.sin(refPt.latitude) *
                    eastNorthUp.y +
                    Math.cos(refPt.latitude) *
                    Math.sin(refPt.longitude) *
                    eastNorthUp.z +
                    refECEF.y).toFloat()
        ecef.z = (
            Math.cos(refPt.latitude) *
                    eastNorthUp.y + Math.sin(refPt.latitude) *
                    eastNorthUp.z +
                    refECEF.z).toFloat()
        return ecef
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
