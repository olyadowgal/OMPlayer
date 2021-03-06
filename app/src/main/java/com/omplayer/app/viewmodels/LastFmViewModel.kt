package com.omplayer.app.viewmodels

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.github.siyamed.shapeimageview.RoundedImageView
import com.mikhaellopez.circularimageview.CircularImageView
import com.omplayer.app.R
import com.omplayer.app.db.entities.Album
import com.omplayer.app.db.entities.Artist
import com.omplayer.app.di.SingletonHolder
import com.omplayer.app.di.SingletonHolder.db
import com.omplayer.app.dialogFragments.LastFmLoginDialogFragment
import com.omplayer.app.entities.*
import com.omplayer.app.fragments.PlayerFragment
import com.omplayer.app.fragments.SettingsFragment
import com.omplayer.app.repositories.LastFmRepository
import com.omplayer.app.utils.ImageUtil
import com.omplayer.app.utils.ImageUtil.saveImage
import com.omplayer.app.utils.LastFmUtil
import com.omplayer.app.utils.LastFmUtil.md5
import com.omplayer.app.utils.LibraryUtil
import com.omplayer.app.utils.PreferenceUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File


class LastFmViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "LastFmViewModel"
    private val API_KEY = "2f8cf16e71b4979ee0a4d2a692ed1206"
    private var SECRET = "9cc8988a83f3cbac07a6709c10361cd1"
    private var LAST_FM_REGISTRATION = "https://www.last.fm/join"
    private val lastFmRepository = LastFmRepository()

    fun register(activity: Activity) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(LAST_FM_REGISTRATION))
        activity.startActivity(browserIntent)
    }

    fun logIn(username: String, password: String, dialog: LastFmLoginDialogFragment, fragment: SettingsFragment) {
        val apiSignature: String =
            "api_key" + API_KEY + "methodauth.getMobileSessionpassword" + password + "username" + username + SECRET
        lastFmRepository.getLastFmSession(API_KEY, password, username, md5(apiSignature)!!)
            .enqueue(object : Callback<LastFmSessionWrapper> {

                override fun onResponse(call: Call<LastFmSessionWrapper>, response: Response<LastFmSessionWrapper>) {
                    try {
                        PreferenceUtil.currentLastFmSession = response.body()!!.session
                        PreferenceUtil.scrobble = true
                        dialog.dismiss()
                        fragment.initializeUserProfile()
                    } catch (e: Exception) {
                        Log.d(TAG, response.message())
                        if (response.message().contains("Forbidden")) {
                            Toast.makeText(getApplication(), "Wrong username or password", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(getApplication(), "Error", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                override fun onFailure(call: Call<LastFmSessionWrapper>, t: Throwable) {
                    Log.d(TAG, t.message)
                    Toast.makeText(getApplication(), "Error", Toast.LENGTH_SHORT).show()
                }

            })
    }

    fun updatePlayingTrack(album: String, artist: String, track: String) {

        val apiSignature: String =
            "album" + album + "api_key" + API_KEY + "artist" + artist + "methodtrack.updateNowPlaying" + "sk" + PreferenceUtil.currentLastFmSession!!.key + "track" + track + SECRET
        lastFmRepository.updatePlayingTrack(
            album,
            artist,
            track,
            API_KEY,
            md5(apiSignature)!!,
            PreferenceUtil.currentLastFmSession!!.key
        ).enqueue(object : Callback<ResponseBody> {

            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                Log.d(TAG, response.body()!!.string())
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Log.d(TAG, t.message)
            }

        })
    }

    fun scrobble(album: String, artist: String, track: String, timestamp: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val apiSignature: String =
                "album" + album + "api_key" + API_KEY + "artist" + artist + "methodtrack.scrobble" + "sk" + PreferenceUtil.currentLastFmSession!!.key + "timestamp" + timestamp + "track" + track + SECRET
            lastFmRepository.scrobbleTrack(
                album,
                artist,
                track,
                timestamp,
                API_KEY,
                md5(apiSignature)!!,
                PreferenceUtil.currentLastFmSession!!.key
            ).enqueue(object : Callback<ResponseBody> {

                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                    Log.d(TAG, response.body()!!.string())
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    Log.d(TAG, t.message)
                }

            })
        }
    }

    fun loveTrack(artist: String, track: String) {
        val apiSignature: String =
            "api_key" + API_KEY + "artist" + artist + "methodtrack.love" + "sk" + PreferenceUtil.currentLastFmSession!!.key + "track" + track + SECRET
        lastFmRepository.loveTrack(
            artist,
            track,
            API_KEY,
            md5(apiSignature)!!,
            PreferenceUtil.currentLastFmSession!!.key
        ).enqueue(object : Callback<ResponseBody> {

            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                Log.d(TAG, response.message())
                Toast.makeText(getApplication(), "Track added to loved", Toast.LENGTH_SHORT).show()
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Log.d(TAG, t.message)
            }

        })
    }

    fun getUserInfo(user: String, userImageView: CircularImageView) {
        try {
            lastFmRepository.getUserInfo(user, API_KEY).enqueue(object : Callback<LastFmUserWrapper> {

                override fun onResponse(call: Call<LastFmUserWrapper>, response: Response<LastFmUserWrapper>) {
                    try {
                        val imageList = response.body()!!.user.images
                        imageList.forEach {
                            if (it.size == "extralarge") {
                                val imageUrl = it.url

                                Glide.with(SingletonHolder.application).load(imageUrl)
                                    .apply(RequestOptions().placeholder(R.drawable.ic_last_fm_placeholder).error(R.drawable.ic_last_fm_placeholder))
                                    .into(userImageView)
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, e.message)
                    }
                }

                override fun onFailure(call: Call<LastFmUserWrapper>, t: Throwable) {
                    Log.d(TAG, t.message)
                }

            })
        } catch (e: Exception) {
        }
    }


    fun getArtistInfo(artist: Artist, artistImageView: RoundedImageView) {
        if (artist.image.isEmpty()) {
            try {
                lastFmRepository.getArtistInfo(artist.name, API_KEY).enqueue(object : Callback<LastFmArtistWrapper> {

                    override fun onResponse(call: Call<LastFmArtistWrapper>, response: Response<LastFmArtistWrapper>) {
                        try {
                            val imageList = response.body()!!.artist.images
                            imageList.forEach {
                                if (it.size == "extralarge") {
                                    val imageUrl = it.url
                                    saveImage(imageUrl, artist.name)
                                    val path = SingletonHolder.application.filesDir.absolutePath
                                    val file = File(path, "${artist.name}.jpg")
                                    artist.image = file.absolutePath
                                    CoroutineScope(Dispatchers.IO).launch {
                                        db.artistDao().update(artist)
                                    }
                                    Glide.with(SingletonHolder.application).load(imageUrl)
                                        .apply(RequestOptions().placeholder(R.drawable.ic_last_fm_placeholder).error(R.drawable.ic_last_fm_placeholder))
                                        .into(artistImageView)

                                }
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, e.message)
                        }
                    }

                    override fun onFailure(call: Call<LastFmArtistWrapper>, t: Throwable) {
                        Log.d(TAG, t.message)
                    }

                })
            } catch (e: Exception) {
            }
        }
    }

    fun getSimilarTracks(title: String, artist: String, fragment: PlayerFragment) {
        lastFmRepository.getSimilarTracks(title, artist, API_KEY)
            .enqueue(object : Callback<LastFmSimilarTracksWrapper> {

                override fun onResponse(
                    call: Call<LastFmSimilarTracksWrapper>,
                    response: Response<LastFmSimilarTracksWrapper>
                ) {
                    try {
                        val similarTracks = response.body()!!.similarTracks.similarTracksList
                        if (similarTracks.isNotEmpty()) {
                            LastFmUtil.similarTracks = similarTracks
                            LastFmUtil.originalTrack = title
                            val trackViewModel = TrackViewModel(SingletonHolder.application)
                            trackViewModel.goToFragment(fragment)
                        } else {
                            Toast.makeText(getApplication(), "No similar tracks found", Toast.LENGTH_SHORT).show()
                        }

                    } catch (e: Exception) {
                        Log.d(TAG, e.message)
                    }
                }

                override fun onFailure(call: Call<LastFmSimilarTracksWrapper>, t: Throwable) {
                    Log.d(TAG, t.message)
                    if (t.message!!.contains("timeout")) {
                        Toast.makeText(getApplication(), "Server Timeout", Toast.LENGTH_SHORT).show()
                    }
                }

            })
    }

    fun getTrackInfo(
        title: String,
        artist: String,
        album: String,
        artistImageView: CircularImageView,
        currentAlbum: Album,
        load: Boolean
    ) {
        if (currentAlbum.cover.isEmpty() || load) {
            try {
                lastFmRepository.getTrackInfo(title, artist, API_KEY).enqueue(object : Callback<LastFmTrackWrapper> {

                    override fun onResponse(call: Call<LastFmTrackWrapper>, response: Response<LastFmTrackWrapper>) {
                        try {
                            val imageList = response.body()!!.track.album.images
                            imageList.forEach {
                                if (it.size == "extralarge") {
                                    val imageUrl = it.url
                                    saveImage(imageUrl, album + "_" + artist)
                                    val path = SingletonHolder.application.filesDir.absolutePath
                                    val file = File(path, "${album + "_" + artist}.jpg")
                                    currentAlbum.cover = file.absolutePath
                                    LibraryUtil.currentAlbumList[LibraryUtil.selectedAlbum].cover = file.absolutePath
                                    CoroutineScope(Dispatchers.IO).launch {
                                        db.albumDao().update(currentAlbum)
                                    }
                                    Glide.with(SingletonHolder.application).load(imageUrl)
                                        .apply(RequestOptions().placeholder(R.drawable.placeholder).error(R.drawable.placeholder))
                                        .into(artistImageView)
                                }
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, e.message)
                            Toast.makeText(getApplication(), "Cover not found", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(call: Call<LastFmTrackWrapper>, t: Throwable) {
                        Log.d(TAG, t.message)
                        Toast.makeText(getApplication(), "Cover not found", Toast.LENGTH_SHORT).show()
                    }

                })
            } catch (e: Exception) {
                Toast.makeText(getApplication(), "Cover not found", Toast.LENGTH_SHORT).show()
            }
        }
    }

}