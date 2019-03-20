package com.example.android.omplayer.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.android.omplayer.R
import com.example.android.omplayer.adapters.AlbumAdapter
import com.example.android.omplayer.utils.LibraryUtil


class AlbumFragment : Fragment() {

    var albums = LibraryUtil.albums

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_album, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val layoutManager = LinearLayoutManager(context)
        layoutManager.orientation = RecyclerView.VERTICAL
        val itemAdapter = AlbumAdapter(albums)

        if (albums.isNotEmpty()) {
            val albumList = activity!!.findViewById<RecyclerView>(R.id.album_list_recycler_view)
            albumList.layoutManager = layoutManager
            albumList.adapter = itemAdapter
        }
    }
}