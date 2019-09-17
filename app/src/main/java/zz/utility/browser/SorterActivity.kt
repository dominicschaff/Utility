package zz.utility.browser

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import kotlinx.android.synthetic.main.activity_sorter.*
import zz.utility.R
import zz.utility.helpers.alert
import zz.utility.helpers.formatSize
import zz.utility.helpers.toast
import zz.utility.isImage
import java.io.File
import java.util.*

class SorterActivity : AppCompatActivity() {

    private val paths = ArrayList<File>()
    private val folders = ArrayList<File>()
    private var current = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sorter)

        val path = File(intent.extras?.getString(PATH) ?: "/1")
        if (!path.exists()) {
            finish()
            return
        }

        val allFiles = (if (path.isFile) path.parentFile else path).listFiles()

        paths += allFiles.filter { it.isImage() }
        folders += allFiles.filter { it.isDirectory }

        paths.sortFiles()

        folders.sortFiles()

        if (paths.isEmpty()) {
            alert("No images")
            return
        }

        fab_next.setOnClickListener { moveOn() }

        fab_delete.setOnClickListener {
            if (paths[current].moveToBin()) moveOn()
            else toast("File could not be moved")
        }

        folders.forEach { folder ->
            val t = layoutInflater.inflate(R.layout.sorter_folder, folder_list, false) as Button
            t.text = folder.name
            t.setOnClickListener {
                File(folder, paths[current].name).let { newFile ->
                    if (newFile.exists()) alert("There already exists the same file in directory ${newFile.parentFile.name}")
                    else {
                        paths[current].renameTo(newFile)
                        moveOn()
                    }
                }
            }
            folder_list.addView(t)
        }
        if (path.isFile)
            current = paths.indexOfFirst { it.name == path.name }

        if (current < 0) current = 0
        showImage()
    }


    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    private fun moveOn() {
        current = (current + 1) % paths.size
        showImage()
    }

    @SuppressLint("SetTextI18n")
    private fun showImage() {

        val path = paths[current]

        val infoText = if (path.exists()) {
            Glide.with(this)
                    .load(Uri.fromFile(path))
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .into(image as ImageView)
            path.length().formatSize()
        } else {
            image.setImageResource(R.drawable.ic_block)
            "no Image"
        }
        info.text = "${current + 1} / ${paths.size}\n$infoText"
    }
}
