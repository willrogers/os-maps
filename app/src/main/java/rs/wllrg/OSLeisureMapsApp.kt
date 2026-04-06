package rs.wllrg

import android.app.Application
import org.osmdroid.config.Configuration
import java.io.File

class OSLeisureMapsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().apply {
            userAgentValue = "OSMaps/1.0 (Android)"
            osmdroidTileCache = File(cacheDir, "tiles")
            cacheMapTileCount = 256.toShort()
        }
    }
}
