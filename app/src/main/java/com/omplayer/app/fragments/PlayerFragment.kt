package com.omplayer.app.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.omplayer.app.R
import com.omplayer.app.activities.MainActivity
import com.omplayer.app.di.SingletonHolder
import com.omplayer.app.stateMachine.states.PausedState
import com.omplayer.app.stateMachine.states.PlayingState
import com.omplayer.app.utils.FormatUtils
import com.omplayer.app.viewmodels.LyricsViewModel
import com.omplayer.app.viewmodels.PlayerViewModel
import com.omplayer.app.viewmodels.PlayerViewModel.Companion.LOOP_MODE
import com.omplayer.app.viewmodels.PlayerViewModel.Companion.NORMAL_MODE
import com.omplayer.app.viewmodels.PlayerViewModel.Companion.SHUFFLE_MODE
import com.omplayer.app.viewmodels.VideoViewModel
import com.savantech.seekarc.SeekArc
import kotlinx.android.synthetic.main.fragment_player.*


class PlayerFragment : Fragment(), View.OnClickListener {

    private val viewModel: PlayerViewModel by lazy {
        ViewModelProviders.of(this).get(PlayerViewModel::class.java)
    }
    private val lyricsViewModel = LyricsViewModel(SingletonHolder.application)
    private val videoViewModel = VideoViewModel(SingletonHolder.application)

    private var isPlaying = false
    private var isFavorite = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        (activity as MainActivity)
            .setActionBarTitle(getString(R.string.action_bar_player))
        (activity as MainActivity).supportActionBar!!.setDisplayHomeAsUpEnabled(false)
        return inflater.inflate(R.layout.fragment_player, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        lifecycle.addObserver(viewModel)
        super.onViewCreated(view, savedInstanceState)

        initializeSeekbar()

        initializeTrackInfo()

        button_previous.setOnClickListener(this)
        button_next.setOnClickListener(this)
        button_play.setOnClickListener(this)
        button_youtube_player.setOnClickListener(this)
        button_shuffle.setOnClickListener(this)
        button_lyrics.setOnClickListener(this)
        button_favorites.setOnClickListener(this)

        viewModel.metadata.observe(this, Observer {
            it?.let { metadata ->
                seekbar_audio.setMaxProgress(metadata.duration.toFloat())
                timer_total.text = FormatUtils.millisecondsToString(metadata.duration.toLong())
                initializeTrackInfo()
            }
        })
        viewModel.favoriteMode.observe(this, Observer {
            isFavorite = it
            if (it) { button_favorites.setImageResource(R.drawable.ic_favorite) }
            else {button_favorites.setImageResource(R.drawable.ic_favorite_border)}
        })

        viewModel.currentPosition.observe(this, Observer {
            seekbar_audio.progress = it?.toFloat() ?: (0).toFloat()
            timer_played.text = it?.toLong()?.let { it1 -> FormatUtils.millisecondsToString(it1) }
        })

        viewModel.shuffleMode.observe(this, Observer {
            if (it != null) {
                button_shuffle.setImageResource(it)
            }
        })

        viewModel.currState.observe(this, Observer {
            when (it) {
                is PlayingState -> {
                    button_play.setImageResource(R.drawable.ic_pause_circle)
                    isPlaying = true
                }
                is PausedState -> {
                    button_play.setImageResource(R.drawable.ic_play_circle)
                    isPlaying = false
                }
            }
        })

    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.button_play -> {
                if (!isPlaying) {
                    button_play.setImageResource(R.drawable.ic_pause_circle)
                    viewModel.onPlayClicked()
                    isPlaying = true
                } else {
                    button_play.setImageResource(R.drawable.ic_play_circle)
                    viewModel.onPauseClicked()
                    isPlaying = false
                }
            }
            R.id.button_next -> viewModel.onNextClicked()
            R.id.button_previous -> viewModel.onPrevClicked()
            R.id.button_youtube_player -> {
                viewModel.onPauseClicked()
                videoViewModel.playVideo(fragmentManager!!)
            }
            R.id.button_shuffle -> viewModel.onSetRepeatShuffleMode()
            R.id.button_lyrics -> lyricsViewModel.getSongLyrics(
                tv_track_artist.text.toString(),
                tv_track_title.text.toString(),
                fragmentManager!!
            )
            R.id.button_favorites -> viewModel.onFavoritesButtonClicked(isFavorite)
        }
    }

    //region Initializing seekbar

    private fun initializeSeekbar() {
        seekbar_audio.setThumbRadius(8)

        seekbar_audio.setOnSeekArcChangeListener(object : SeekArc.OnSeekArcChangeListener {
            var userSelectedPosition = 0
            override fun onStartTrackingTouch(seekArc: SeekArc?) {
                viewModel.stopUpdateSeekbar()
            }

            override fun onProgressChanged(seekArc: SeekArc?, progress: Float) {
                userSelectedPosition = progress.toInt()
            }

            override fun onStopTrackingTouch(seekArc: SeekArc?) {
                viewModel.onSeek(userSelectedPosition)
                viewModel.startUpdateSeekbar()
            }
        })
    }

    fun initializeTrackInfo() {
        viewModel.loadTrackData(iv_track_cover, tv_track_title, tv_track_album, tv_track_artist, context!!)
    }

    //endregion
}
