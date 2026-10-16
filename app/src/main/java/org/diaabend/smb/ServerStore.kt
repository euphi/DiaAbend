package org.diaabend.smb

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Servers the user has connected to, most recently used first. Kept in app-private preferences. */
class ServerStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("servers", Context.MODE_PRIVATE)

    fun all(): List<Server> = try {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        List(array.length()) { array.getJSONObject(it) }.map {
            Server(
                host = it.getString("host"),
                port = it.optInt("port", Server.DEFAULT_PORT),
                name = it.optString("name", it.getString("host")),
                user = it.optString("user"),
                password = it.optString("password"),
                domain = it.optString("domain"),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun find(id: String): Server? = all().firstOrNull { it.id == id }

    fun save(server: Server) = write(listOf(server) + all().filter { it.id != server.id })

    fun remove(id: String) = write(all().filter { it.id != id })

    private fun write(servers: List<Server>) {
        val array = JSONArray()
        for (s in servers) {
            array.put(
                JSONObject()
                    .put("host", s.host).put("port", s.port).put("name", s.name)
                    .put("user", s.user).put("password", s.password).put("domain", s.domain)
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "servers"
    }
}
