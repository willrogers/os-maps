package rs.wllrg

import android.app.Application
import org.osmdroid.config.Configuration
import java.io.File

class OSLeisureMapsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Set a descriptive User-Agent (required by OSMDroid / tile servers)
        Configuration.getInstance().userAgentValue = "OSLeisureMaps/1.0 (Android)"
        // Cache tiles in app's private cache directory
        Configuration.getInstance().osmdroidTileCache = File(cacheDir, "osmdroid")
    }
}
