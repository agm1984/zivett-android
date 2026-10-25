package com.zivett.app.features.shared

import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.models.Job
import com.zivett.app.core.models.JobArea
import com.zivett.app.core.models.JobLocation
import com.zivett.app.core.network.ApiRequest
import com.zivett.app.core.realtime.RealtimeEvents
import com.zivett.app.design.ZColors
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZType
import com.zivett.app.features.customer.home.CustomerHomeLogic
import kotlinx.coroutines.delay
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.time.Instant

/// A round pin drawable (charcoal for the home, gold for the pro).
private fun dot(color: Int, stale: Boolean = false): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(color)
    setStroke(6, AndroidColor.WHITE)
    setSize(42, 42)
    alpha = if (stale) 150 else 255
}

private fun MapView.standard() {
    setTileSource(TileSourceFactory.MAPNIK)
    setMultiTouchControls(true)
    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
    isTilesScaledToDpi = true
}

/// Compose's view host doesn't clip children, and osmdroid paints its
/// tile grid past its own bounds — a clipping frame keeps the map inside
/// the box the layout gave it.
private fun clippedHost(map: MapView): android.widget.FrameLayout = android.widget.FrameLayout(map.context).apply {
    clipChildren = true
    clipToPadding = true
    addView(map, android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT))
}

/// The en-route map: the job's pin, the pro's breadcrumb trail, and the
/// latest reported point (`GET /api/customer/jobs/{job}/location`),
/// live over the job's location channel with a 30s poll fallback. The
/// web uses Leaflet over OpenStreetMap; osmdroid draws the same tiles.
@Composable
fun JobTrackingMap(job: Job, area: JobArea = JobArea.customer, locationRequest: ((Int) -> ApiRequest<JobLocation>)? = null, modifier: Modifier = Modifier) {
    val environment = LocalAppEnvironment.current
    var location by remember(job.id) { mutableStateOf<JobLocation?>(null) }
    var framed by remember(job.id) { mutableStateOf(false) }
    val mapView = remember { mutableStateOf<MapView?>(null) }

    /// A report older than 3 minutes reads as stale — the marker dims
    /// rather than pretending the dot is live (JobTrackingMap.vue's rule).
    val stale = location?.updatedAt?.let { Instant.now().epochSecond - it.epochSecond >= 180 } ?: true

    suspend fun refresh() {
        location = runCatching { environment.client.send((locationRequest ?: area.location)(job.id)) }.getOrNull()
    }

    LaunchedEffect(job.id) {
        // The trail so far, then live points; the poll is the fallback
        // while the socket is down.
        refresh()
        while (true) {
            delay(30_000)
            if (!environment.realtime.connected) refresh()
        }
    }
    DisposableEffect(job.id) {
        val live = environment.realtime.subscribe(RealtimeEvents.jobLocationChannel(job.id)) { event, payload ->
            if (event != RealtimeEvents.locationUpdated) return@subscribe
            val point = RealtimeEvents.point(payload) ?: return@subscribe
            val current = location ?: JobLocation()
            location = current.copy(points = current.points + point, updatedAt = point.at ?: Instant.now())
        }
        onDispose { live.cancel() }
    }

    Box(modifier = modifier.clipToBounds()) {
        AndroidView(
            factory = { context -> clippedHost(MapView(context).apply { standard() }.also { mapView.value = it }) },
            modifier = Modifier.fillMaxSize(),
            update = { host ->
                val map = host.getChildAt(0) as MapView
                map.overlays.clear()
                val coordinates = mutableListOf<GeoPoint>()
                if (job.lat != null && job.lng != null) {
                    val home = GeoPoint(job.lat, job.lng)
                    coordinates += home
                    map.overlays += Marker(map).apply { position = home; title = "Home"; icon = dot(AndroidColor.parseColor("#151B23")); setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER) }
                }
                val points = location?.points ?: emptyList()
                if (points.size > 1) {
                    map.overlays += Polyline(map).apply {
                        setPoints(points.map { GeoPoint(it.lat, it.lng) })
                        outlinePaint.color = AndroidColor.argb(140, 0xD4, 0xAF, 0x37)
                        outlinePaint.strokeWidth = 9f
                        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                    }
                }
                location?.latest?.let { pro ->
                    val point = GeoPoint(pro.lat, pro.lng)
                    coordinates += point
                    map.overlays += Marker(map).apply { position = point; title = job.company?.name ?: "Pro"; icon = dot(AndroidColor.parseColor("#D4AF37"), stale); setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER) }
                }
                // Frame once when both ends are known — re-fitting on every
                // poll yanks the camera out of the viewer's hands.
                if (!framed && coordinates.isNotEmpty()) {
                    if (coordinates.size == 1) {
                        map.controller.setZoom(15.0)
                        map.controller.setCenter(coordinates[0])
                    } else {
                        val box = BoundingBox.fromGeoPointsSafe(coordinates)
                        map.post { map.zoomToBoundingBox(box.increaseByScale(1.6f), false) }
                        framed = true
                    }
                }
                map.invalidate()
            },
        )
        // The freshness pill.
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(ZSpacing.sm).clip(CircleShape).background(ZColors.fixedNavyDeep.copy(alpha = 0.92f)).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (location?.latest == null) ZColors.fixedGold.copy(alpha = 0.6f) else ZColors.fixedGold))
            Text(" " + CustomerHomeLogic.freshness(location?.updatedAt), style = ZType.label.copy(fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp)), color = Color.White)
        }
    }
}

/// The pin editor behind saved addresses and properties: the typed
/// address is for humans, the pin for navigation. Tap the map to move it.
@Composable
fun PinPickerMap(lat: Double, lng: Double, label: String, modifier: Modifier = Modifier, onMove: (Double, Double) -> Unit) {
    AndroidView(
        factory = { context ->
            clippedHost(MapView(context).apply {
                standard()
                controller.setZoom(17.0)
                controller.setCenter(GeoPoint(lat, lng))
                overlays += MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint): Boolean { onMove(p.latitude, p.longitude); return true }
                    override fun longPressHelper(p: GeoPoint): Boolean = false
                })
            })
        },
        modifier = modifier.clipToBounds(),
        update = { host ->
            val map = host.getChildAt(0) as MapView
            map.overlays.removeAll { it is Marker }
            map.overlays += Marker(map).apply { position = GeoPoint(lat, lng); title = label.ifEmpty { "Pin" }; icon = dot(AndroidColor.parseColor("#151B23")); setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER) }
            map.invalidate()
        },
    )
}

/// A numbered stop pin (charcoal disc, white number) — the day map's
/// route order.
private fun numberedPin(context: android.content.Context, number: Int, warn: Boolean): android.graphics.drawable.BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val size = (28 * density).toInt()
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.parseColor(if (warn) "#B45309" else "#151B23") }
    val ring = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE; style = android.graphics.Paint.Style.STROKE; strokeWidth = 2.5f * density }
    val radius = size / 2f - 2 * density
    canvas.drawCircle(size / 2f, size / 2f, radius, fill)
    canvas.drawCircle(size / 2f, size / 2f, radius, ring)
    val text = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE; textSize = 13 * density; textAlign = android.graphics.Paint.Align.CENTER; isFakeBoldText = true
    }
    canvas.drawText(number.toString(), size / 2f, size / 2f - (text.descent() + text.ascent()) / 2, text)
    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}

/// One located visit on the day map.
data class RouteStop(val number: Int, val lat: Double, val lng: Double, val title: String, val warn: Boolean = false)

/// The calendar day's route: the company's base (gold) and each located
/// visit as a numbered pin in window order, joined by straight lines —
/// no routing API, the same osmdroid tiles as live tracking.
@Composable
fun DayRouteMap(base: Pair<Double, Double>?, baseLabel: String?, stops: List<RouteStop>, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clipToBounds()) {
        AndroidView(
            factory = { context -> clippedHost(MapView(context).apply { standard() }) },
            modifier = Modifier.fillMaxSize(),
            update = { host ->
                val map = host.getChildAt(0) as MapView
                map.overlays.clear()
                val points = mutableListOf<GeoPoint>()
                base?.let { (lat, lng) ->
                    val point = GeoPoint(lat, lng)
                    points += point
                    map.overlays += Marker(map).apply { position = point; title = baseLabel ?: "Base"; icon = dot(AndroidColor.parseColor("#D4AF37")); setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER) }
                }
                stops.forEach { stop -> points += GeoPoint(stop.lat, stop.lng) }
                if (points.size > 1) {
                    map.overlays += Polyline(map).apply {
                        setPoints(points)
                        outlinePaint.color = AndroidColor.argb(170, 0x15, 0x1B, 0x23)
                        outlinePaint.strokeWidth = 5f
                        outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(18f, 12f), 0f)
                    }
                }
                stops.forEach { stop ->
                    map.overlays += Marker(map).apply {
                        position = GeoPoint(stop.lat, stop.lng); title = stop.title
                        icon = numberedPin(map.context, stop.number, stop.warn)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }
                }
                when {
                    points.size == 1 -> { map.controller.setZoom(14.0); map.controller.setCenter(points[0]) }
                    points.size > 1 -> {
                        val box = BoundingBox.fromGeoPointsSafe(points)
                        map.post { map.zoomToBoundingBox(box.increaseByScale(1.5f), false) }
                    }
                }
                map.invalidate()
            },
        )
    }
}
