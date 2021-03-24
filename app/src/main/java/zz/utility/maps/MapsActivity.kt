package zz.utility.maps

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.AsyncTask
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.gson.JsonObject
import com.graphhopper.GHRequest
import com.graphhopper.GraphHopper
import com.graphhopper.ResponsePath
import com.graphhopper.config.Profile
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
import org.oscim.layers.marker.MarkerInterface
import org.oscim.layers.marker.MarkerItem
import org.oscim.layers.marker.MarkerSymbol
import org.oscim.layers.tile.buildings.BuildingLayer
import org.oscim.layers.tile.vector.labeling.LabelLayer
import org.oscim.layers.vector.PathLayer
import org.oscim.layers.vector.VectorLayer
import org.oscim.layers.vector.geometries.*
import org.oscim.map.Viewport
import org.oscim.renderer.GLViewport
import org.oscim.scalebar.DefaultMapScaleBar
import org.oscim.scalebar.MapScaleBar
import org.oscim.scalebar.MapScaleBarLayer
import org.oscim.theme.VtmThemes
import org.oscim.tiling.source.mapfile.MapFileTileSource
import org.oscim.tiling.source.mapfile.MultiMapFileTileSource
import zz.utility.R
import zz.utility.configFile
import zz.utility.externalFile
import zz.utility.helpers.*
import zz.utility.homeDir
import zz.utility.lib.OpenLocationCode
import zz.utility.lib.SunriseSunset
import zz.utility.views.chooser
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

@SuppressLint("MissingPermission")
class MapsActivity : AppCompatActivity(), LocationListener {

    private lateinit var mapScaleBar: MapScaleBar
    private lateinit var locationLayer: LocationLayer
    private lateinit var locationManager: LocationManager
    private val mapPosition = MapPosition()
    private var followMe = false
    private val locationsSaved = ArrayList<LocationPoint>()

    private var useCar = true
    private var rotateFollow = false
    private var overlayDrawn = false

    private lateinit var lastLocation: Location

    private lateinit var pathLayer: PathLayer

    private var currentResponse: ResponsePath? = null

    private var hidden = true
    private var daylight = true
    private var record = false

    private lateinit var hopper: GraphHopper

    @SuppressLint("MissingPermission")
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_maps)
        progress.show()

        // Tile source
        val multiTileSource = MultiMapFileTileSource()

        val world = File(homeDir(), "world.map")
        if (world.exists() && world.isFile) {
            val worldTileSource = MapFileTileSource()
            worldTileSource.setMapFile(world.absolutePath)
            multiTileSource.add(worldTileSource, Viewport.MIN_ZOOM_LEVEL, 9)
        }

        homeDir().listFiles()?.forEach {
            if (it.isFile && it.name.endsWith(".map") && it.name != "world.map") {
                val tileSource = MapFileTileSource()
                tileSource.setMapFile(it.absolutePath)
                multiTileSource.add(tileSource)
            }
        }

        val tileLayer = mapView.map().setBaseMap(multiTileSource)
        mapView.map().layers().add(BuildingLayer(mapView.map(), tileLayer))
        mapView.map().layers().add(LabelLayer(mapView.map(), tileLayer))
        mapView.map().setTheme(VtmThemes.OSMARENDER)

        // Scale bar
        mapScaleBar = DefaultMapScaleBar(mapView.map())
        val mapScaleBarLayer = MapScaleBarLayer(mapView.map(), mapScaleBar)
        mapScaleBarLayer.renderer.setPosition(GLViewport.Position.BOTTOM_LEFT)
        mapScaleBarLayer.renderer.setOffset(5 * CanvasAdapter.getScale(), 0f)
        mapView.map().layers().add(mapScaleBarLayer)

        fab_menu.setOnClickListener {
            val state = if (hidden) {
                fab_menu.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_clear))
                View.VISIBLE
            } else {
                fab_menu.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_menu))
                View.GONE
            }

            hidden = !hidden

            center.visibility = state
            vehicle.visibility = state
            share.visibility = state
            navigate.visibility = state
            center_on_me.visibility = state
            fab_theme.visibility = state
            draw_overlay.visibility = state
            fab_save.visibility = state
            show_extra.visibility = state
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
            vehicle.setImageDrawable(ContextCompat.getDrawable(this, if (useCar) R.drawable.ic_map_car else R.drawable.ic_map_walk))
        }

        center.setOnClickListener {
            mapView.map().viewport().setRotation(0.0)
            mapView.map().viewport().setMapViewCenter(0f, 0f)
            rotateFollow = false
        }

        draw_overlay.setOnClickListener {
            drawMapOverlays()
        }
        fab_save.setOnClickListener {
            record = true
            fab_save.hide()
        }

        show_extra.setOnClickListener {
            if (gps_data_info.visibility == View.VISIBLE) {
                show_extra.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_gps))
                gps_data_info.hide()
            } else {
                show_extra.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_gps_off))
                gps_data_info.show()
            }
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

        setupRouting()


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
                        chooser("Select Action", resources.getStringArray(R.array.map_actions), callback = { action, _ ->
                            when (action) {
                                1 -> {
                                    val builder = AlertDialog.Builder(this@MapsActivity)
                                    builder.setTitle("New Directory Name")

                                    val input = EditText(this@MapsActivity)
                                    input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
                                    builder.setView(input)

                                    builder.setPositiveButton("OK") { _, _ ->
                                        JsonObject().apply {
                                            addProperty("unixtimestamp", now())
                                            addProperty("date", Date(now()).toDateFull())
                                            addProperty("name", input.text.toString())
                                            addProperty("latitude", p.latitude)
                                            addProperty("longitude", p.longitude)
                                        }.appendToFile(File(homeDir(), "saved_locations.json"))
                                    }
                                    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }

                                    builder.show()
                                }
                                2 -> {
                                    val i = Intent(Intent.ACTION_SEND)

                                    i.type = "text/plain"
                                    i.putExtra(Intent.EXTRA_SUBJECT, "Shared Location")
                                    i.putExtra(Intent.EXTRA_TEXT, "http://maps.google.com/maps?q=loc:%.10f,%.10f".format(p.latitude, p.longitude))

                                    try {
                                        startActivity(Intent.createChooser(i, "Share Location"))
                                    } catch (ex: android.content.ActivityNotFoundException) {
                                        toast("There is no activity to share location to.")
                                    }
                                }
                                else -> {
                                    toast("Navigating to ${p.latitude}, ${p.longitude}", Toast.LENGTH_SHORT)
                                    calcPath(lastLocation.latitude, lastLocation.longitude, p.latitude, p.longitude)
                                }
                            }
                        })
                    }
                    else -> false
                }
            }

        })

        val locations = configFile().a("locations").mapObject { LocationPoint(s("name"), d("latitude"), d("longitude"), s("colour", "blue")) }
        locationsSaved.addAll(locations)

        markerColours.forEach { (image, name) ->
            val list = ArrayList<MarkerItem>()
            list.addAll(locations.filter { it.colour == name }.map {
                MarkerItem(it.name, it.name, it.toGeoPoint())
            })

            val layer = ItemizedLayer(
                    mapView.map(),
                    list as List<MarkerInterface>,
                    MarkerSymbol(drawableToBitmap(ContextCompat.getDrawable(this, image)), MarkerSymbol.HotspotPlace.BOTTOM_CENTER, true),
                    object : ItemizedLayer.OnItemGestureListener<MarkerInterface> {

                        override fun onItemSingleTapUp(index: Int, item: MarkerInterface?): Boolean {
                            item ?: return true
                            toast("This is: " + list[index].title)
                            return true
                        }

                        override fun onItemLongPress(index: Int, item: MarkerInterface?): Boolean {
                            item ?: return true
                            toast("Navigating to:" + list[index].title)
                            calcPath(lastLocation.latitude, lastLocation.longitude, item.point.latitude, item.point.longitude)

                            return true
                        }
                    }
            )
            mapView.map().layers().add(layer)
        }
        val newLocations = File(homeDir(), "saved_locations.json")
        if (newLocations.exists()) {
            val list = ArrayList<MarkerItem>()

            list.addAll(newLocations.readLines().map { it.asJsonObject() }.map {
                MarkerItem(it.s("name"), it.s("name"), it.toGeoPoint())
            })
            val layer = ItemizedLayer(
                    mapView.map(),
                    list as List<MarkerInterface>,
                    MarkerSymbol(drawableToBitmap(ContextCompat.getDrawable(this, R.drawable.ic_place_cyan)), MarkerSymbol.HotspotPlace.BOTTOM_CENTER, true),
                    object : ItemizedLayer.OnItemGestureListener<MarkerInterface> {

                        override fun onItemSingleTapUp(index: Int, item: MarkerInterface?): Boolean {
                            item ?: return true
                            toast("This is: " + list[index].title)
                            return true
                        }

                        override fun onItemLongPress(index: Int, item: MarkerInterface?): Boolean {
                            item ?: return true
                            toast("Navigating to:" + list[index].title)
                            calcPath(lastLocation.latitude, lastLocation.longitude, item.point.latitude, item.point.longitude)

                            return true
                        }

                    }
            )
            mapView.map().layers().add(layer)
        }
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
        try {
            mapView.onDestroy()
        } catch (err:NullPointerException){

        }

        super.onDestroy()
    }

    @SuppressLint("SetTextI18n")
    override fun onLocationChanged(location: Location) {
        lastLocation = location
        locationLayer.isEnabled = true
        locationLayer.setPosition(location.latitude, location.longitude, location.accuracy)


        if (location.hasSpeed()) {
            if (location.speed < 1) map_speed.hide()
            else map_speed.show()
            map_speed.text = "%.1f".format(location.speed * 3.6)
        }

        if (location.hasAltitude())
            map_altitude.text = "%.0f m".format(location.altitude)

        if (location.hasBearing()) {
            map_bearing.show()
            map_bearing.text = "%s".format(location.bearing.bearingToCompass())
        }

        // Follow location
        if (followMe) centerOn(location.latitude, location.longitude)
        if (rotateFollow && location.speed > 1.0) {
            mapView.map().viewport().setRotation(location.bearing.toDouble() * -1.0)
            mapView.map().viewport().setTilt(60F)
        }
        mapView.map().updateMap(true)
        gps_data.text = "%.6f, %.6f : %.0f m [%s]".format(location.latitude, location.longitude, location.accuracy, location.provider)

        if (gps_data_info.visibility == View.VISIBLE) {
            if (location.hasAccuracy())
                gps_accuracy.text = "%.0f m".format(location.accuracy)

            if (location.hasSpeed()) {
                gps_speed_m.text = "%.1f".format(location.speed)
                gps_speed_km.text = "%.1f".format(location.speed * 3.6)
            }

            if (location.hasAltitude())
                gps_altitude.text = "%.0f m".format(location.altitude)

            if (location.hasBearing())
                gps_bearing.text = "%s %.0f°".format(location.bearing.bearingToCompass(), location.bearing)

            gps_lat_long.text = "%.5f %.5f".format(location.latitude, location.longitude)

            val ss = SunriseSunset(location.latitude, location.longitude, Date(location.time), 0.0)

            gps_code.text =  OpenLocationCode(location.latitude, location.longitude).code

            gps_time_data.text = "${Date(location.time).toDateDay()}\n${ss.sunrise?.toTimeShort()} -> ${ss.sunset?.toTimeShort()}"
        }
        if (record) {
            val ss = SunriseSunset(location.latitude, location.longitude, Date(location.time), 0.0)
            JsonObject().apply {
                addProperty("event_time", Date().toDateFull())
                addProperty("latitude", location.latitude)
                addProperty("longitude", location.longitude)
                addProperty("accuracy", location.accuracy)
                addProperty("speed", location.speed)

                addProperty("altitude", location.altitude)

                addProperty("bearing", location.bearing)
                addProperty("provider", location.provider)
                addProperty("bearingAccuracyDegrees", location.bearingAccuracyDegrees)
                addProperty("speedAccuracyMetersPerSecond", location.speedAccuracyMetersPerSecond)
                addProperty("verticalAccuracyMeters", location.verticalAccuracyMeters)
                addProperty("openLocationCode", OpenLocationCode(location.latitude, location.longitude).code)
                addProperty("time", Date(location.time).toDateDay())
                addProperty("sunrise", ss.sunrise?.toDateDay())
                addProperty("sunset", ss.sunset?.toDateDay())
            }.appendToFile(externalFile("utility/location.json"))
        }
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

    @SuppressLint("StaticFieldLeak")
    private fun setupRouting() {
        Thread {
            {
                val tmpHopp: GraphHopper = GraphHopper().apply {
                    forMobile()
                    graphHopperLocation = File(homeDir(), "area").absolutePath
                    setProfiles(
                            Profile("car").setVehicle("car").setWeighting("fastest"),
                            Profile("foot").setVehicle("foot").setWeighting("fastest")
                    )
                    importOrLoad()
                }

                log("found graph " + tmpHopp.graphHopperStorage.toString() + ", nodes:" + tmpHopp.graphHopperStorage.nodes)
                tmpHopp.profiles.forEach {
                    it.name.error()
                }
                hopper = tmpHopp
            }.orPrint()
            runOnUiThread { progress.hide() }
        }.start()
    }

    @SuppressLint("StaticFieldLeak")
    private fun calcPath(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double) {
        progress.show()
        object : AsyncTask<Void, Void, ResponsePath>() {
            var time: Float = 0.toFloat()

            override fun doInBackground(vararg v: Void): ResponsePath? {
                val sw = StopWatch().start()
                val resp = hopper.route(GHRequest(fromLat, fromLon, toLat, toLon).apply {
                    profile = if (useCar) "car" else "foot"
                    hints.putObject(Parameters.Routing.INSTRUCTIONS, true)
                })
                time = sw.stop().seconds
                if (resp.hasErrors()) {
                    runOnUiThread {
                        resp.errors.forEach {
                            it.message!!.error()
                            toast(it.message!!)
                        }
                    }
                    return null
                }
                return if (resp.all.isEmpty()) null else resp.best
            }

            @SuppressLint("SetTextI18n")
            override fun onPostExecute(resp: ResponsePath?) {
                progress.hide()
                if (resp == null) {
                    toast("Unable to create route")
                    return
                }
                if (!resp.hasErrors()) {
                    currentResponse = resp
                    val t = resp.time / 1000
//                    resp.ascend
                    path.text = ("${resp.instructions.size} turns | %.1f km | %02d:%02d\nTook %.0f ms to compute").format(
                            resp.distance / 1000.0,
                            t / 60,
                            t % 60,
                            time * 1000)
                    path.show()

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

    private fun drawMapOverlays() {
        if (overlayDrawn) return
        overlayDrawn = true
        val obj = File(homeDir(), "map.json").asJsonObject()

        val locations = obj.a("locations").mapObject { LocationPoint(s("name"), d("latitude"), d("longitude"), s("colour", "blue")) }
        markerColours.forEach { (image, name) ->
            val list = ArrayList<MarkerItem>()

            list.addAll(locations.filter { it.colour == name }.map {
                MarkerItem(it.name, it.name, it.toGeoPoint())
            })
            ItemizedLayer(
                    mapView.map(),
                    list as List<MarkerInterface>,
                    MarkerSymbol(drawableToBitmap(ContextCompat.getDrawable(this, image)), MarkerSymbol.HotspotPlace.BOTTOM_CENTER, true),
                    object : ItemizedLayer.OnItemGestureListener<MarkerInterface> {

                        override fun onItemSingleTapUp(index: Int, item: MarkerInterface?): Boolean {
                            item ?: return true
                            toast("This is: " + list[index].title)
                            return true
                        }

                        override fun onItemLongPress(index: Int, item: MarkerInterface?): Boolean {
                            item ?: return true
                            toast("Navigating to:" + list[index].title)
                            calcPath(lastLocation.latitude, lastLocation.longitude, item.point.latitude, item.point.longitude)

                            return true
                        }
                    }
            )
        }

        val vectorLayer = VectorLayer(mapView.map())

        obj.a("shapes").mapObject {
            "Type of object: ${s("type")}".error()
            when (s("type")) {
                "circle" -> {
                    vectorLayer.add(CircleDrawable(toGeoPoint(), d("size"), colourStyle(s("colour"))))
                }
                "rectangle" -> {
                    vectorLayer.add(RectangleDrawable(GeoPoint(d("top"), d("left")), GeoPoint(d("bottom"), d("right")), colourStyle(s("colour"))))
                }
                "line" -> {
                    vectorLayer.add(LineDrawable(ArrayList<GeoPoint>().apply {
                        a("points").mapObject {
                            add(toGeoPoint())
                        }
                    }, colourStyle(s("colour"))))
                }
                "layer" -> {
                    vectorLayer.add(PolygonDrawable(ArrayList<GeoPoint>().apply {
                        a("points").mapObject {
                            add(toGeoPoint())
                        }
                    }, colourStyle(s("colour"))))
                }
            }
        }
        vectorLayer.update()
        mapView.map().layers().add(vectorLayer)
    }

    private fun log(str: String) {
        Log.i("GH", str)
    }

    private fun logUser(str: String) {
        log(str)
        toast(str)
    }

}
