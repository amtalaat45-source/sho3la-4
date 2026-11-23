package com.example.studyvault

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class SocialBlockerService : AccessibilityService() {

    private val blockedSocialApps = setOf(
        "com.facebook.katana",
        "com.facebook.lite",
        "com.instagram.android",
        "com.instagram.lite",
        "com.google.android.youtube"
    )

    private val excludedApps = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b"
    )

    private var imePackages: Set<String> = emptySet()

    private lateinit var gamificationManager: GamificationManager
    private var currentForegroundPackage: String = ""
    private val handler = Handler(Looper.getMainLooper())
    private var lastToastTime = 0L

    private val monitorRunnable = object : Runnable {
        override fun run() {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (powerManager.isInteractive) {
                refreshForegroundFromWindow()   // do not rely only on events (HyperOS/MIUI skip some)
                if (blockedSocialApps.contains(currentForegroundPackage)) {
                    enforceSocialRules(isTick = true)
                }
            }
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        gamificationManager = GamificationManager(this)
        refreshImePackages()
        handler.removeCallbacks(monitorRunnable)   // never run two loops
        handler.post(monitorRunnable)
    }

    private fun refreshImePackages() {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imePackages = imm.enabledInputMethodList.map { it.packageName }.toSet()
        } catch (e: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun refreshForegroundFromWindow() {
        try {
            val root = rootInActiveWindow ?: return
            val pkg = root.packageName?.toString()
            root.recycle()
            if (pkg.isNullOrEmpty() || pkg == "com.android.systemui" || isKeyboardPackage(pkg)) return
            currentForegroundPackage = pkg
        } catch (e: Exception) {
        }
    }

    private fun isKeyboardPackage(pkg: String): Boolean {
        return imePackages.contains(pkg) ||
                pkg.contains("inputmethod", ignoreCase = true) ||
                pkg.contains("keyboard", ignoreCase = true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return

            if (pkg == "com.android.systemui") return
            // Keyboard windows must not replace the foreground app (otherwise typing pauses tracking)
            if (isKeyboardPackage(pkg)) return

            currentForegroundPackage = pkg

            if (excludedApps.contains(pkg)) return

            if (blockedSocialApps.contains(pkg)) {
                enforceSocialRules(isTick = false)
            }
        }
    }

    private fun enforceSocialRules(isTick: Boolean) {
        val vaultPrefs = getSharedPreferences("StudyVaultPrefs", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        val bingeLockEnd = vaultPrefs.getLong("BINGE_LOCK_END_TIME", 0L)
        if (now < bingeLockEnd) {
            val minsLeft = maxOf(1L, (bingeLockEnd - now) / 60000L)
            showThrottledToast("⛔ تجاوزت 3 ساعات سوشيال! استراحة إجبارية متبقي منها $minsLeft دقيقة")
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }

        val penaltyEndTime = vaultPrefs.getLong("PENALTY_END_TIME", 0L)
        if (now < penaltyEndTime) {
            val minsLeft = maxOf(1L, (penaltyEndTime - now) / 60000L)
            showThrottledToast("⛔ عقوبة نشطة! ممنوع فيسبوك وإنستغرام ويوتيوب لـ $minsLeft دقيقة")
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }

        if (StudySessionService.isStudying && !StudySessionService.isPaused) {
            showThrottledToast("🔒 أنت في جلسة مذاكرة حالياً! السوشيال مقفول تماماً")
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }

        val isAppActive = vaultPrefs.getBoolean("IS_APP_ACTIVE", false)

        if (!isAppActive) {
            if (isTick) {
                trackThreeHourSafetyLimit(vaultPrefs)
            }
            return
        }

        val isSpendingFlames = vaultPrefs.getBoolean("IS_SPENDING_FLAMES_MODE", false)
        if (!isSpendingFlames) {
            showThrottledToast("🔒 السوشيال مقفول بالكارت! افتح 'صرف الشعلات' أو المس الكارت")
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }

        if (gamificationManager.getSpendableFlames() <= 0L) {
            vaultPrefs.edit().putBoolean("IS_SPENDING_FLAMES_MODE", false).apply()
            showThrottledToast("🔥 رصيدك 0 شعلة! ذاكر لجمع شعلات (الدقيقة = 6 🔥)")
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }

        if (isTick) {
            if (trackThreeHourSafetyLimit(vaultPrefs)) return

            val secondsCount = vaultPrefs.getInt("SOCIAL_SECONDS_TICK", 0) + 1
            if (secondsCount >= 10) {
                vaultPrefs.edit().putInt("SOCIAL_SECONDS_TICK", 0).apply()
                gamificationManager.spendFlames(1L)

                if (gamificationManager.getSpendableFlames() <= 0L) {
                    vaultPrefs.edit().putBoolean("IS_SPENDING_FLAMES_MODE", false).apply()
                    showThrottledToast("🔥 نفد رصيد الشعلات! تم إعادة قفل السوشيال")
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            } else {
                vaultPrefs.edit().putInt("SOCIAL_SECONDS_TICK", secondsCount).apply()
            }
        }
    }

    private fun trackThreeHourSafetyLimit(prefs: SharedPreferences): Boolean {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
        val savedDate = prefs.getString("BINGE_DATE", "")

        var usedSeconds = if (savedDate == today) {
            prefs.getLong("BINGE_SOCIAL_SECONDS", 0L)
        } else {
            0L
        }

        usedSeconds++

        if (usedSeconds >= 10800L) {
            val oneHourMillis = 60L * 60L * 1000L
            prefs.edit()
                .putString("BINGE_DATE", today)
                .putLong("BINGE_SOCIAL_SECONDS", 0L)
                .putLong("BINGE_LOCK_END_TIME", System.currentTimeMillis() + oneHourMillis)
                .putBoolean("IS_SPENDING_FLAMES_MODE", false)
                .apply()

            showThrottledToast("⛔ قعدت 3 ساعات على السوشيال! مقفول إجبارياً لمدة ساعة")
            performGlobalAction(GLOBAL_ACTION_HOME)
            return true
        } else {
            prefs.edit()
                .putString("BINGE_DATE", today)
                .putLong("BINGE_SOCIAL_SECONDS", usedSeconds)
                .apply()
            return false
        }
    }

    private fun showThrottledToast(msg: String) {
        val now = System.currentTimeMillis()
        if (now - lastToastTime > 3000L) {
            lastToastTime = now
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(monitorRunnable)
    }
}
