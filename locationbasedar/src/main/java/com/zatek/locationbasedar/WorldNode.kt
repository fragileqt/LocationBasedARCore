package com.zatek.locationbasedar

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.hardware.GeomagneticField
import android.location.Location
import android.widget.Toast
import com.google.ar.core.Camera
import com.google.ar.core.Plane
import com.google.ar.core.Trackable
import com.google.ar.sceneform.*
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.ux.ArFragment
import com.tbruyelle.rxpermissions2.RxPermissions
import com.zatek.locationbasedar.repositories.CompassSensorRepository
import com.zatek.locationbasedar.repositories.LocationRepository
import com.zatek.locationbasedar.utils.doOn
import com.zatek.locationbasedar.utils.northRotation
import com.zatek.locationbasedar.utils.toWorldCoordinates
import com.zatek.locationbasedar.utils.with
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.withLatestFrom
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import java.lang.IllegalArgumentException
import java.util.*

class WorldNode private constructor(
    private val fragment: ArFragment,
    val config: Config,
    private val arScene: Scene,
    private val onReferencingLocationChangedListener: OnReferencingLocationChangedListener?,
    private val onCompassRotationChangedListener: OnCompassRotationChangedListener?
) : Node() {

    private val context: Context = fragment.requireContext()
    private val rxPermissions: RxPermissions = RxPermissions(fragment)

    private val locationRepository = LocationRepository(config, context, onReferencingLocationChangedListener)

    private val compassSensorManager by lazy {
        CompassSensorRepository(context)
    }

    private val compositeDisposable = CompositeDisposable()

    private val requestPermissionSubject = BehaviorSubject.create<Unit>()
    private val permissionsGrantedSubject = BehaviorSubject.createDefault(false)
    private val isActivatedSubject = BehaviorSubject.create<Boolean>()

    private val updateSubject = BehaviorSubject.createDefault(true)

    private val cameraSubject = BehaviorSubject.create<Camera>()

    private var childrenSize = 0

    val mostAccurateLocation: Location get() = locationRepository.mostAccurateLocation

    private val mostAccurateLocationSubject = locationRepository.mostAccurateSubject.share()

    private val refreshSubject = BehaviorSubject.create<Long>()

    fun getEstimatedLocation(): Location {
        val node = Node()
        node.setParent(this)
        node.worldPosition = arScene.camera.worldPosition
        return node.localPosition.toWorldCoordinates(mostAccurateLocation).also{
            node.setParent(null)
        }
    }

    fun setLocation(location: Location) {
        locationRepository.setLocation(location)
    }

    fun stopUpdates() {
        locationRepository.stopUpdates()
        updateSubject.onNext(false)
    }

    fun startUpdates() {
        locationRepository.startUpdates()
        updateSubject.onNext(true)
    }

    private fun NodeParent.getRealSize(): Int {
        return this.children.size + this.children.sumBy { it.getRealSize() }
    }

    private fun init() {
        this.setParent(arScene)

        arScene.camera.farClipPlane = config.maxRenderingDistance

        if (config.receiveLocationUpdates)
            compositeDisposable.add(
                requestPermissionSubject
                    .withLatestFrom(permissionsGrantedSubject)
                    .filter {
                        it.second.not()
                    }.switchMap {
                        rxPermissions.requestEachCombined(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    }.subscribe({
                        if (it.granted) {
                            permissionGranted()
                        }
                        permissionsGrantedSubject.onNext(it.granted)
                    }, {
                        it.printStackTrace()
                    })
            )
        compositeDisposable.addAll(
            // Update rotácie uzlu
            doOn(
                compassSensorManager.magneticNorthAzimuthSubject,
                compassSensorManager.accuracySubject
            ).with(
                compassSensorManager.magneticNorthAzimuthSubject,
                compassSensorManager.accuracySubject,
                mostAccurateLocationSubject
            )
                .subscribeOn(Schedulers.io())
                .subscribe { (azimuth, accuracy, location) ->
                    val geoField = GeomagneticField(
                        location.latitude.toFloat(),
                        location.longitude.toFloat(),
                        location.altitude.toFloat(),
                        System.currentTimeMillis()
                    )

                    val direction: Float = azimuth + geoField.declination

                    onCompassRotationChangedListener?.rotationChanged(direction, accuracy)
                },
            // Update rotácie a pozície
            doOn(
                isActivatedSubject.filter{ it },
                mostAccurateLocationSubject
            ).with(
                mostAccurateLocationSubject,
                compassSensorManager.magneticNorthAzimuthSubject
            ).subscribe { (location, rotation) ->
                val frame = (this.arScene.view as ArSceneView).arFrame
                val camera = this.arScene.camera
                val view = this.arScene.view
                val floorY = frame?.let { frame ->
                    val points = frame.hitTest(
                        (view?.width?.toFloat() ?: 0f) / 2,
                        view?.height?.toFloat() ?: 0f
                    )
                    points.find { it is Plane }?.hitPose?.ty()
                } ?: 0f
                this.worldPosition = Vector3(camera.worldPosition.x, floorY, camera.worldPosition.z)
                if (config.northRotated)
                    this.worldRotation = camera?.northRotation(rotation, location)
                (this as NodeParent).children.forEach {
                    if (it is LocationBasedNode)
                        it.update(location)
                }
            },
            doOn(
                refreshSubject.distinctUntilChanged(),
                mostAccurateLocationSubject
            )
                .with(mostAccurateLocationSubject)
                .subscribeOn(Schedulers.io())
                .subscribe { location ->
                    children.forEach {
                        when (it) {
                            is LocationNode -> it.update(location)
                            is WrapperNode -> it.update(location)
                        }
                    }
                }
        )
    }

    @SuppressLint("MissingPermission")
    private fun permissionGranted() {
        startUpdates()
    }

    fun update() {
        refreshSubject.onNext(Date().time)
    }


    override fun onActivate() {
        super.onActivate()
        compassSensorManager.onResume()
        isActivatedSubject.onNext(true)
    }

    override fun onDeactivate() {
        super.onDeactivate()
        compassSensorManager.onPause()
        locationRepository.resetAccuracy()
        isActivatedSubject.onNext(false)
    }

    private var storedLatestPosition = Vector3.zero()
    override fun onUpdate(frameTime: FrameTime?) {
        super.onUpdate(frameTime)
        frameTime?.let {
            (this.arScene.view as ArSceneView).arFrame?.camera?.let {
                cameraSubject.onNext(it)
            }
        }
        if (childrenSize != getRealSize()) {
            childrenSize = getRealSize()
            refreshSubject.onNext(System.currentTimeMillis())
            storedLatestPosition = worldPosition
            Toast.makeText(fragment.context, "Pose updated", Toast.LENGTH_LONG).show()
        }
        requestPermissionSubject.onNext(Unit)
    }

    override fun setLocalScale(p0: Vector3?) {
        super.setLocalScale(p0)
        update()
    }

    class Builder {
        private lateinit var fragmentActivity: ArFragment
        private lateinit var config: Config
        private lateinit var scene: Scene
        private var onReferencingLocationChangedListener: OnReferencingLocationChangedListener? =
            null
        private var onCompassRotationChangedListener: OnCompassRotationChangedListener? = null

        fun with(arFragment: ArFragment): Builder {
            this.fragmentActivity = arFragment
            return this
        }

        fun setConfig(config: Config): Builder {
            this.config = config
            return this
        }

        fun setScene(scene: Scene): Builder {
            this.scene = scene
            return this
        }

        fun setOnReferencingLocationChangedListener(listener: OnReferencingLocationChangedListener): Builder {
            this.onReferencingLocationChangedListener = listener
            return this
        }

        fun setOnCompassRotationChangedListener(listener: OnCompassRotationChangedListener): Builder {
            this.onCompassRotationChangedListener = listener
            return this
        }

        fun build(): WorldNode {
            if (::fragmentActivity.isInitialized.not()) {
                throw IllegalArgumentException("Please call Builder.with with ArFragment")
            }
            if (::fragmentActivity.isInitialized.not()) {
                throw IllegalArgumentException("Please call Builder.setScene with Scene")
            }
            if (::config.isInitialized.not()) {
                config = Config()
            }
            return WorldNode(
                fragmentActivity,
                config,
                scene,
                onReferencingLocationChangedListener,
                onCompassRotationChangedListener
            )
        }

    }

    data class Config(
        val northRotated: Boolean = true,
        val maxRenderingDistance: Float = 1000f,
        val updateIntervalInMeters: Float = 500f,
        val locationAccuracyThreshold: Float = 100f,
        val receiveLocationUpdates: Boolean = true,
        val updateOnMoreAccurateLocation: Boolean = true
    )

    interface OnReferencingLocationChangedListener {
        fun locationChanged(location: Location)
    }

    interface OnCompassRotationChangedListener {
        fun rotationChanged(rotation: Float, accuracy: Int)
    }

    init {
        init()
    }
}