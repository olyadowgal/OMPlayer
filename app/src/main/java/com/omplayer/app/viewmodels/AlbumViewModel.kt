package com.omplayer.app.viewmodels

import android.app.Application
import android.view.View
import android.widget.TextView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.omplayer.app.adapters.AlbumAdapter
import com.omplayer.app.db.entities.Track
import com.omplayer.app.di.SingletonHolder
import com.omplayer.app.fragments.BaseAlbumFragment
import com.omplayer.app.repositories.ArtistRepository
import com.omplayer.app.repositories.TrackRepository
import com.omplayer.app.utils.LibraryUtil
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

class AlbumViewModel(application: Application) : AndroidViewModel(application), AlbumAdapter.Callback {

    private var parentJob = Job()
    private val coroutineContext: CoroutineContext
        get() = parentJob + Dispatchers.IO
    private val scope = CoroutineScope(coroutineContext)
    private val db = SingletonHolder.db

    private val trackRepository: TrackRepository = TrackRepository(db.trackDao())
    private val artistRepository: ArtistRepository = ArtistRepository(db.artistDao())
    private var tracks = ArrayList<Track>()

    val itemAdapter = AlbumAdapter(LibraryUtil.albums, this)

    private var _viewLiveData: MutableLiveData<View> = MutableLiveData()
    var viewLiveData = _viewLiveData


    fun getAlbumName(): String {
        return LibraryUtil.currentAlbumList[LibraryUtil.selectedAlbum].title
    }



    fun getAlbumArtist(albumArtistName: TextView) {
        scope.launch {
            withContext(coroutineContext) {
                val albumArtist =
                    artistRepository.getArtistById(LibraryUtil.currentAlbumList[LibraryUtil.selectedAlbum].artistId)!!.name
                withContext(Dispatchers.Main) {
                    albumArtistName.text = albumArtist
                }
            }
        }
    }

    fun getAlbumYear(): String {
        return LibraryUtil.currentAlbumList[LibraryUtil.selectedAlbum].year
    }

    fun getAlbumCoverUrl(): String {
        return LibraryUtil.currentAlbumList[LibraryUtil.selectedAlbum].cover
    }

    override fun loadAlbumTracks(albumId: Int, view: View) {
        scope.launch {
            withContext(coroutineContext) {
                tracks = trackRepository.getTracksByAlbumId(albumId) as ArrayList<Track>
                withContext(Dispatchers.Main) {
                    LibraryUtil.selectedAlbumTracklist = tracks
                    _viewLiveData.value = view
                }
            }
        }
    }

//    fun loadAlbumTracks(albumId: Int, fragment: BaseAlbumFragment) {
//        scope.launch {
//            withContext(coroutineContext) {
//                tracks = trackRepository.getTracksByAlbumId(albumId) as ArrayList<Track>
//                withContext(Dispatchers.Main) {
//                    LibraryUtil.selectedAlbumTracklist = tracks
//                    fragment.selectAlbum()
//                }
//            }
//        }
//    }
}