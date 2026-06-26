package com.lenovo.mesh.ipv6diag

import android.app.Application
import com.lenovo.mesh.ipv6diag.data.db.AppDatabase
import com.lenovo.mesh.ipv6diag.data.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class IPv6DiagApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var sessionRepository: SessionRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getInstance(this)
        sessionRepository = SessionRepository(db)

        // Seed default server endpoint from config.xml on first launch
        appScope.launch {
            val defaultHostname = getString(R.string.default_server_hostname)
            sessionRepository.seedDefaultEndpointIfNeeded(defaultHostname)
        }
    }
}
