package com.example.android.omplayer.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity
data class Artist(
    @PrimaryKey(autoGenerate = true)
    val id: Int = -1,
    var name: String = "",
    val country: String = "",
    val description: String = "",
    val image: String = ""
)