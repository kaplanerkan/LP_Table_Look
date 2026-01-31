package com.lotus.lptablelook

import android.app.Application
import com.lotus.lptablelook.data.AppDatabase
import com.lotus.lptablelook.data.TableRepository
import com.lotus.lptablelook.network.SyncService

class TableLookApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val repository: TableRepository by lazy { TableRepository(database) }
    val syncService: SyncService by lazy { SyncService(this, repository) }
}
