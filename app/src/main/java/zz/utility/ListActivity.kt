package zz.utility

import android.app.Activity
import android.content.DialogInterface
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_list.*
import zz.utility.helpers.*
import java.io.File


class ListActivity : Activity() {

    private val files = ArrayList<File>()

    private val items = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)

        val f = File("$HOME/lists").listFiles()
        files.addAll(f)
        val fileNames: Array<String> = files.map { it.nameWithoutExtension }.toTypedArray()
        createChooser("Select file to run", fileNames, DialogInterface.OnClickListener { _, which ->
            doAsync({
                return@doAsync { files[which].asJsonArray().map { it.asString } }.or { ArrayList<String>() }
            }, {
                items.addAll(it!!)
                doRefresh()
            })
        })
        swipe_to_refresh.setOnRefreshListener { doRefresh() }
    }

    private fun doRefresh() {
        swipe_to_refresh.isRefreshing = true
        text_item.text = items.random()
        swipe_to_refresh.isRefreshing = false
    }
}
