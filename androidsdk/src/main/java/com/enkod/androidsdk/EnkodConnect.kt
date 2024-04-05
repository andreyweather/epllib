package com.enkod.androidsdk

import android.content.Context
import android.content.Intent
import android.os.Build
import com.enkod.androidsdk.EnKodSDK.isAppInforegrounded
import com.enkod.androidsdk.EnKodSDK.logInfo
import com.enkod.androidsdk.EnKodSDK.startTokenManualUpdateObserver
import com.enkod.androidsdk.Preferences.START_AUTO_UPDATE_TAG
import com.enkod.androidsdk.Preferences.TAG
import com.enkod.androidsdk.Preferences.TIME_LAST_TOKEN_UPDATE_TAG
import com.enkod.androidsdk.Preferences.USING_FCM
import com.enkod.androidsdk.Variables.defaultTimeAutoUpdateToken
import com.enkod.androidsdk.Variables.defaultTimeManualUpdateToken
import com.enkod.androidsdk.Variables.millisInHours
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging


class EnkodConnect(

    _account: String?,
    _usingFcm: Boolean? = false,
    _tokenManualUpdate: Boolean? = true,
    _tokenAutoUpdate: Boolean? = true,
    _timeTokenManualUpdate: Int? = null,
    _timeTokenAutoUpdate: Int? = null

) {

    private val account: String
    private val usingFcm: Boolean
    private val tokenManualUpdate: Boolean
    private val tokenAutoUpdate: Boolean
    private var timeTokenManualUpdate: Int
    private var timeTokenAutoUpdate: Int


    init {

        account = _account ?: ""
        usingFcm = _usingFcm ?: false
        tokenManualUpdate = _tokenManualUpdate ?: true
        tokenAutoUpdate = _tokenAutoUpdate ?: true

        timeTokenManualUpdate =

            if (_timeTokenManualUpdate != null && _timeTokenManualUpdate > 0) _timeTokenManualUpdate
            else defaultTimeManualUpdateToken

        timeTokenAutoUpdate =

            if (_timeTokenAutoUpdate != null && _timeTokenAutoUpdate > 0) _timeTokenAutoUpdate
            else defaultTimeAutoUpdateToken

    }


    fun start(context: Context) {

        logInfo("user settings: account: $account, fcm: $usingFcm, tokenMU: $tokenManualUpdate, tokenAU: $tokenAutoUpdate, timeMU: $timeTokenManualUpdate, timeAU: $timeTokenAutoUpdate")

        val preferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
        val preferencesStartTokenAutoUpdate = preferences.getString(START_AUTO_UPDATE_TAG, null)

        when (usingFcm) {

            true -> {

                preferences.edit()

                    .putBoolean(USING_FCM, true)
                    .apply()

                if (EnKodSDK.isOnline(context)) {

                    EnKodSDK.isOnlineStatus(true)

                    try {

                        FirebaseMessaging.getInstance().token.addOnCompleteListener(
                            OnCompleteListener { task ->
                                if (!task.isSuccessful) {

                                    return@OnCompleteListener
                                }

                                val token = task.result

                                logInfo("current fcm token: $token")

                                if (preferencesStartTokenAutoUpdate == null && tokenAutoUpdate) {

                                    preferences.edit()

                                        .putInt(
                                            Preferences.TIME_TOKEN_AUTO_UPDATE_TAG,
                                            timeTokenAutoUpdate
                                        )
                                        .apply()


                                    TokenAutoUpdate.startTokenAutoUpdateUsingWorkManager(
                                        context,
                                        timeTokenAutoUpdate
                                    )

                                    preferences.edit()

                                        .putString(START_AUTO_UPDATE_TAG, Variables.start)
                                        .apply()
                                }

                                if (tokenManualUpdate) {

                                    startTokenManualUpdateObserver.value = false

                                    tokenUpdate(context, timeTokenManualUpdate)

                                }

                                EnKodSDK.init(context, account, token)

                                logInfo("start library with fcm")

                            })

                    } catch (e: Exception) {

                        EnKodSDK.init(context, account)

                        logInfo("the library started using fcm with an error")

                    }

                } else {

                    EnKodSDK.isOnlineStatus(false)

                    logInfo("error internet")
                }
            }

            false -> {

                preferences.edit()

                    .putBoolean(USING_FCM, false)
                    .apply()

                if (EnKodSDK.isOnline(context)) {
                    EnKodSDK.isOnlineStatus(true)
                    EnKodSDK.init(context, account)
                    logInfo("start library without fcm")
                } else {

                    EnKodSDK.isOnlineStatus(false)
                    logInfo("error internet")
                }
            }
        }
    }

    private fun tokenUpdate(context: Context, timeUpdate: Int) {

        val timeUpdateInMillis: Long = ((timeUpdate * millisInHours)).toLong()

        val preferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
        val timeLastTokenUpdate = preferences.getLong(TIME_LAST_TOKEN_UPDATE_TAG, 0)

        if (isAppInforegrounded()) {

            if (EnKodSDK.isOnline(context)) {

                if (timeLastTokenUpdate > 0) {

                    if ((System.currentTimeMillis() - timeLastTokenUpdate) >= timeUpdateInMillis) {

                        logInfo("start manual update in start method")

                        startTokenManualUpdateObserver.value = true

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(
                                Intent(
                                    context,
                                    TokenManualUpdateService::class.java
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

