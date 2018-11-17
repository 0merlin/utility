package zz.utility.launcher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.launcher_utility_fragment.view.*
import zz.utility.MainActivity
import zz.utility.R
import zz.utility.browser.FileBrowserActivity
import zz.utility.helpers.gotoNewWindow
import zz.utility.maps.MapsActivity
import zz.utility.utility.*

class UtilityFragment : androidx.fragment.app.Fragment() {
    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View {

        return inflater.inflate(R.layout.launcher_utility_fragment, null).apply {
            goto_scan.setOnClickListener { activity?.gotoNewWindow(BarcodeScanningActivity::class.java) }
            goto_osm_maps.setOnClickListener { activity?.gotoNewWindow(MapsActivity::class.java) }
            goto_files.setOnClickListener { activity?.gotoNewWindow(FileBrowserActivity::class.java) }
            goto_quote.setOnClickListener { activity?.gotoNewWindow(QuoteActivity::class.java) }
            goto_list.setOnClickListener { activity?.gotoNewWindow(ListActivity::class.java) }
            goto_gps.setOnClickListener { activity?.gotoNewWindow(GPSActivity::class.java) }
            goto_camera.setOnClickListener { activity?.gotoNewWindow(CameraScalesActivity::class.java) }
            goto_versions.setOnClickListener { activity?.gotoNewWindow(AndroidVersionsActivity::class.java) }
            goto_draw.setOnClickListener { activity?.gotoNewWindow(TouchScreenActivity::class.java) }
            goto_main.setOnClickListener { activity?.gotoNewWindow(MainActivity::class.java) }

            mainGrid.columnCount = if (this@UtilityFragment.resources.getBoolean(R.bool.is_landscape)) 4 else 2
        }
    }
}