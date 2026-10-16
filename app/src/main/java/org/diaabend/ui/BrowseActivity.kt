package org.diaabend.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import jcifs.smb.SmbAuthException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.diaabend.R
import org.diaabend.smb.Entry
import org.diaabend.smb.DigikamFs
import org.diaabend.smb.Resolved
import org.diaabend.smb.Server
import org.diaabend.smb.ServerStore
import org.diaabend.smb.Settings
import org.diaabend.smb.SmbClient

/** Shows the shares of a server or the contents of one folder. A folder holding only photos opens as slideshow. */
class BrowseActivity : ComponentActivity() {
    private lateinit var ui: ListScreen
    private lateinit var store: ServerStore
    private lateinit var server: Server
    private lateinit var url: String
    private lateinit var path: String
    private lateinit var settings: Settings

    /** Folder the listed photos are in; differs from [url] where DigikamFS' star folder was skipped. */
    private lateinit var photosUrl: String

    private val isRoot get() = url == server.rootUrl

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ListScreen(this)
        store = ServerStore(this)
        settings = Settings(this)
        server = intent.server()
        url = intent.getStringExtra(EXTRA_URL) ?: server.rootUrl
        path = intent.getStringExtra(EXTRA_PATH) ?: server.name
        ui.title.text = path
        load()
    }

    override fun onRestart() {
        super.onRestart()
        // The user may have logged in further down the folder tree in the meantime
        store.find(server.id)?.let { server = it }
    }

    private fun load() {
        ui.setRows(emptyList())
        ui.setMessage(null)
        ui.setBusy(true)
        lifecycleScope.launch {
            val listing = try {
                withContext(Dispatchers.IO) {
                    val listing = SmbClient.list(server, url, PHOTO_EXTENSIONS)
                    DigikamFs.resolve(server, url, listing, settings.digikam) { SmbClient.list(server, it, PHOTO_EXTENSIONS) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ui.setBusy(false)
                if (e is SmbAuthException) askLogin() else showFailure(getString(R.string.error_connect, e.message))
                return@launch
            }
            ui.setBusy(false)
            show(listing)
        }
    }

    private fun show(resolved: Resolved) {
        val listing = resolved.listing
        photosUrl = resolved.photosUrl
        if (store.find(server.id) != server) store.save(server)

        if (!isRoot && listing.folders.isEmpty() && listing.photos.isNotEmpty()) {
            startSlideshow()
            finish()
            return
        }
        val rows = ArrayList<Row>()
        if (listing.photos.isNotEmpty()) {
            val count = listing.photos.size
            val subtitle = resources.getQuantityString(R.plurals.photo_count, count, count)
            rows += Row("slideshow", getString(R.string.start_slideshow), subtitle, R.drawable.ic_play) { startSlideshow() }
        }
        for (folder in listing.folders) {
            rows += Row("folder:${folder.name}", folder.name, null, R.drawable.ic_folder) { open(folder) }
        }
        if (isRoot && !server.hasCredentials) rows += loginRow()
        if (listing.folders.isEmpty() && listing.photos.isEmpty()) {
            ui.setMessage(
                when {
                    isRoot -> getString(R.string.no_shares)
                    resolved.ratingFiltered -> resources.getQuantityString(R.plurals.no_rated_photos, settings.minStars, settings.minStars)
                    else -> getString(R.string.empty_folder)
                }
            )
        }
        ui.setRows(rows)
    }

    private fun showFailure(message: String) {
        ui.setMessage(message)
        ui.setRows(listOf(Row("retry", getString(R.string.retry), null, R.drawable.ic_refresh) { load() }, loginRow()))
    }

    private fun loginRow() = Row("login", getString(R.string.login), null, R.drawable.ic_person) { askLogin() }

    private fun open(folder: Entry) =
        startActivity(intent(this, server).putExtra(EXTRA_URL, folder.url).putExtra(EXTRA_PATH, "$path  ›  ${folder.name}"))

    private fun startSlideshow() =
        startActivity(Intent(this, SlideshowActivity::class.java).putExtra(EXTRA_SERVER, server).putExtra(EXTRA_URL, photosUrl))

    private fun askLogin() {
        val user = EditText(this).apply {
            setHint(R.string.username)
            setText(if (server.domain.isEmpty()) server.user else "${server.domain}\\${server.user}")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine()
        }
        val password = EditText(this).apply {
            setHint(R.string.password)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine()
        }
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(user)
            addView(password)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.login_title, server.name))
            .setView(padded(fields))
            .setPositiveButton(R.string.login_ok) { _, _ ->
                val name = user.text.toString().trim()
                server = server.copy(
                    user = name.substringAfterLast('\\'),
                    domain = name.substringBeforeLast('\\', ""),
                    password = password.text.toString(),
                )
                load()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> showFailure(getString(R.string.login_required)) }
            .setOnCancelListener { showFailure(getString(R.string.login_required)) }
            .show()
        user.requestFocus()
    }

    companion object {
        const val EXTRA_SERVER = "server"
        const val EXTRA_URL = "url"
        private const val EXTRA_PATH = "path"

        fun intent(context: Context, server: Server): Intent =
            Intent(context, BrowseActivity::class.java).putExtra(EXTRA_SERVER, server)
    }
}

@Suppress("DEPRECATION")
fun Intent.server(): Server = getSerializableExtra(BrowseActivity.EXTRA_SERVER) as Server

/** Dialog content needs its own margins; AlertDialog.setView puts views edge to edge. */
fun padded(view: View): View = FrameLayout(view.context).apply {
    val margin = (24 * resources.displayMetrics.density).toInt()
    setPadding(margin, margin / 2, margin, 0)
    addView(view)
}
