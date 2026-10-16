package org.diaabend.ui

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.activity.ComponentActivity
import org.diaabend.R
import org.diaabend.smb.DigikamPrefs
import org.diaabend.smb.Settings

/** What to pick from DigikamFS exports: the resolution profile and the lowest star rating. */
class SettingsActivity : ComponentActivity() {
    private lateinit var ui: ListScreen
    private lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ListScreen(this)
        settings = Settings(this)
        ui.title.setText(R.string.settings)
        render()
    }

    private fun render() {
        val profile = settings.profile.ifEmpty { getString(R.string.profile_off) }
        val stars = resources.getQuantityString(R.plurals.min_stars_value, settings.minStars, settings.minStars)
        ui.setRows(
            listOf(
                Row("profile", getString(R.string.profile), profile, R.drawable.ic_settings) { askProfile() },
                Row("stars", getString(R.string.min_stars), stars, R.drawable.ic_settings) { askStars() },
            )
        )
    }

    private fun askProfile() {
        val input = EditText(this).apply {
            setHint(R.string.profile_hint)
            setText(settings.profile)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.profile)
            .setMessage(R.string.profile_explanation)
            .setView(padded(input))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                settings.profile = input.text.toString()
                render()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        input.requestFocus()
    }

    private fun askStars() {
        val levels = (1..DigikamPrefs.MAX_STARS).toList()
        val labels = levels.map { resources.getQuantityString(R.plurals.min_stars_value, it, it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.min_stars)
            .setSingleChoiceItems(labels, levels.indexOf(settings.minStars)) { dialog, which ->
                settings.minStars = levels[which]
                dialog.dismiss()
                render()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
