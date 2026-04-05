package com.example.osleisure.tile

import android.util.Log
import com.example.osleisure.util.ApiKeys
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

/**
 * OSMDroid tile source backed by Bing Maps Virtual Earth, with the OS Leisure
 * map layer selected via `productSet=mmOS`.
 *
 * Bing Maps uses a "quadkey" tile addressing scheme instead of z/x/y:
 * each tile's position is encoded as a string of digits 0–3, one per zoom
 * level, by interleaving the bits of the x (column) and y (row) coordinates.
 * This mirrors the algorithm in Maverick's DataSource.encodeQuadTree().
 *
 * The `{stripe}` server number (0–3) is cycled per request to distribute
 * load across Bing's CDN edge nodes (ecn.t0…ecn.t3.tiles.virtualearth.net).
 */
class BingTileSource : OnlineTileSourceBase(
    "Bing OS Maps",
    12,  // minimum zoom — OS Explorer tiles only exist from zoom 12 upwards
    17,  // maximum zoom — tiles return blank above zoom 17
    256, // tile size in pixels
    ".png",
    arrayOf(
        "https://ecn.t0.tiles.virtualearth.net",
        "https://ecn.t1.tiles.virtualearth.net",
        "https://ecn.t2.tiles.virtualearth.net",
        "https://ecn.t3.tiles.virtualearth.net"
    )
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        val quadkey = encodeQuadkey(x, y, zoom)
        val stripe = (x + y) % 4
        val url = "https://ecn.t$stripe.tiles.virtualearth.net/tiles/r$quadkey" +
                "?g=3455&lbl=l1&productSet=mmOS&key=${ApiKeys.BING_MAPS_KEY}"
        Log.d("BingTileSource", "Requesting z=$zoom x=$x y=$y quadkey=$quadkey")
        return url
    }

    /**
     * Converts a tile's (x, y, zoom) into a Bing Maps quadkey string.
     * At each zoom level the digit encodes which quadrant (NW=0,NE=1,SW=2,SE=3)
     * the tile falls into, reading from the most-significant level down.
     */
    private fun encodeQuadkey(x: Int, y: Int, zoom: Int): String {
        val digits = CharArray(zoom)
        var tx = x
        var ty = y
        for (i in zoom - 1 downTo 0) {
            val digit = (tx and 1) or ((ty and 1) shl 1)
            digits[i] = '0' + digit
            tx = tx shr 1
            ty = ty shr 1
        }
        return String(digits)
    }
}
