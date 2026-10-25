package com.fetchly.app

import android.content.Context
import android.os.Environment
import androidx.room.Room
import com.fetchly.app.data.FetchlyDatabase
import com.fetchly.app.download.DownloadManager
import com.fetchly.app.download.DownloadRunner
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

/** Creates the app's shared objects once per process. */
class AppContainer(context: Context) {

    private val database: FetchlyDatabase = Room.databaseBuilder(
        context,
        FetchlyDatabase::class.java,
        FetchlyDatabase.NAME,
    ).build()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // Read timeout catches stalled connections. Without it a dead connection can hang forever.
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** App-private external storage. Visible in Android/data/<package>; moving to MediaStore is a later step. */
    private val downloadsDir: File =
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val downloads: DownloadManager = DownloadManager(
        dao = database.downloadDao(),
        runner = DownloadRunner(database.downloadDao(), client),
        scope = appScope,
        downloadsDir = downloadsDir,
    )
}
