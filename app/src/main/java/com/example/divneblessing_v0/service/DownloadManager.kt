package com.example.divneblessing_v0.service

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.example.divneblessing_v0.data.DivineRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

class SongDownloadManager(
    private val context: Context,
    private val repository: DivineRepository
) {
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val TAG = "SongDownloadManager"

    /**
     * Download a song from Cloudflare R2
     */
    suspend fun downloadSong(songId: String, audioFileURL: String, title: String): Long {
        return withContext(Dispatchers.IO) {
            try {
                // Create download request
                val fileName = "${songId}.mp3"
                val request = DownloadManager.Request(Uri.parse(audioFileURL))
                    .setTitle(title)
                    .setDescription("Downloading song...")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setDestinationInExternalFilesDir(
                        context,
                        Environment.DIRECTORY_MUSIC,
                        fileName
                    )
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(false)

                // Enqueue download
                val downloadId = downloadManager.enqueue(request)
                Log.d(TAG, "Download started for $title with ID: $downloadId")
                
                downloadId
            } catch (e: Exception) {
                Log.e(TAG, "Error starting download", e)
                -1L
            }
        }
    }

    /**
     * Observe download progress
     */
    fun observeDownload(downloadId: Long): Flow<DownloadProgress> = flow {
        while (true) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)

            if (cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val downloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                
                val status = cursor.getInt(statusIndex)
                val downloaded = cursor.getLong(downloadedIndex)
                val total = cursor.getLong(totalIndex)

                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                        val localUri = cursor.getString(localUriIndex)
                        emit(DownloadProgress.Completed(localUri, total))
                        cursor.close()
                        break
                    }
                    DownloadManager.STATUS_FAILED -> {
                        emit(DownloadProgress.Failed)
                        cursor.close()
                        break
                    }
                    else -> {
                        val progress = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                        emit(DownloadProgress.Downloading(progress, downloaded, total))
                    }
                }
            }
            cursor.close()
            kotlinx.coroutines.delay(500)
        }
    }

    /**
     * Delete downloaded song
     */
    suspend fun deleteSong(songId: String, localFilePath: String?): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (localFilePath != null) {
                    val file = File(Uri.parse(localFilePath).path ?: "")
                    if (file.exists()) {
                        file.delete()
                    }
                }
                
                // Also try deleting from standard location
                val fileName = "${songId}.mp3"
                val file = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), fileName)
                if (file.exists()) {
                    file.delete()
                }
                
                // Update database
                repository.markSongAsNotDownloaded(songId)
                Log.d(TAG, "Song deleted: $songId")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting song", e)
                false
            }
        }
    }
}

sealed class DownloadProgress {
    data class Downloading(val progress: Int, val downloaded: Long, val total: Long) : DownloadProgress()
    data class Completed(val localUri: String, val fileSize: Long) : DownloadProgress()
    object Failed : DownloadProgress()
}
