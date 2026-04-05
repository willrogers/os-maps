package com.example.osleisure.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.IMyLocationConsumer

/**
 * Extends GpsMyLocationProvider to inject compass heading (from the rotation
 * vector sensor) into each location update. This makes the map arrow reflect
 * which way the phone is pointing rather than direction of travel.
 *
 * The sensor fires at UI rate (~16 Hz) and triggers a redraw each time, giving
 * smooth rotation even when stationary. A low-pass filter smooths jitter.
 */
class CompassLocationProvider(private val context: Context) :
    GpsMyLocationProvider(context), SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private var smoothedBearing = 0f
    private var consumer: IMyLocationConsumer? = null

    // -------------------------------------------------------------------------
    // Provider lifecycle
    // -------------------------------------------------------------------------

    override fun startLocationProvider(myLocationConsumer: IMyLocationConsumer): Boolean {
        consumer = myLocationConsumer
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor != null) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        addLocationSource(LocationManager.NETWORK_PROVIDER)
        return super.startLocationProvider(myLocationConsumer)
    }

    override fun stopLocationProvider() {
        sensorManager.unregisterListener(this)
        consumer = null
        super.stopLocationProvider()
    }

    // -------------------------------------------------------------------------
    // Inject compass bearing into GPS/network location updates
    // -------------------------------------------------------------------------

    override fun onLocationChanged(location: Location) {
        location.bearing = smoothedBearing
        super.onLocationChanged(location)
    }

    // -------------------------------------------------------------------------
    // Sensor events — update bearing and push a redraw immediately
    // -------------------------------------------------------------------------

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        // Remap axes to account for which way the screen is currently oriented.
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }
        when (rotation) {
            Surface.ROTATION_90  -> SensorManager.remapCoordinateSystem(
                rotationMatrix, SensorManager.AXIS_Z, SensorManager.AXIS_MINUS_X, remappedMatrix)
            Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(
                rotationMatrix, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Z, remappedMatrix)
            Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(
                rotationMatrix, SensorManager.AXIS_MINUS_Z, SensorManager.AXIS_X, remappedMatrix)
            else                 -> SensorManager.remapCoordinateSystem(
                rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, remappedMatrix)
        }

        SensorManager.getOrientation(remappedMatrix, orientation)
        val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
            .let { if (it < 0f) it + 360f else it }

        // Low-pass filter using shortest angular path to avoid 359° → 1° flipping.
        smoothedBearing += 0.15f * angularDiff(smoothedBearing, azimuth)
        smoothedBearing = (smoothedBearing + 360f) % 360f

        // Push the new bearing to the overlay without waiting for the next GPS fix.
        val last = lastKnownLocation ?: return
        val updated = Location(last).apply { bearing = smoothedBearing }
        consumer?.onLocationChanged(updated, this)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    // -------------------------------------------------------------------------

    private fun angularDiff(from: Float, to: Float): Float {
        var d = to - from
        while (d > 180f)  d -= 360f
        while (d < -180f) d += 360f
        return d
    }
}
