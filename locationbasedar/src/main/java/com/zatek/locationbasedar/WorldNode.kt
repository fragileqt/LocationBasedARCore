package com.zatek.locationbasedar

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.hardware.GeomagneticField
import android.location.Location
import android.widget.Toast
import com.google.ar.core.Camera
import com.google.ar.sceneform.*
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.ux.ArFragment
import com.tbruyelle.rxpermissions2.RxPermissions
import io.nlopez.smartlocation.SmartLocation
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.Observables
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

    private val compassSensorManager by lazy {
        CompassSensorManager(context)
    }

    private val compositeDisposable = CompositeDisposable()

    private val requestPermissionSubject = BehaviorSubject.create<Unit>()
    private val permissionsGrantedSubject = BehaviorSubject.createDefault(false)

    private val locationUpdateSubject = BehaviorSubject.create<Location>()
    private val isActivatedSubject = BehaviorSubject.create<Boolean>()

    private val updateSubject = BehaviorSubject.createDefault(true)

    private val latestAccuracy = BehaviorSubject.createDefault(Float.MAX_VALUE)

    private val cameraSubject = BehaviorSubject.create<Camera>()

    private var childrenSize = 0

    var mostAccurateLocation: Location = Location("")
        private set

    private val mostAccurateLocationSubject =
        locationUpdateSubject.withLatestFrom(latestAccuracy, updateSubject).filter {
            (
                    (
                            config.updateOnMoreAccurateLocation &&
                                    it.first.accuracy <= config.locationAccuracyThreshold
                                    && (it.first.accuracy < it.second || it.first.distanceTo(
                                mostAccurateLocation
                            ) > config.updateIntervalInMeters)
                            )
                            || config.updateOnMoreAccurateLocation.not()
                    )
                    && it.third
        }.subscribeOn(Schedulers.io()).map {
            onReferencingLocationChangedListener?.locationChanged(it.first)
            mostAccurateLocation = it.first
            latestAccuracy.onNext(it.first.accuracy)
            it.first
        }.share()

    private val refreshSubject = BehaviorSubject.create<Long>()

    fun getEstimatedLocation(): Location =
        arScene.camera.worldPosition.toWorldCoordinates(mostAccurateLocation)

    fun setLocation(location: Location) {
        locationUpdateSubject.onNext(location)
    }

    fun stopUpdates() {
        updateSubject.onNext(false)
    }

    fun startUpdates() {
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
                requestPermissionSubject.withLatestFrom(permissionsGrantedSubject).filter{
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
                })
            )
        compositeDisposable.addAll(
            compassSensorManager.magneticNorthAzimuthSubject
                .withLatestFrom(
                    mostAccurateLocationSubject
                )
                .subscribeOn(Schedulers.io())
                .subscribe { (azimuth, location) ->
                    val geoField = GeomagneticField(
                        location.latitude.toFloat(),
                        location.longitude.toFloat(),
                        location.altitude.toFloat(),
                        System.currentTimeMillis()
                    )

                    val direction: Float = azimuth + geoField.declination

                    onCompassRotationChangedListener?.rotationChanged(direction)
                },
            Observables.combineLatest(
                isActivatedSubject,
                mostAccurateLocationSubject,
                updateSubject
            ).withLatestFrom(compassSensorManager.magneticNorthAzimuthSubject).filter { it.first.first && it.first.third }
                .subscribe {
                    val frame = (this.arScene.view as ArSceneView).arFrame
                    val camera = this.arScene.camera
                    val view = this.arScene.view
                    val floorY = frame?.let { frame ->
                        val points = frame.hitTest(
                            (view?.width?.toFloat() ?: 0f) / 2,
                            view?.height?.toFloat() ?: 0f
                        )
                        points.firstOrNull()?.hitPose?.ty()
                    } ?: 0f
                    this.worldPosition = Vector3(0f, floorY, 0f)
                    val location = it.first.second
                    if (config.northRotated)
                        this.worldRotation = camera?.northRotation(it.second, location)
                    (this as NodeParent).children.forEach {
                        if (it is LocationBasedNode)
                            it.update(location)
                    }
                },
            Observables.combineLatest(
                refreshSubject.distinctUntilChanged(),
                mostAccurateLocationSubject
            )
                .subscribeOn(Schedulers.io())
                .subscribe { (_, location) ->
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
        SmartLocation.with(context).location().lastLocation?.let {
            locationUpdateSubject.onNext(
                it.apply {
                    accuracy = config.locationAccuracyThreshold
                }
            )
        }
        SmartLocation.with(context).location()
            .start {
                locationUpdateSubject.onNext(it)
            }
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
        latestAccuracy.onNext(Float.MAX_VALUE)
        isActivatedSubject.onNext(false)
    }

    var storedLatestPosition = Vector3.zero()
    override fun onUpdate(frameTime: FrameTime?) {
        super.onUpdate(frameTime)
        frameTime?.let {
            (this.arScene.view as ArSceneView).arFrame?.camera?.let {
                cameraSubject.onNext(it)
            }
        }
        if (childrenSize != getRealSize() || worldPosition != storedLatestPosition) {
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
        fun rotationChanged(rotation: Float)
    }

    init {
        init()
    }
}