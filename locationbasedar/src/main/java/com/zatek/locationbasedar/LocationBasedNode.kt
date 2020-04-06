package com.zatek.locationbasedar

import android.location.Location
import com.google.ar.sceneform.Node

abstract class LocationBasedNode(): Node() {
    abstract fun update(location: Location)
    fun worldNode() : WorldNode?{
        var node = this as Node?
        while(node !is WorldNode){
            node = node?.parent
            if(node == null) break
        }
        return node as WorldNode?
    }
}