package rs.wllrg

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import rs.wllrg.databinding.ActivityMainBinding
import rs.wllrg.location.CompassLocationProvider
import rs.wllrg.search.OSNamesService
import rs.wllrg.search.SearchAdapter
import rs.wllrg.search.SearchResult
import rs.wllrg.tile.BingTileSource
import rs.wllrg.tile.OSTileSource

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var map: MapView
    private lateinit var searchAdapter: SearchAdapter
    private val namesService = OSNamesService()
    private var searchJob: Job? = null
    private var locationOverlay: MyLocationNewOverlay? = null
    private var locationArrowBitmap: Bitmap? = null

    companion object {
        private const val TAG = "OSLeisureMaps"

        // Centre of Great Britain (roughly), zoom 7 shows the whole of England/Wales/Scotland
        private val UK_CENTRE = GeoPoint(51.7440, -1.2351) // OX4 3SA
        private const val DEFAULT_ZOOM = 14.0
        private const val RESULT_ZOOM = 14.0
        private const val LOCATION_PERMISSION_REQUEST = 1001

        // Debounce delay so we don't fire a request on every keystroke
        private const val SEARCH_DEBOUNCE_MS = 400L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, getPreferences(MODE_PRIVATE))

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupMap()
        setupSearch()
        setupSearchToggle()
        setupLocationFab()
    }

    override fun onBackPressed() {
        if (binding.searchCard.visibility == View.VISIBLE) {
            collapseSearch()
        } else {
            super.onBackPressed()
        }
    }

    // -------------------------------------------------------------------------
    // Map setup
    // -------------------------------------------------------------------------

    private fun setupMap() {
        map = binding.mapView
        Log.d(TAG, "setupMap: starting")

        // Try OS Maps API first; fall back to Bing if the key looks unset
        val tileSource =
            try {
                OSTileSource().also { validateKey(it.name()) }.also { Log.d(TAG, "Using OS tile source") }
            } catch (e: Exception) {
                Log.d(TAG, "OS tile source failed (${e.message}), falling back to Bing")
                BingTileSource()
            }
        Log.d(TAG, "Calling setTileSource: ${tileSource.name()}")
        map.setTileSource(tileSource)
        Log.d(TAG, "setTileSource done")

        map.setMultiTouchControls(true)
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
        map.minZoomLevel = tileSource.minimumZoomLevel.toDouble()
        map.maxZoomLevel = tileSource.maximumZoomLevel.toDouble()

        // Wait for the first layout pass so the MapView has a known size,
        // then set zoom/centre so OSMDroid can calculate which tiles to request.
        map.viewTreeObserver.addOnGlobalLayoutListener(
            object :
                android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    map.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    Log.d(TAG, "onGlobalLayout: map size ${map.width}x${map.height}, setting zoom/centre")
                    map.controller.setZoom(DEFAULT_ZOOM)
                    map.controller.setCenter(UK_CENTRE)
                }
            },
        )
    }

    /**
     * Throws if the API key is still the placeholder, so we fall back to Bing.
     */
    private fun validateKey(sourceName: String) {
        val key = BuildConfig.OS_MAPS_KEY
        if (key.isBlank() || key == "YOUR_OS_MAPS_API_KEY") {
            throw IllegalStateException("OS Maps API key not set for $sourceName")
        }
    }

    // -------------------------------------------------------------------------
    // Search expand / collapse
    // -------------------------------------------------------------------------

    private fun setupSearchToggle() {
        binding.btnOpenSearch.setOnClickListener { expandSearch() }
        binding.btnCloseSearch.setOnClickListener { collapseSearch() }
    }

    private fun expandSearch() {
        TransitionManager.beginDelayedTransition(binding.rootLayout, AutoTransition())
        binding.searchButton.visibility = View.GONE
        binding.searchCard.visibility = View.VISIBLE
        binding.searchView.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        binding.searchView.post {
            imm.showSoftInput(binding.searchView.findFocus(), InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun collapseSearch() {
        hideResults()
        binding.searchView.setQuery("", false)
        binding.searchView.clearFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchView.windowToken, 0)
        TransitionManager.beginDelayedTransition(binding.rootLayout, AutoTransition())
        binding.searchCard.visibility = View.GONE
        binding.searchButton.visibility = View.VISIBLE
    }

    // -------------------------------------------------------------------------
    // Search
    // -------------------------------------------------------------------------

    private fun setupSearch() {
        searchAdapter = SearchAdapter { result -> onSearchResultSelected(result) }

        binding.searchResults.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = searchAdapter
        }

        binding.searchView.setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    query?.let { runSearch(it) }
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    searchJob?.cancel()
                    if (newText.isNullOrBlank()) {
                        hideResults()
                        return true
                    }
                    searchJob =
                        lifecycleScope.launch {
                            delay(SEARCH_DEBOUNCE_MS)
                            runSearch(newText)
                        }
                    return true
                }
            },
        )
    }

    private fun runSearch(query: String) {
        lifecycleScope.launch {
            try {
                val results = namesService.search(query)
                if (results.isEmpty()) {
                    hideResults()
                    Toast.makeText(this@MainActivity, R.string.no_results, Toast.LENGTH_SHORT).show()
                } else {
                    searchAdapter.submitList(results)
                    binding.searchResults.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                hideResults()
                Toast.makeText(this@MainActivity, R.string.error_search, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun onSearchResultSelected(result: SearchResult) {
        collapseSearch()
        val point = GeoPoint(result.lat, result.lon)
        map.controller.animateTo(point, RESULT_ZOOM, 800L)
        map.post { map.invalidate() }
    }

    private fun hideResults() {
        searchAdapter.clear()
        binding.searchResults.visibility = View.GONE
    }

    // -------------------------------------------------------------------------
    // My Location FAB
    // -------------------------------------------------------------------------

    private fun setupLocationFab() {
        binding.fabMyLocation.setOnClickListener {
            if (hasLocationPermission()) {
                enableMyLocation()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    LOCATION_PERMISSION_REQUEST,
                )
            }
        }
    }

    private fun enableMyLocation() {
        val existing = locationOverlay
        if (existing != null && existing.isMyLocationEnabled) {
            existing.myLocation?.let {
                map.controller.animateTo(it)
                map.post { map.invalidate() }
            }
            return
        }

        setFabAcquiring(true)

        // CompassLocationProvider handles network + GPS + magnetometer internally.
        val provider = CompassLocationProvider(this)
        val arrow =
            locationArrowBitmap ?: drawableToBitmap(R.drawable.ic_location_arrow, 36)
                .also { locationArrowBitmap = it }

        val overlay =
            MyLocationNewOverlay(provider, map).apply {
                setDirectionArrow(arrow, arrow)
                enableMyLocation()
                runOnFirstFix {
                    runOnUiThread {
                        map.controller.animateTo(myLocation)
                        map.post { map.invalidate() }
                        setFabAcquiring(false)
                    }
                }
            }

        // Remove any stale overlay before adding the new one.
        locationOverlay?.let { map.overlays.remove(it) }
        map.overlays.add(overlay)
        locationOverlay = overlay
        map.invalidate()
    }

    /** Orange tint while waiting for a fix; navy when idle/fixed. */
    private fun setFabAcquiring(acquiring: Boolean) {
        val color = if (acquiring) "#E8612A" else "#1B4A7A"
        binding.fabMyLocation.backgroundTintList = ColorStateList.valueOf(Color.parseColor(color))
    }

    private fun drawableToBitmap(
        resId: Int,
        sizeDp: Int,
    ): Bitmap {
        val scale = resources.displayMetrics.density
        val px = (sizeDp * scale).toInt()
        val drawable = ResourcesCompat.getDrawable(resources, resId, theme)!!
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, px, px)
        drawable.draw(canvas)
        return bitmap
    }

    private fun hasLocationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            enableMyLocation()
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }
}
