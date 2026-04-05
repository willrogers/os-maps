package com.example.osleisure.tile

import com.example.osleisure.util.ApiKeys
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

/**
 * OSMDroid tile source backed by the OS Maps API (Leisure_3857 layer).
 *
 * Uses the ZXY endpoint in EPSG:3857 (Web Mercator), which is OSMDroid's native
 * projection — no coordinate transformation required.
 *
 * OS Maps API docs: https://developer.ordnancesurvey.co.uk/os-maps-api
 * Zoom levels 7–20 cover the full Leisure map product range.
 */
class OSTileSource : OnlineTileSourceBase(
    "OS Leisure Maps",
    7,   // minimum zoom
    20,  // maximum zoom
    256, // tile size in pixels
    ".png",
    arrayOf("https://api.os.uk")
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return "https://api.os.uk/maps/raster/v1/zxy/Leisure_3857/$zoom/$x/$y.png?key=${ApiKeys.OS_MAPS_KEY}"
    }
}
