package org.diaabend.ui

import android.graphics.Bitmap
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.diaabend.R
import org.diaabend.smb.Entry
import org.diaabend.smb.Server
import org.diaabend.smb.SmbClient

/**
 * Full-screen slideshow of one folder.
 * Right/left: next/previous photo. OK, up, down or play/pause: pause and resume.
 */
class SlideshowActivity : ComponentActivity() {
    private lateinit var server: Server
    private lateinit var images: Array<ImageView>
    private lateinit var indicator: ImageView
    private lateinit var progress: View
    private lateinit var message: TextView

    private var photos: List<Entry> = emptyList()
    private var index = 0
    private var paused = false
    private var visibleImage = 0
    private var hasShownPhoto = false

    private var showJob: Job? = null
    private var timerJob: Job? = null

    /** Downloads in flight or finished, for the current photo and its two neighbours. */
    private val loads = HashMap<Int, Deferred<Bitmap?>>()

    // Two at a time, so holding down a key can't pile up dozens of downloads
    @OptIn(ExperimentalCoroutinesApi::class)
    private val loadDispatcher = Dispatchers.IO.limitedParallelism(2)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.slideshow)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        images = arrayOf(findViewById(R.id.image_a), findViewById(R.id.image_b))
        indicator = findViewById(R.id.indicator)
        progress = findViewById(R.id.progress)
        message = findViewById(R.id.message)

        server = intent.server()
        index = savedInstanceState?.getInt(STATE_INDEX) ?: 0
        paused = savedInstanceState?.getBoolean(STATE_PAUSED) ?: false

        val url = intent.getStringExtra(BrowseActivity.EXTRA_URL)!!
        lifecycleScope.launch {
            photos = try {
                SmbClient.lastListing?.takeIf { it.first == url }?.second?.photos
                    ?: withContext(Dispatchers.IO) { SmbClient.list(server, url, PHOTO_EXTENSIONS).photos }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            if (photos.isEmpty()) fail() else show(index, 1)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_INDEX, index)
        outState.putBoolean(STATE_PAUSED, paused)
    }

    override fun onStart() {
        super.onStart()
        if (hasShownPhoto && showJob?.isActive != true) scheduleNext()
    }

    override fun onStop() {
        super.onStop()
        timerJob?.cancel()
    }

    /** Shows photo [target]; photos that can't be loaded are skipped in direction [step]. */
    private fun show(target: Int, step: Int) {
        if (photos.isEmpty()) return
        timerJob?.cancel()
        showJob?.cancel()
        index = target.mod(photos.size)
        showJob = lifecycleScope.launch {
            var failures = 0
            var bitmap: Bitmap?
            while (true) {
                prune()
                bitmap = load(index).await()
                if (bitmap != null) break
                loads.remove(index)
                if (++failures >= photos.size) {
                    fail()
                    return@launch
                }
                index = (index + step).mod(photos.size)
            }
            display(bitmap!!)
            load((index + 1).mod(photos.size))
            scheduleNext()
        }
    }

    private fun scheduleNext() {
        timerJob?.cancel()
        if (paused) return
        timerJob = lifecycleScope.launch {
            delay(INTERVAL_MS)
            show(index + 1, 1)
        }
    }

    private fun load(i: Int): Deferred<Bitmap?> = loads.getOrPut(i) {
        val photo = photos[i]
        val metrics = resources.displayMetrics
        lifecycleScope.async(loadDispatcher) {
            try {
                SmbClient.read(server, photo.url) { isActive }
                    ?.let { PhotoDecoder.decode(it, metrics.widthPixels, metrics.heightPixels) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }

    /** Drops everything but the current photo and its neighbours. */
    private fun prune() {
        val keep = setOf(index, (index + 1).mod(photos.size), (index - 1).mod(photos.size))
        val iterator = loads.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in keep) {
                entry.value.cancel()
                iterator.remove()
            }
        }
    }

    private fun display(bitmap: Bitmap) {
        hasShownPhoto = true
        progress.visibility = View.GONE
        val outgoing = images[visibleImage]
        visibleImage = 1 - visibleImage
        val incoming = images[visibleImage]
        incoming.animate().cancel()
        outgoing.animate().cancel()
        incoming.setImageBitmap(bitmap)
        incoming.alpha = 0f
        incoming.animate().alpha(1f).setDuration(FADE_MS).withEndAction(null)
        outgoing.animate().alpha(0f).setDuration(FADE_MS).withEndAction { outgoing.setImageDrawable(null) }
    }

    private fun fail() {
        progress.visibility = View.GONE
        message.visibility = View.VISIBLE
    }

    private fun togglePause() {
        if (!hasShownPhoto) return
        paused = !paused
        // Shown briefly as feedback for the key press, then the photo is alone again
        indicator.setImageResource(if (paused) R.drawable.ic_pause else R.drawable.ic_play)
        indicator.animate().cancel()
        indicator.alpha = 1f
        indicator.animate().alpha(0f).setStartDelay(700).setDuration(FADE_MS)
        if (paused) timerJob?.cancel() else if (showJob?.isActive != true) scheduleNext()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD ->
                show(index + 1, 1)

            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND ->
                show(index - 1, -1)

            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE ->
                if (event.repeatCount == 0) togglePause()

            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    /** For phones and tablets: tap the left or right third to step, the middle to pause. */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val width = window.decorView.width
            when {
                event.x < width / 3f -> show(index - 1, -1)
                event.x > width * 2 / 3f -> show(index + 1, 1)
                else -> togglePause()
            }
        }
        return true
    }

    private companion object {
        const val INTERVAL_MS = 10_000L
        const val FADE_MS = 400L
        const val STATE_INDEX = "index"
        const val STATE_PAUSED = "paused"
    }
}
