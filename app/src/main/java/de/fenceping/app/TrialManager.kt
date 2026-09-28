package de.fenceping.app

import android.content.Context

class TrialManager(context: Context) {
    private val prefs = context.getSharedPreferences("trial_prefs", Context.MODE_PRIVATE)

    init {
        if (!prefs.contains(KEY_INSTALL)) {
            prefs.edit().putLong(KEY_INSTALL, System.currentTimeMillis()).apply()
        }
    }

    fun remainingDays(): Int {
        val left = TRIAL_MS - (System.currentTimeMillis() - installTime())
        if (left <= 0) return 0
        return Math.ceil(left.toDouble() / DAY_MS).toInt()
    }

    fun isExpired(): Boolean = System.currentTimeMillis() - installTime() > TRIAL_MS

    private fun installTime(): Long = prefs.getLong(KEY_INSTALL, System.currentTimeMillis())

    companion object {
        private const val KEY_INSTALL = "install_time"
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val TRIAL_MS = 10L * DAY_MS
    }
}
