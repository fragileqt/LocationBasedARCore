package com.zatek.locationbasedar

import android.location.Location
import com.google.ar.sceneform.NodeParent
import com.google.ar.sceneform.math.Vector3

class WrapperNode private constructor(val config: Config) : LocationBasedNode() {
    var lastLocation = Location("")

    override fun update(location: Location) {
        lastLocation = location
        //localPosition = Vector3(0f, 0f, 0f)
        worldNode()?.let {
            if (config.scaleWithWorld.not())
                localScale = Vector3(1f, 1f, 1f).scaled((1 / it.localScale.x))
        }
        this.children.forEach {
            if (it is LocationBasedNode)
                it.update(location)
        }
    }

    override fun setParent(p0: NodeParent?) {
        super.setParent(p0)
        update(lastLocation)
    }

    class Builder {
        private lateinit var config: Config
        fun setConfig(config: Config): Builder {
            this.config = config
            return this
        }

        fun build(): WrapperNode {
            if (::config.isInitialized.not())
                config = Config()
            return WrapperNode(
                config
            )
        }
    }

    data class Config(val scaleWithWorld: Boolean = true)
}