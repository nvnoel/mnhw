package com.shinigami.client.manager

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class NetworkManager(ctx: Context) {

  private val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

  val networkStatus: Flow<Boolean> = callbackFlow {
    val cb = object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(net: Network) = trySend(true).let {}
      override fun onLost(net: Network) = trySend(false).let {}
      override fun onUnavailable() = trySend(false).let {}
    }

    val req = NetworkRequest.Builder()
      .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
      .build()

    cm?.registerNetworkCallback(req, cb)
    trySend(checkConnectionNow())

    awaitClose { cm?.unregisterNetworkCallback(cb) }
  }

  fun checkConnectionNow(): Boolean {
    val net = cm?.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(net) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }
}