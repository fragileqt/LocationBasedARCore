package com.zatek.locationbasedar

import android.location.Location
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.ar.sceneform.math.Vector3
import com.zatek.locationbasedar.utils.Enu
import com.zatek.locationbasedar.utils.GeoUtils

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class AppContextTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.zatek.locationbasedar.test", appContext.packageName)
    }

    @Test
    fun ecefToGeoConversionTest(){
        val locations = listOf(Location("").apply{
            latitude = 49.213460
            longitude = 18.749457
            altitude = 354.0
        })
        val ecefs = listOf(
            Vector3(3953181.8f, 1341882.6f, 4806367.5f)
        )
        locations.zip(ecefs).forEach {(location, ecef) ->
            val converted = GeoUtils.convertGpsToECEF(location)
            assertEquals(ecef, converted)
        }
        locations.zip(ecefs).forEach {(location, ecef) ->
            val inverted = GeoUtils.ecefToGeo(ecef)
            assertTrue(inverted.distanceTo(location) < 1)
        }
    }

    @Test
    fun ecefToEnuConversionTest(){
        val origin = Location("").apply{
            latitude = 49.213460
            longitude = 18.749457
            altitude = 354.0
        }
        val locations = listOf(Location("").apply{
            latitude = 49.212748
            longitude = 18.748063
            altitude = 354.0
        }, Location("").apply{
            latitude = 49.214513
            longitude = 18.737469
            altitude = 354.0
        })
        val enus = listOf(
            Enu(
                e = -101.56205603153107,
                n = -79.103484554047,
                u = 0.2305017479090381
            ),
            Enu(
                e = -873.4796903476274,
                n = 117.00502289975478,
                u = 0.09105734835895873
            )
        )
        locations.zip(enus).forEach {(location, expectedEnu) ->
            val poiEcef = GeoUtils.convertGpsToECEF(location)
            val originEcef = GeoUtils.convertGpsToECEF(origin)
            val enu = GeoUtils.convertECEFtoENU(poiEcef, originEcef, location.latitude, location.longitude)
            assertEquals(enu, expectedEnu)
        }
        locations.zip(enus).forEach{(location, expectedEnu)->
            val poiEcef = GeoUtils.convertGpsToECEF(location)
            val originEcef = GeoUtils.convertGpsToECEF(origin)
            val enu = GeoUtils.convertECEFtoENU(poiEcef, originEcef, location.latitude, location.longitude)
            val ecef = GeoUtils.enuToECEF(
                Vector3((enu.e).toFloat(), enu.n.toFloat(), enu.u.toFloat()),
                origin
            )
            val loc = GeoUtils.ecefToGeo(ecef)
            assertTrue(loc.distanceTo(location) < 1)
        }
    }
}
