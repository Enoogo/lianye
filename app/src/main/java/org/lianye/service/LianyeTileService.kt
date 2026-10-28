package org.lianye.service

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import org.lianye.R

/**
 * Quick Settings tile: starts an auto long-capture directly on tap (no bubble round-trip).
 * The panel is collapsed first so it is not stitched into the screenshot. The tile is
 * unavailable until the accessibility service is connected and auto mode is ready.
 */
class LianyeTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (!LianyeAccessibilityService.isConnected()) {
            updateTile()
            return
        }
        // Start the capture directly. The service collapses the Quick Settings panel itself
        // so the panel is not captured as part of the long screenshot.
        LianyeAccessibilityService.startCaptureFromTile()
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        tile.label = getString(R.string.qs_tile_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_main_header_shuttle)
        tile.state = when {
            !LianyeAccessibilityService.isConnected() -> Tile.STATE_UNAVAILABLE
            else -> Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }
}
