package com.enkod.androidsdk


import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.enkod.androidsdk.EnKodSDK.initPreferences
import com.enkod.androidsdk.EnKodSDK.initRetrofit
import com.enkod.androidsdk.EnKodSDK.isAppInforegrounded
import com.enkod.androidsdk.EnKodSDK.logInfo
import com.enkod.androidsdk.EnKodSDK.startTokenAutoUpdateObserver
import com.enkod.androidsdk.Preferences.TAG
import com.enkod.androidsdk.Variables.defaultTimeAutoUpdateToken
import com.enkod.androidsdk.Variables.millisInHours
import com.enkod.androidsdk.VerificationOfTokenCompliance.startVerificationTokenUsingWorkManager
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rx.subjects.BehaviorSubject
import java.util.concurrent.TimeUnit



internal object TokenAutoUpdate {

    val autoUpdateObserver = AutoUpdateObserver(false)
    var update = false

    fun startTokenAutoUpdateUsingWorkManager (context: Context, time: Int) {

        logInfo("token auto update work start")

        val constraint =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        val workRequest =

            PeriodicWorkRequestBuilder<TokenAutoUpdateWorkManager>(
                time.toLong(),
                TimeUnit.HOURS
            )
                .setInitialDelay(time.toLong(),TimeUnit.HOURS)
                .setConstraints(constraint)
                .build()

        WorkManager

            .getInstance(context)
            .enqueueUniquePeriodicWork(
                "tokenAutoUpdateWorker",
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )

    }

    class TokenAutoUpdateWorkManager(
        context: Context,
        workerParameters: WorkerParameters
    ) :

        Worker(context, workerParameters) {


        override fun doWork(): Result {

            val preferences = applicationContext.getSharedPreferences(TAG, Context.MODE_PRIVATE)

            val preferencesUsingFcm: Boolean? =
                preferences.getBoolean(Preferences.USING_FCM, false)

            if (preferencesUsingFcm == true) {

                if (isAppInforegrounded()) {

                    EnKodSDK.startTokenManualUpdateObserver.observable.subscribe { start ->
                       when (start) {
                           true -> {
                               logInfo("auto update canceled manual update activated")
                               return@subscribe
                           }
                           false -> tokenUpdate(applicationContext)
                       }
                    }
                }
                else tokenUpdate(applicationContext)
            }

            return Result.success()

        }
    }

    private fun tokenUpdate(context: Context) {

        startTokenAutoUpdateObserver.value = true

        initPreferences(context)
        initRetrofit(context)

        logInfo( "token auto update function")

        val preferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)

        var preferencesAcc = preferences.getString(Preferences.ACCOUNT_TAG, null)

        val timeLastTokenUpdate: Long? =
            preferences.getLong(Preferences.TIME_LAST_TOKEN_UPDATE_TAG, 0)

        val setTimeTokenUpdate: Int? =
            preferences.getInt(Preferences.TIME_TOKEN_AUTO_UPDATE_TAG, defaultTimeAutoUpdateToken)

        val timeAutoUpdate =
            (setTimeTokenUpdate ?: defaultTimeAutoUpdateToken) * millisInHours


        if (preferencesAcc != null) {

            if (timeLastTokenUpdate != null && timeLastTokenUpdate > 0) {

                if ((System.currentTimeMillis() - timeLastTokenUpdate) >= timeAutoUpdate-60000) {

                    updateProcess (context, preferencesAcc)

                } else {

                    return
                }
            }
        }

        autoUpdateObserver.observable.subscribe { start ->

            if (start == true) {

                update = false

                CoroutineScope(Dispatchers.IO).launch {

                    delay(5000)

                    logInfo("old token delete, waiting for the update to complete")

                    if (update == false) {

                        logInfo("update reload")

                        updateProcess (context, preferencesAcc ?: "")

                    }
                }
            }
        }
    }

    fun updateProcess (context: Context, preferencesAcc: String) {

        try {

            autoUpdateObserver.value = true

            FirebaseMessaging.getInstance().deleteToken()

                .addOnCompleteListener { task ->

                    if (task.isSuccessful) {

                        logInfo( "token auto update: delete old token")


                        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->

                            if (task.isSuccessful) {

                                val token = task.result

                                EnKodSDK.init(
                                    context,
                                    preferencesAcc,
                                    token
                                )

                                startVerificationTokenUsingWorkManager(context)

                                autoUpdateObserver.value = false
                                update = true

                                logInfo( "token update in auto update function")

                            } else {

                                startVerificationTokenUsingWorkManager(context)


                                logInfo("error get new token in token auto update function")

                            }
                        }

                    } else {


                        startVerificationTokenUsingWorkManager(context)


                        logInfo("error deletion token in token auto update function")

                    }
                }

        } catch (e: Exception) {


            startVerificationTokenUsingWorkManager(context)


            logInfo("error in  token auto update function: $e")

        }
    }
}

class AutoUpdateObserver<T>(private val defaultValue: T) {
    var value: T = defaultValue
        set(value) {
            field = value
            observable.onNext(value)
        }
    val observable = BehaviorSubject.create<T>(value)
}