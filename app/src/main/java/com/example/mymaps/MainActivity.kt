package com.example.mymaps

import android.Manifest
import android.annotation.SuppressLint
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.os.PersistableBundle
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.mymaps.databinding.ActivityMainBinding
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.Task
import org.json.JSONException
import org.json.JSONObject
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.config.Configuration.getInstance
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class MainActivity : AppCompatActivity() {
    private lateinit var binding:ActivityMainBinding
    private lateinit var fusedLocationClient:FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // open map
        getInstance().load(this,
            PreferenceManager.getDefaultSharedPreferences(this))
        binding.map.setTileSource(TileSourceFactory.MAPNIK)

        //setup location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationRequest = LocationRequest.Builder(10000)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(5000)
            .build()

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)

        val client: SettingsClient = LocationServices.getSettingsClient(this)
        val task: Task<LocationSettingsResponse> =
            client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            // GPS and other settings are fine
        }

        //setup location callback
        locationCallback = object: LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                super.onLocationResult(p0)
                val location = p0.lastLocation
                if (location != null) {
                    val latitude = location.latitude
                    val longitude = location.longitude
                    updateMapWithLocation(latitude,longitude)
                    Log.d("LocationCallback", "Lat: $latitude, Lng: $longitude")
                } else {
                    Log.e("LocationCallback", "Location is null.")
                }
            }
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    exception.startResolutionForResult(this, 1001)
                } catch (e: IntentSender.SendIntentException) {
                    e.printStackTrace()
                }
            }
        }


    }

    fun updateMapWithLocation(lat: Double, lng: Double) {
        val map = binding.map
        val startPoint = GeoPoint(lat, lng)
        val mapController = map.controller
        mapController.setZoom(18.0)
        mapController.setCenter(startPoint)

        val marker = Marker(binding.map)
        marker.position = startPoint
        marker.setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)
        marker.title = "You are here"
        map.overlays.clear()
        map.overlays.add(marker)
        map.invalidate()

    }

    fun updateMapWithRoute(startLat: Double, startLng: Double, destLat: Double,
                           destLng: Double) {

        val map = binding.map
        val startPoint = GeoPoint(startLat, startLng)
        val endPoint = GeoPoint(destLat, destLng)
        val mapController = map.controller
        mapController.setZoom(15.0)
        mapController.setCenter(startPoint)

        // Start marker
        val startMarker = org.osmdroid.views.overlay.Marker(map)
        startMarker.position = startPoint
        startMarker.setAnchor(
            org.osmdroid.views.overlay.Marker.ANCHOR_CENTER,
            org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM
        )
        startMarker.title = "Start"

        // Destination marker
        val destMarker = org.osmdroid.views.overlay.Marker(map)
        destMarker.position = endPoint
        destMarker.setAnchor(
            org.osmdroid.views.overlay.Marker.ANCHOR_CENTER,
            org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM
        )
        destMarker.title = "Destination"

        map.overlays.clear()
        map.overlays.add(startMarker)
        map.overlays.add(destMarker)

        // Request route from OSRM
        val url = "https://router.project-osrm.org/route/v1/driving/$startLng,$startLat;$destLng,$destLat?overview=full&geometries=geojson"

        // Volley request
        val q = Volley.newRequestQueue(this)
        val request = StringRequest(
            Request.Method.GET, url,
            {
                // respons sukses
                    response ->
                try {
                    val json = JSONObject(response)
                    val routes = json.getJSONArray("routes")

                    if (routes.length() > 0) {
                        // start baca titik-titik rute
                        val geometry = routes.getJSONObject(0)
                            .getJSONObject("geometry")
                            .getJSONArray("coordinates")

                        val polylinePoints = mutableListOf<GeoPoint>()
                        for (i in 0 until geometry.length()) {
                            val coord = geometry.getJSONArray(i)
                            val lon:Double = coord.getDouble(0)
                            val lat:Double = coord.getDouble(1)
                            polylinePoints.add(GeoPoint(lat, lon))
                        }

                        val roadOverlay = Polyline().apply {
                            setPoints(polylinePoints)
                            title = "Route"
                        }
                        map.overlays.add(roadOverlay)
                        map.invalidate()
                    }

                } catch (e: JSONException) {
                    e.printStackTrace()
                }

            },
            {
                // respons gagal
                Log.e("VolleyError", "Routing error: ${it.message}")
            }
        )

        q.add(request)

    }

        override fun onResume() {
        super.onResume()
        startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
    // open map



    fun startLocationUpdates(){
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions( this,
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ), LOCATION_PERMISSION_REQUEST_CODE
                )
                return
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            fusedLocationClient.requestLocationUpdates(locationRequest,locationCallback
                ,Looper.getMainLooper())
        }


    @SuppressLint("MissingPermission")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // Permissions granted
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)

            } else {
                Toast.makeText(this, "Location permission denied.", Toast.LENGTH_SHORT).show()
            }
        }

    }

}