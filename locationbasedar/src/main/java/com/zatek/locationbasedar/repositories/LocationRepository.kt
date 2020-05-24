package com.zatek.locationbasedar.repositories

import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.zatek.locationbasedar.WorldNode
import io.reactivex.rxkotlin.withLatestFrom
import io.reactivex.subjects.BehaviorSubject


class LocationRepository(
    config: WorldNode.Config,
    val context: Context,
    onReferencingLocationChangedListener: WorldNode.OnReferencingLocationChangedListener?
) : LocationCallback() {

    private val locationSubject = BehaviorSubject.create<Location>()
    private val mostAccurateAccuracy = BehaviorSubject.createDefault(Float.MAX_VALUE)

    var mostAccurateLocation = Location("").apply{
        accuracy = Float.MAX_VALUE
    }

    val mostAccurateSubject = locationSubject
        .withLatestFrom(mostAccurateAccuracy)
        .filter { (location, accuracy) ->
            when{
                config.updateIntervalInMeters < mostAccurateLocation.distanceTo(location) -> true
                config.updateOnMoreAccurateLocation && accuracy > location.accuracy && location.accuracy < config.locationAccuracyThreshold -> true
                config.updateOnMoreAccurateLocation.not() -> true
                else -> false
            }
        }
        .map {
            mostAccurateLocation = it.first
            onReferencingLocationChangedListener?.locationChanged(mostAccurateLocation)
            mostAccurateAccuracy.onNext(mostAccurateLocation.accuracy)
            mostAccurateLocation
        }

    fun startUpdates() {
        val locationRequest = LocationRequest().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 0
            fastestInterval = 0
        }

        FusedLocationProviderClient(context).requestLocationUpdates(
            locationRequest,
            this,
            Looper.myLooper()
        )
    }

    fun stopUpdates(){
        FusedLocationProviderClient(context).removeLocationUpdates(this)
    }

    override fun onLocationResult(locationResult: LocationResult?) {
        locationResult?.let {
            locationSubject.onNext(it.lastLocation)
        }
        super.onLocationResult(locationResult)
    }

    fun setLocation(location: Location) {
        mostAccurateAccuracy.onNext(Float.MAX_VALUE)
        locationSubject.onNext(location)
    }

    fun resetAccuracy() {
        mostAccurateAccuracy.onNext(Float.MAX_VALUE)
    }
}