package com.zatek.locationbasedar

import android.location.Location
import com.google.ar.sceneform.NodeParent
import com.google.ar.sceneform.math.Vector3

class LocationNode private constructor(val location: Location, val config: Config) : LocationBasedNode() {

    override fun update(location: Location) {
        this.localPosition = this.location.toLocalCoordinates(location)
        if(config.relativeScaling)
            worldNode()?.let {
                this.localScale =
                    Vector3(1f, 1f, 1f).scaled(it.mostAccurateLocation.distanceTo(location))
            }

        this.localPosition = Vector3(
            config.fixCoordinate[0]?:this.localPosition.x,
            config.fixCoordinate[1]?:this.localPosition.y,
            config.fixCoordinate[2]?:this.localPosition.z
        )

        if(config.scaleWithWorld){
            worldNode()?.let {
                this.localPosition = localPosition.scaled(it.localScale.x)
            }
        }
    }

    override fun setParent(p0: NodeParent?) {
        super.setParent(p0)
        update(location)
    }

    class Builder{
        private lateinit var location: Location
        private lateinit var config: Config
        fun setLocation(location: Location): Builder {
            this.location = location
            return this
        }
        fun setConfig(config: Config): Builder {
            this.config = config
            return this
        }
        fun build(): LocationNode {
            return LocationNode(
                location,
                config
            )
        }
    }

    data class Config(
        val relativeScaling: Boolean = false,
        val fixCoordinate: List<Float?> = listOf(null, null, null),
        val scaleWithWorld: Boolean = true
    )
/*
    fun scale(scaleCoef: Float = 1f, positionCoef: Float = 1f){
        this.scaleCoef = scaleCoef
        this.positionCoef = positionCoef
        update(lastLocation)
    }

    fun fixAltitude(altitude: Float) {
        fixedAltitude = altitude
    }
*/
}