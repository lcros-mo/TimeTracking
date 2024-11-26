package com.timetracking.app.utils

import AppVPNService
import android.content.Context
import android.content.Intent
import android.net.VpnService

class VPNManager(private val context: Context) {
    private var isConnected = false

    fun connect(): Boolean {
        if (isConnected) return true

        val intent = VpnService.prepare(context)
        if (intent != null) {
            context.startActivity(intent)
            return false
        }

        context.startService(Intent(context, AppVPNService::class.java))
        isConnected = true
        return true
    }

    fun disconnect() {
        if (!isConnected) return
        context.stopService(Intent(context, AppVPNService::class.java))
        isConnected = false
    }
}