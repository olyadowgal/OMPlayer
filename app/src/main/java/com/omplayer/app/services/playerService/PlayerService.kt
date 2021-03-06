package com.omplayer.app.services.playerService

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.omplayer.app.R
import com.omplayer.app.activities.MainActivity
import com.omplayer.app.application.App.Companion.CHANNEL_ID
import com.omplayer.app.consts.Extra
import com.omplayer.app.consts.RequestCodes
import com.omplayer.app.di.SingletonHolder
import com.omplayer.app.extensions.foreverObserver
import com.omplayer.app.fragments.PlayerFragment
import com.omplayer.app.livedata.ForeverObserver
import com.omplayer.app.stateMachine.Action
import com.omplayer.app.stateMachine.states.PlayingState
import com.omplayer.app.utils.FormatUtils
import com.omplayer.app.utils.LastFmUtil
import com.omplayer.app.utils.LibraryUtil
import com.omplayer.app.viewmodels.LyricsViewModel
import com.omplayer.app.viewmodels.PlayerViewModel
import kotlinx.android.synthetic.main.fragment_player.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit


class PlayerService : Service() {

    private val playerManager = SingletonHolder.playerManager
    private val serviceContext: Context = this

    private val playerViewModel = PlayerViewModel(SingletonHolder.application)
    private val foreverObservers = mutableListOf<ForeverObserver<*>>()

    private val becomingNoisyReceiver =
        BecomingNoisyReceiver()

//region Intents init
     private val activityIntent = Intent(SingletonHolder.application, MainActivity::class.java)

    private val contentIntent by lazy {

        PendingIntent.getActivity(this,
            0,
            activityIntent,
            PendingIntent.FLAG_CANCEL_CURRENT)
    }

    private val stopPendingIntent by lazy {
        PendingIntent.getBroadcast(
            this,
            RequestCodes.STOP.hashCode(),
            Intent(this, PlayerBroadcastReceiver::class.java)
                .putExtra(Extra.ACTION, Action.Stop()),
            PendingIntent.FLAG_CANCEL_CURRENT
        )
    }

    private val playPendingIntent by lazy {
        PendingIntent.getBroadcast(
            this,
            RequestCodes.PLAY.hashCode(),
            Intent(this, PlayerBroadcastReceiver::class.java)
                .putExtra(Extra.ACTION, Action.Play()),
            PendingIntent.FLAG_CANCEL_CURRENT
        )
    }

    private val pausePendingIntent by lazy {
        PendingIntent.getBroadcast(
            this,
            RequestCodes.PAUSE.hashCode(),
            Intent(this, PlayerBroadcastReceiver::class.java)
                .putExtra(Extra.ACTION, Action.Pause()),
            PendingIntent.FLAG_CANCEL_CURRENT
        )
    }

    private val nextPendingIntent by lazy {
        PendingIntent.getBroadcast(
            this,
            RequestCodes.NEXT.hashCode(),
            Intent(this, PlayerBroadcastReceiver::class.java)
                .putExtra(Extra.ACTION, Action.Next()),
            PendingIntent.FLAG_CANCEL_CURRENT
        )
    }

    private val prevPendingIntent by lazy {
        PendingIntent.getBroadcast(
            this,
            RequestCodes.PREV.hashCode(),
            Intent(this, PlayerBroadcastReceiver::class.java)
                .putExtra(Extra.ACTION, Action.Prev()),
            PendingIntent.FLAG_CANCEL_CURRENT
        )
    }

    //endregion

    override fun onCreate() {
        super.onCreate()
        //Handles headphones coming unplugged. cannot be done through a manifest receiver
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(becomingNoisyReceiver, filter)

        if (scheduledTask == null) {
            scheduledTask = executor.schedule(
                observeTrackTimeTask,
                0,
                TimeUnit.SECONDS
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        MediaButtonReceiver.handleIntent(playerManager.mediaSessionCompat, intent)

        when (intent?.getSerializableExtra(Extra.ACTION)) {
            is Action.Play -> {
                getNotification(R.drawable.ic_pause, "ic_pause", pausePendingIntent)
            }

            is Action.Pause -> {
                getNotification(R.drawable.ic_play, "ic_play", playPendingIntent)
            }
        }

        return START_NOT_STICKY
    }



    private fun getNotification(icon: Int, text: String, intent: PendingIntent) {
        CoroutineScope(Dispatchers.IO).launch {
            withContext(coroutineContext) {
                val currentTrack = LibraryUtil.tracklist[LibraryUtil.selectedTrack]
                val currentAlbum = SingletonHolder.db.albumDao().getAlbumById(currentTrack.albumId)
                val currentArtist = SingletonHolder.db.artistDao().getArtistById(currentTrack.artistId)
                val currentCover = loadImage(currentAlbum.cover)
                withContext(Dispatchers.Main) {
                    val notification: Notification = NotificationCompat.Builder(serviceContext, CHANNEL_ID)
                        .setLargeIcon(currentCover)
                        .setSmallIcon(R.drawable.ic_note)
                        .setContentTitle(currentTrack.title)
                        .setContentText(currentArtist.name)
                        .setContentIntent(contentIntent)
                        .addAction(R.drawable.ic_prev, "ic_prev", prevPendingIntent)
                        .addAction(icon, text, intent)
                        .addAction(R.drawable.ic_next, "ic_next", nextPendingIntent)
                        .addAction(R.drawable.ic_close, "ic_close", stopPendingIntent)
                        .setStyle(MediaStyle())
                        .build()!!
                    startForeground(1, notification)
                }
            }
        }
    }

    private fun loadImage(albumArtUrl: String): Bitmap {
        try {
            val file = File(albumArtUrl)
            val uri = Uri.fromFile(file)

            return Glide.with(serviceContext)
                .asBitmap()
                .load(uri).apply(
                    RequestOptions()
                        .placeholder(R.drawable.placeholder)
                        .error(R.drawable.placeholder)
                ).submit().get()
        } catch (e: Exception) {
        }
        return BitmapFactory.decodeResource(serviceContext.resources, R.drawable.placeholder)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        foreverObservers.forEach { it.release() }
        scheduledTask?.cancel(true)
        scheduledTask = null
        unregisterReceiver(becomingNoisyReceiver)
        stopForeground(true)
    }

    private var timestamp: Long = 0
    private var lastTrackUpdateTime: Long = 0

    private var scheduledTask: ScheduledFuture<*>? = null
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    private val observeTrackTimeTask = Runnable {
        Handler(Looper.getMainLooper()).post {
            foreverObservers.add(playerViewModel.currentPosition.foreverObserver(Observer {
                if (it != null && SingletonHolder.playerManager.currState.value is PlayingState) {
                    if (it < 1000) {
                        timestamp = LastFmUtil.timestamp()
                        LastFmUtil.isScrobbled = false
                        playerViewModel.updateLastFmTrack()
                        lastTrackUpdateTime = timestamp
                    }
                    if (LastFmUtil.timestamp() - lastTrackUpdateTime > 180) {
                        playerViewModel.updateLastFmTrack()
                        lastTrackUpdateTime = LastFmUtil.timestamp()
                    }
                    if (!LastFmUtil.isScrobbled && playerViewModel.scrollableTrack(it, timestamp)) {
                        playerViewModel.scrobbleTrack(timestamp.toString())
                        LastFmUtil.isScrobbled = true
                    }
                }
            }))
        }
    }

}
