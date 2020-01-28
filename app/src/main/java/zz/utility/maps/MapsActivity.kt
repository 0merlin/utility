package zz.utility.maps

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.graphhopper.GHRequest
import com.graphhopper.GraphHopper
import com.graphhopper.PathWrapper
import com.graphhopper.util.Parameters
import com.graphhopper.util.StopWatch
import kotlinx.android.synthetic.main.activity_maps.*
import org.oscim.android.canvas.AndroidGraphics.drawableToBitmap
import org.oscim.backend.CanvasAdapter
import org.oscim.core.GeoPoint
import org.oscim.core.MapPosition
import org.oscim.event.Gesture
import org.oscim.event.GestureListener
import org.oscim.event.MotionEvent
import org.oscim.layers.Layer
import org.oscim.layers.LocationLayer
import org.oscim.layers.marker.ItemizedLayer
import org.oscim.layers.marker.MarkerItem
import org.oscim.layers.marker.MarkerSymbol
import org.oscim.layers.tile.buildings.BuildingLayer
import org.oscim.layers.tile.vector.labeling.LabelLayer
import org.oscim.layers.vector.PathLayer
import org.oscim.layers.vector.VectorLayer
import org.oscim.layers.vector.geometries.PolygonDrawable
import org.oscim.layers.vector.geometries.Style
import org.oscim.renderer.GLViewport
import org.oscim.scalebar.DefaultMapScaleBar
import org.oscim.scalebar.MapScaleBar
import org.oscim.scalebar.MapScaleBarLayer
import org.oscim.theme.VtmThemes
import org.oscim.tiling.source.mapfile.MapFileTileSource
import zz.utility.HOME
import zz.utility.MAIN_CONFIG
import zz.utility.R
import zz.utility.helpers.*

data class LocationPoint(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val colour: String = "blue"
)

fun colourStyle(f: String): Style = Style.builder()
        .buffer(0.5)
        .fillColor(f)
        .fillAlpha(0.2F).build()

@SuppressLint("MissingPermission")
class MapsActivity : AppCompatActivity(), LocationListener, ItemizedLayer.OnItemGestureListener<MarkerItem> {

    private lateinit var mapScaleBar: MapScaleBar
    private lateinit var locationLayer: LocationLayer
    private lateinit var locationManager: LocationManager
    private val mapPosition = MapPosition()
    private var followMe = false
    private val locationsSaved = ArrayList<LocationPoint>()

    private var useCar = true
    private var rotateFollow = false

    private lateinit var lastLocation: Location

    private lateinit var pathLayer: PathLayer

    private var currentResponse: PathWrapper? = null

    private var hidden = true
    private var daylight = true

    @SuppressLint("MissingPermission")
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_maps)
        progress.see()

        // Tile source
        val tileSource = MapFileTileSource()
        if (tileSource.setMapFile("$HOME/area.map")) {
            val tileLayer = mapView.map().setBaseMap(tileSource)
            mapView.map().layers().add(BuildingLayer(mapView.map(), tileLayer))
            mapView.map().layers().add(LabelLayer(mapView.map(), tileLayer))
            mapView.map().setTheme(VtmThemes.OSMARENDER)
//            mapView.map().setTheme(VtmThemes.NEWTRON)

            // Scale bar
            mapScaleBar = DefaultMapScaleBar(mapView.map())
            val mapScaleBarLayer = MapScaleBarLayer(mapView.map(), mapScaleBar)
            mapScaleBarLayer.renderer.setPosition(GLViewport.Position.BOTTOM_LEFT)
            mapScaleBarLayer.renderer.setOffset(5 * CanvasAdapter.getScale(), 0f)
            mapView.map().layers().add(mapScaleBarLayer)
        }

        fab_menu.setOnClickListener {
            val state = if (hidden) {
                fab_menu.setImageDrawable(getDrawable(R.drawable.ic_clear))
                View.VISIBLE
            } else {
                fab_menu.setImageDrawable(getDrawable(R.drawable.ic_menu))
                View.GONE
            }

            hidden = !hidden

            center.visibility = state
            vehicle.visibility = state
            share.visibility = state
            navigate.visibility = state
            center_on_me.visibility = state
            fab_theme.visibility = state
        }

        fab_theme.setOnClickListener {
            mapView.map().setTheme(if (daylight) VtmThemes.NEWTRON else VtmThemes.OSMARENDER)
            daylight = !daylight
        }

        navigate.setOnClickListener {
            val titles = Array(locationsSaved.size) { locationsSaved[it].name }
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Select location")
                    .setItems(titles) { _, which ->
                        calcPath(lastLocation.latitude, lastLocation.longitude, locationsSaved[which].latitude, locationsSaved[which].longitude)
                    }
            builder.show()
        }

        center_on_me.setOnClickListener {
            followMe = !followMe
            if (!rotateFollow)
                rotateFollow = true
            mapView.map().viewport().setMapViewCenter(0f, 0.5f)
            onLocationChanged(lastLocation)
            val mp = mapView.map().mapPosition
            mapView.map().setMapPosition(mp.latitude, mp.longitude, (1 shl 18).toDouble())
        }

        share.setOnClickListener {
            val i = Intent(Intent.ACTION_SEND)

            i.type = "text/plain"
            i.putExtra(Intent.EXTRA_SUBJECT, "Shared Location")
            i.putExtra(Intent.EXTRA_TEXT, "http://maps.google.com/maps?q=loc:%.10f,%.10f".format(lastLocation.latitude, lastLocation.longitude))

            try {
                startActivity(Intent.createChooser(i, "Share Location"))
            } catch (ex: android.content.ActivityNotFoundException) {
                toast("There is no activity to share location to.")
            }
        }

        vehicle.setOnClickListener {
            useCar = !useCar
            vehicle.setImageDrawable(getDrawable(if (useCar) R.drawable.ic_map_car else R.drawable.ic_map_walk))
        }

        center.setOnClickListener {
            mapView.map().viewport().setRotation(0.0)
            mapView.map().viewport().setMapViewCenter(0f, 0f)
            rotateFollow = false
        }

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        locationLayer = LocationLayer(mapView.map())
        locationLayer.locationRenderer.setShader("location_1_reverse")
        locationLayer.isEnabled = false
        mapView.map().layers().add(locationLayer)

        lastLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
                        ?: Location("tmp").apply {
                    latitude = -32.0
                    longitude = 18.0
                }

        mapView.map().setMapPosition(lastLocation.latitude, lastLocation.longitude, (1 shl 12).toDouble())

        onLocationChanged(lastLocation)

        val locations = MAIN_CONFIG.a("locations").mapObject { LocationPoint(s("name"), d("latitude"), d("longitude"), s("colour", "blue")) }
        locationsSaved.addAll(locations)

        arrayOf(
                R.drawable.ic_place_green,
                R.drawable.ic_place_blue,
                R.drawable.ic_place_pink,
                R.drawable.ic_place_red,
                R.drawable.ic_place_black,
                R.drawable.ic_place_light_blue,
                R.drawable.ic_place_purple
        ).forEachIndexed { index, image ->
            ItemizedLayer(
                    mapView.map(),
                    ArrayList<MarkerItem>(),
                    MarkerSymbol(drawableToBitmap(getDrawable(image)), MarkerSymbol.HotspotPlace.BOTTOM_CENTER, true),
                    this@MapsActivity
            ).apply {
                mapView.map().layers().add(this)
                addItems(locations.filter {
                    when (index) {
                        0 -> it.colour == "green"
                        1 -> it.colour == "blue"
                        2 -> it.colour == "pink"
                        3 -> it.colour == "red"
                        4 -> it.colour == "black"
                        5 -> it.colour == "light_blue"
                        6 -> it.colour == "purple"
                        else -> false
                    }
                }.map {
                    MarkerItem(it.name, it.name, GeoPoint(it.latitude, it.longitude))
                })
            }
        }
        setupGraphhopper()


        val style = Style.builder()
                .fixed(true)
                .generalization(Style.GENERALIZATION_SMALL)
                .strokeColor(ContextCompat.getColor(this, R.color.colorAccent))
                .strokeWidth(4 * resources.displayMetrics.density)
                .build()
        pathLayer = PathLayer(mapView.map(), style)
        mapView.map().layers().add(pathLayer)

        mapView.map().layers().add(object : Layer(mapView.map()), GestureListener {
            override fun onGesture(g: Gesture?, e: MotionEvent?): Boolean {
                g ?: return false
                e ?: return false
                return when (g) {
                    is Gesture.Tap -> consume {
                        val p = mMap.viewport().fromScreenPoint(e.x, e.y)
                        toast("You clicked on ${p.latitude}, ${p.longitude}")
                    }
                    is Gesture.LongPress -> consume {
                        val p = mMap.viewport().fromScreenPoint(e.x, e.y)
                        toast("Navigating to ${p.latitude}, ${p.longitude}", Toast.LENGTH_SHORT)
                        calcPath(lastLocation.latitude, lastLocation.longitude, p.latitude, p.longitude)
                    }
                    else -> false
                }
            }

        })
        val vectorLayer = VectorLayer(mapView.map())

        MAIN_CONFIG.a("layers").mapObject {
            vectorLayer.add(PolygonDrawable(ArrayList<GeoPoint>().apply {
                a("points").mapObject {
                    add(GeoPoint(d("latitude"), d("longitude")))
                }
            }, colourStyle(s("colour"))))
        }

        vectorLayer.update()
        mapView.map().layers().add(vectorLayer)

        val intent = intent ?: return
        val data = intent.data ?: return
        Thread {
            Thread.sleep(2000)
            runOnUiThread {
                val path = data.pathSegments
                when (path.size) {
                    0 -> {
                    }
                    1 -> {
                        val point = locationsSaved.find { it.name.toLowerCase().contains(path[0].toLowerCase()) }
                                ?: return@runOnUiThread
                        calcPath(lastLocation.latitude, lastLocation.longitude, point.latitude, point.longitude)
                        centerOn(point.latitude, point.longitude)
                    }
                    2 -> {
                        try {
                            val latitude = path[0].toDouble()
                            val longitude = path[1].toDouble()
                            centerOn(latitude, longitude)
                            calcPath(lastLocation.latitude, lastLocation.longitude, latitude, longitude)
                        } catch (e: Exception) {
                            toast("Couldn't decode: ${data.path}")
                        }
                    }
                    else -> toast("Unable to understand given app link")
                }
            }
        }.start()
    }

    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()
        mapView.onResume()
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0F, this)
    }

    override fun onPause() {
        locationManager.removeUpdates(this)
        mapView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        mapScaleBar.destroy()
        mapView.onDestroy()
        super.onDestroy()
    }

    @SuppressLint("SetTextI18n")
    override fun onLocationChanged(location: Location) {
        lastLocation = location
        locationLayer.isEnabled = true
        locationLayer.setPosition(location.latitude, location.longitude, location.accuracy)


        if (location.hasSpeed()) {
            if (location.speed < 1) map_speed.unsee()
            else map_speed.see()
            map_speed.text = "%.1f".format(location.speed * 3.6)
        }

        if (location.hasAltitude())
            map_altitude.text = "%.0f m".format(location.altitude)

        if (location.hasBearing()) {
            map_bearing.see()
            map_bearing.text = "%s".format(location.bearing.bearingToCompass())
        }

        // Follow location
        if (followMe) centerOn(location.latitude, location.longitude)
        if (rotateFollow && location.speed > 1.0) {
            mapView.map().viewport().setRotation(location.bearing.toDouble() * -1.0)
            mapView.map().viewport().setTilt(60F)
        }
        mapView.map().updateMap(true)
        gps_data.text = "%.8f, %.8f\n%.0f m [%s]".format(location.latitude, location.longitude, location.accuracy, location.provider)
    }

    private fun centerOn(latitude: Double, longitude: Double) {
        mapView.map().getMapPosition(mapPosition)
        mapPosition.setPosition(latitude, longitude)
//        mapPosition.setScale(120000.0)
        mapView.map().mapPosition = mapPosition
    }

    override fun onProviderDisabled(provider: String) {}

    override fun onProviderEnabled(provider: String) {}

    override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}

    override fun onItemSingleTapUp(index: Int, item: MarkerItem?): Boolean {
        item ?: return true
        toast("Is here: " + item.getTitle())
        return true
    }

    override fun onItemLongPress(index: Int, item: MarkerItem?): Boolean {
        item ?: return true
        toast("Navigating to:" + item.getTitle())
        calcPath(lastLocation.latitude, lastLocation.longitude, item.geoPoint.latitude, item.geoPoint.longitude)

        return true
    }


    private lateinit var hopper: GraphHopper


    @SuppressLint("StaticFieldLeak")
    private fun setupGraphhopper() {
        Thread(Runnable {
            {
                val tmpHopp = GraphHopper().forMobile()
                tmpHopp.load("$HOME/area")
                log("found graph " + tmpHopp.graphHopperStorage.toString() + ", nodes:" + tmpHopp.graphHopperStorage.nodes)
                hopper = tmpHopp
            }.orPrint()
            runOnUiThread { progress.unsee() }
        }).start()
    }

    @SuppressLint("StaticFieldLeak")
    private fun calcPath(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double) {
        progress.see()
        object : AsyncTask<Void, Void, PathWrapper>() {
            var time: Float = 0.toFloat()

            override fun doInBackground(vararg v: Void): PathWrapper? {
                val sw = StopWatch().start()
                val req = GHRequest(fromLat, fromLon, toLat, toLon).setAlgorithm(Parameters.Algorithms.DIJKSTRA_BI)
                req.vehicle = if (useCar) "car" else "foot"
                val resp = hopper.route(req)
                time = sw.stop().seconds
                return { resp.best }.or { null }
            }

            @SuppressLint("SetTextI18n")
            override fun onPostExecute(resp: PathWrapper?) {
                progress.unsee()
                if (resp == null) {
                    toast("Unable to create route")
                    return
                }
                if (!resp.hasErrors()) {
                    currentResponse = resp
                    val t = resp.time / 1000
                    path.text = "%d turns, %.1f km (%02d:%02d)".format(
                            resp.instructions.size,
                            resp.distance / 1000.0,
                            t / 60,
                            t % 60)
                    path.see()
                    toast("Took ${(time * 1000).toInt()} ms to compute")


                    val geoPoints = ArrayList<GeoPoint>()
                    val pointList = resp.points

                    for (i in 0 until pointList.size)
                        geoPoints.add(GeoPoint(pointList.getLatitude(i), pointList.getLongitude(i)))
                    pathLayer.setPoints(geoPoints)

                    mapView.map().updateMap(true)

                } else {
                    logUser("Error:" + resp.errors)
                }
            }
        }.execute()
    }

    private fun log(str: String) {
        Log.i("GH", str)
    }

    private fun logUser(str: String) {
        log(str)
        toast(str)
    }

}
