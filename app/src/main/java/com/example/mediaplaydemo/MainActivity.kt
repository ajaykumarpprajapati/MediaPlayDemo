package com.example.mediaplaydemo

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.mediaplaydemo.databinding.ActivityMainBinding
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller: MediaController?
        get() = controllerFuture?.takeIf { it.isDone }?.get()

    // Playback works whether or not the notification permission is granted.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val pickMedia =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { play(MediaItem.fromUri(it)) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnAudio.setOnClickListener { play(MediaItem.fromUri(SAMPLE_AUDIO)) }
        binding.btnVideo.setOnClickListener { play(MediaItem.fromUri(SAMPLE_VIDEO)) }
        binding.btnPick.setOnClickListener { pickMedia.launch(arrayOf("audio/*", "video/*")) }

        // Android 13+ requires this for the playback notification to be shown.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener(
            { binding.playerView.player = future.get() },
            MoreExecutors.directExecutor()
        )
    }

    override fun onStop() {
        // Detach the UI only; the service keeps playing in the background.
        binding.playerView.player = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }

    private fun play(item: MediaItem) {
        val c = controller ?: return
        c.setMediaItem(item)
        c.prepare()
        c.play()
    }

    private companion object {
        const val SAMPLE_AUDIO =
            "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3"
        const val SAMPLE_VIDEO =
            "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"
    }
}
