package org.diaabend.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import org.diaabend.R
import org.diaabend.smb.Discovery
import org.diaabend.smb.MdnsDiscovery
import org.diaabend.smb.NaturalOrder
import org.diaabend.smb.Server
import org.diaabend.smb.ServerStore

/** Start screen: servers used before, servers found in the local network, and manual entry. */
class ServersActivity : ComponentActivity() {
    private lateinit var ui: ListScreen
    private lateinit var store: ServerStore

    /** Host address to name (null while unknown) of everything discovery has turned up. */
    private val found = HashMap<String, String?>()
    private val namedByMdns = HashSet<String>()
    private var scanJob: Job? = null
    private var mdns: MdnsDiscovery? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ListScreen(this)
        store = ServerStore(this)
        ui.title.setText(R.string.app_name)
    }

    override fun onStart() {
        super.onStart()
        scan()
    }

    override fun onStop() {
        super.onStop()
        stopScan()
    }

    private fun scan() {
        stopScan()
        render()
        ui.setBusy(true)
        mdns = MdnsDiscovery(this) { host, name ->
            runOnUiThread {
                found[host] = name
                namedByMdns += host
                render()
            }
        }.also { it.start() }
        scanJob = lifecycleScope.launch {
            Discovery.scan().flowOn(Dispatchers.IO).collect { (host, name) ->
                if (host !in namedByMdns) found[host] = name ?: found[host]
                render()
            }
            ui.setBusy(false)
        }
    }

    private fun stopScan() {
        scanJob?.cancel()
        mdns?.stop()
        mdns = null
        ui.setBusy(false)
    }

    private fun render() {
        val saved = store.all()
        val savedHosts = saved.filter { it.port == Server.DEFAULT_PORT }.map { it.host }.toSet()
        val discovered = found.filterKeys { it !in savedHosts }
            .map { (host, name) -> Server(host, name = name ?: host) }
            .sortedWith(compareBy(NaturalOrder) { it.name })

        val rows = ArrayList<Row>()
        for (server in saved) {
            val subtitle = if (server.hasCredentials) "${server.user}@${server.id}" else server.id
            rows += Row(
                "saved:${server.id}", server.name, subtitle.takeIf { it != server.name }, R.drawable.ic_server,
                onLongClick = { confirmRemove(server) },
            ) { open(server) }
        }
        for (server in discovered) {
            rows += Row("found:${server.id}", server.name, server.host.takeIf { it != server.name }, R.drawable.ic_server) {
                open(server)
            }
        }
        rows += Row("enter", getString(R.string.enter_address), null, R.drawable.ic_add) { askAddress() }
        rows += Row("rescan", getString(R.string.search_again), null, R.drawable.ic_refresh) { scan() }
        rows += Row("settings", getString(R.string.settings), null, R.drawable.ic_settings) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        ui.setRows(rows)
    }

    private fun open(server: Server) = startActivity(BrowseActivity.intent(this, server))

    private fun askAddress() {
        val input = EditText(this).apply {
            setHint(R.string.address_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.enter_address)
            .setView(padded(input))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val server = Server.parse(input.text.toString())
                if (server != null) open(server) else Toast.makeText(this, R.string.invalid_address, Toast.LENGTH_LONG).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        input.requestFocus()
    }

    private fun confirmRemove(server: Server) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.remove_server, server.name))
            .setPositiveButton(R.string.remove) { _, _ ->
                store.remove(server.id)
                render()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
