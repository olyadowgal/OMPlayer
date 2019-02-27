package com.example.android.musicplayerdemo.services.playerService

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.android.musicplayerdemo.consts.Extra
import com.example.android.musicplayerdemo.di.SingletonHolder
import com.example.android.musicplayerdemo.stateMachine.Action

class PlayerBroadcastReceiver : BroadcastReceiver() {


    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getSerializableExtra(Extra.ACTION) as Action
        SingletonHolder.playerManager.performAction(action)
        SingletonHolder.playerServiceManager.startService(action)
    }

}