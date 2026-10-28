package org.lianye

import android.app.Application
import org.lianye.di.AppComponent

import android.content.ComponentName
import android.content.pm.PackageManager
import org.lianye.service.capture.LianyeMediaProjectionService
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

class App : Application() {
    val exportCoordinator by lazy { org.lianye.service.export.ExportCoordinator(this, appComponent.lianyeRepository) }
    private val draftTasks = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun queueDraftTask(task: suspend () -> Unit) { draftTasks.trySend(task) }
    fun persistCurrentDraft() { queueDraftTask { appComponent.lianyeRepository.persistCurrentDraft() } }

    lateinit var appComponent: AppComponent
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        org.lianye.platform.LianyeLogStore.init(this)
        appComponent = AppComponent(filesDir)
        persistenceScope.launch {
            for (task in draftTasks) {
                try { task() } catch (error: Exception) { android.util.Log.e("DraftPersistence", "Draft operation failed", error) }
            }
        }
        configureComponentIsolation()
    }

    private fun configureComponentIsolation() {
        // Manual capture needs MediaProjection on every supported Android version.
        // Re-enable the component when upgrading a build which disabled it on API30+.
        // Automatic API30+ capture still uses the accessibility screenshot API.
        val component = ComponentName(this, LianyeMediaProjectionService::class.java)
        packageManager.setComponentEnabledSetting(
            component,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
