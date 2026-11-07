package tv.own.owntv.features.settings

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import tv.own.owntv.R

/** Dedicated audio service for IPTV radio. Terrestrial radio retains its TV input session. */
internal class RadioPlaybackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.stayon_radio_iptv_title), NotificationManager.IMPORTANCE_LOW)
            )
        }
        scope.launch {
            RadioAudioPlayer.playingStationId.collect { id -> if (started && id == null) stopSelf() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(STATION)
        val station = StayonRadioLibrary.shared(this).stations.value.firstOrNull {
            it.id == id && it.tunerChannel == null
        }
        if (station == null) {
            RadioAudioPlayer.stop()
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val stop = PendingIntent.getService(this, 0, Intent(this, RadioPlaybackService::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(station.name)
            .setContentText(getString(R.string.stayon_radio_iptv_title))
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.stayon_radio_stop), stop)
            .build()
        ServiceCompat.startForeground(this, 53, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        started = false
        RadioAudioPlayer.play(station)
        started = true
        if (RadioAudioPlayer.playingStationId.value == null) stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        RadioAudioPlayer.stop()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "stayon_radio"
        private const val STATION = "station"

        fun play(context: Context, station: StayonRadioStation) {
            runCatching {
                ContextCompat.startForegroundService(context,
                    Intent(context, RadioPlaybackService::class.java).putExtra(STATION, station.id))
            }.onFailure { RadioAudioPlayer.reportStartFailure(context.getString(R.string.stayon_radio_failed)) }
        }
    }
}
