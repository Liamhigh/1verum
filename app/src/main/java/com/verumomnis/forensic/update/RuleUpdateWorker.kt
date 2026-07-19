package com.verumomnis.forensic.update

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Daily background check for signed rule updates.
 *
 * Runs [RuleUpdateClient.checkForUpdate] once per day behind a network
 * constraint. Transient network failures are retried with exponential backoff
 * (up to [MAX_ATTEMPTS] attempts, then deferred to the next daily run);
 * deterministic rejections (bad signature, malformed manifest, stale version)
 * are NOT retried — retrying cannot change their outcome.
 *
 * Scheduled as unique periodic work with the KEEP policy, so re-scheduling on
 * every app start never resets an in-flight or already-scheduled job. The
 * outcome of each run is published as [KEY_RESULT] output data.
 */
class RuleUpdateWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val registry = RuleRegistry.getInstance(applicationContext)
        return when (val result = RuleUpdateClient(registry).checkForUpdate()) {
            is RuleUpdateClient.UpdateResult.Applied ->
                Result.success(outcome("applied v${result.version}"))

            is RuleUpdateClient.UpdateResult.Stale ->
                Result.success(outcome("up-to-date (remote v${result.remoteVersion})"))

            is RuleUpdateClient.UpdateResult.Network ->
                if (runAttemptCount < MAX_ATTEMPTS) Result.retry()
                else Result.success(outcome("network unavailable; deferred to next daily run"))

            is RuleUpdateClient.UpdateResult.BadSignature ->
                Result.success(outcome("rejected: ${result.message}"))

            is RuleUpdateClient.UpdateResult.Malformed ->
                Result.success(outcome("rejected: ${result.message}"))
        }
    }

    private fun outcome(text: String): Data =
        Data.Builder().putString(KEY_RESULT, text).build()

    companion object {
        const val UNIQUE_WORK_NAME = "verum_rule_update"
        const val KEY_RESULT = "rule_update_result"
        private const val MAX_ATTEMPTS = 5

        /**
         * Enqueues the daily unique periodic update check. Safe to call on every
         * app start — KEEP leaves an existing schedule untouched.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<RuleUpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
