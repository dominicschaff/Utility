package zz.utility

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import kotlinx.android.synthetic.main.fragment_launcher_main.view.*
import zz.utility.helpers.a
import zz.utility.helpers.fileAsJsonObject
import zz.utility.helpers.o

class MainFragment : androidx.fragment.app.Fragment() {
    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_launcher_main, null)

        val pm = context!!.packageManager

        MAIN.fileAsJsonObject().o("launcher").a("fave").map {
            doLoad(view.grid, it.asString, pm)
        }

        return view

    }

    private fun doLoad(gl: GridLayout, s: String, pm: PackageManager) {
        val d = getActivityIcon(s) ?: return
        val im = layoutInflater.inflate(R.layout.launcher_icon_main, gl, false) as ImageView

        val b = getAppIcon(pm, s)

        if (b != null) im.setImageBitmap(b)
        else im.setImageDrawable(d.activityInfo.loadIcon(pm))

        im.setOnClickListener { context!!.startActivity(context!!.packageManager.getLaunchIntentForPackage(d.activityInfo.packageName.toString())) }
        gl.addView(im)
    }

    private fun getActivityIcon(packageName: String): ResolveInfo? {
        val pm = context!!.packageManager
        val intent = Intent()
        intent.`package` = packageName

        val p = pm.queryIntentActivities(intent, 0)

        return p.firstOrNull()
    }
}