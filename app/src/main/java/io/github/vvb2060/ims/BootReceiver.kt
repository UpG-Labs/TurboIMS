package io.github.vvb2060.ims

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager

/**
 * BootReceiver listens for device boot and schedules a BootWorker to wait for Shizuku and apply
 * saved configurations.
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                Log.i(TAG, "Boot completed, enqueuing BootWorker")
                enqueueBootWork(context)
            }
        }
    }

    private fun enqueueBootWork(context: Context) {
        val workRequest = OneTimeWorkRequest.Builder(BootWorker::class.java).build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
