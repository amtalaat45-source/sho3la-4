package com.example.studyvault

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var statsManager: StatsManager
    private lateinit var gamificationManager: GamificationManager

    private lateinit var profileRankText: TextView
    private lateinit var flamesCountText: TextView
    private lateinit var levelProgressBar: ProgressBar
    private lateinit var nextLevelText: TextView
    private lateinit var streakInfoText: TextView
    private lateinit var nfcStateText: TextView
    private lateinit var statusText: TextView
    private lateinit var timerText: TextView
    private lateinit var rewardText: TextView
    private lateinit var bingeStatsText: TextView
    private lateinit var btnStartEndSession: Button
    private lateinit var btnPauseResume: Button
    private lateinit var btnSpendFlames: Button
    private lateinit var btnAccessibility: Button
    private lateinit var btnResetNfc: Button
    private lateinit var chartTitleText: TextView
    private lateinit var btnWeeklyChart: Button
    private lateinit var btnMonthlyChart: Button
    private lateinit var weeklyChart: WeeklyBarChartView
    private lateinit var todayStatsText: TextView
    private lateinit var weekStatsText: TextView
    private lateinit var monthStatsText: TextView
    private lateinit var yearStatsText: TextView
    private lateinit var recordsText: TextView
    private lateinit var badgesText: TextView

    private var isShowingMonthlyChart = false
    private val uiHandler = Handler(Looper.getMainLooper())

    private val uiRefreshRunnable = object : Runnable {
        override fun run() {
            refreshAllUI()
            uiHandler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        statsManager = StatsManager(this)
        gamificationManager = GamificationManager(this)

        bindViews()
        requestRequiredPermissions()
        setupButtons()
        refreshAllUI()

        if (savedInstanceState == null) handleNfcIntent(intent)
    }

    private fun bindViews() {
        profileRankText = findViewById(R.id.profileRankText)
        flamesCountText = findViewById(R.id.flamesCountText)
        levelProgressBar = findViewById(R.id.levelProgressBar)
        nextLevelText = findViewById(R.id.nextLevelText)
        streakInfoText = findViewById(R.id.streakInfoText)
        nfcStateText = findViewById(R.id.nfcStateText)
        statusText = findViewById(R.id.statusText)
        timerText = findViewById(R.id.timerText)
        rewardText = findViewById(R.id.rewardText)
        bingeStatsText = findViewById(R.id.bingeStatsText)
        btnStartEndSession = findViewById(R.id.btnStartEndSession)
        btnPauseResume = findViewById(R.id.btnPauseResume)
        btnSpendFlames = findViewById(R.id.btnSpendFlames)
        btnAccessibility = findViewById(R.id.btnAccessibility)
        btnResetNfc = findViewById(R.id.btnResetNfc)
        chartTitleText = findViewById(R.id.chartTitleText)
        btnWeeklyChart = findViewById(R.id.btnWeeklyChart)
        btnMonthlyChart = findViewById(R.id.btnMonthlyChart)
        weeklyChart = findViewById(R.id.weeklyChart)
        todayStatsText = findViewById(R.id.todayStatsText)
        weekStatsText = findViewById(R.id.weekStatsText)
        monthStatsText = findViewById(R.id.monthStatsText)
        yearStatsText = findViewById(R.id.yearStatsText)
        recordsText = findViewById(R.id.recordsText)
        badgesText = findViewById(R.id.badgesText)
    }

    private fun requestRequiredPermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.READ_PHONE_STATE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 101)
        }
    }

    private fun setupButtons() {
        btnStartEndSession.setOnClickListener {
            val serviceIntent = Intent(this, StudySessionService::class.java)
            if (!StudySessionService.isStudying) {
                ContextCompat.startForegroundService(this, serviceIntent)
                Toast.makeText(this, "🔥 بدأ العد! ضع الموبايل ثابتاً على المكتب", Toast.LENGTH_SHORT).show()
            } else {
                val mins = StudySessionService.studySeconds / 60L
                stopService(serviceIntent)
                StudySessionService.statusMessage = "تم إنهاء الجلسة وحفظ $mins دقيقة ✅"
                Toast.makeText(this, "تم حفظ الدقائق والشعلات بنجاح", Toast.LENGTH_SHORT).show()
            }
            refreshAllUI()
        }

        btnPauseResume.setOnClickListener {
            if (!StudySessionService.isStudying) {
                Toast.makeText(this, "اضغط 'ابدأ احسب' أولاً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            StudySessionService.isPaused = !StudySessionService.isPaused
            if (!StudySessionService.isPaused) {
                StudySessionService.isPhoneLifted = true
                StudySessionService.graceSecondsLeft = 120
                StudySessionService.resetSensorAnchor = true
                StudySessionService.statusMessage = "تم الاستئناف! ضع الهاتف ثابتاً على المكتب"
            }
            refreshAllUI()
        }

        btnSpendFlames.setOnClickListener {
            val prefs = getSharedPreferences("StudyVaultPrefs", Context.MODE_PRIVATE)
            val isAppActive = prefs.getBoolean("IS_APP_ACTIVE", false)

            if (!isAppActive) {
                Toast.makeText(this, "البرنامج مقفول بالكارت أصلاً والسوشيال مفتوح", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (StudySessionService.isStudying && !StudySessionService.isPaused) {
                Toast.makeText(this, "أوقف أو أنهِ جلسة المذاكرة أولاً لصرف الشعلات", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val currentlySpending = prefs.getBoolean("IS_SPENDING_FLAMES_MODE", false)
            if (!currentlySpending) {
                if (gamificationManager.getSpendableFlames() <= 0L) {
                    Toast.makeText(this, "رصيدك 0 شعلة! ذاكر لجمع شعلات أولاً", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                prefs.edit().putBoolean("IS_SPENDING_FLAMES_MODE", true).apply()
                Toast.makeText(this, "🔥 تم فتح السوشيال بالشعلات (الدقيقة = 6 🔥)", Toast.LENGTH_SHORT).show()
            } else {
                prefs.edit().putBoolean("IS_SPENDING_FLAMES_MODE", false).apply()
                Toast.makeText(this, "🔒 تم إعادة قفل السوشيال والحفاظ على شعلاتك", Toast.LENGTH_SHORT).show()
            }
            refreshAllUI()
        }

        btnAccessibility.setOnClickListener {
            if (isAccessibilityEnabled()) {
                Toast.makeText(this, "الخدمة مفعّلة بالفعل ✅ (لو غيّرت التطبيق: أطفئها وشغّلها مرة أخرى)", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "فعّل تطبيق 'شعلة' من قائمة التطبيقات المثبتة", Toast.LENGTH_LONG).show()
            }
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        nfcStateText.setOnClickListener {
            if (nfcAdapter?.isEnabled == false) {
                try { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
                catch (e: Exception) { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
            }
        }

        btnResetNfc.setOnClickListener {
            val prefs = getSharedPreferences("StudyVaultPrefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("IS_APP_ACTIVE", false) || StudySessionService.isStudying) {
                Toast.makeText(this, "المس الكارت القديم لقفل البرنامج أولاً قبل تغييره!", Toast.LENGTH_LONG).show()
            } else {
                prefs.edit().remove("SAVED_NFC_UID").apply()
                Toast.makeText(this, "تم مسح الكارت القديم. المس الكارت الجديد لتسجيله", Toast.LENGTH_SHORT).show()
                refreshAllUI()
            }
        }

        btnWeeklyChart.setOnClickListener {
            isShowingMonthlyChart = false
            btnWeeklyChart.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF3D00"))
            btnMonthlyChart.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#221918"))
            chartTitleText.text = "الرسم البياني (أسبوعي)"
            refreshChart()
        }

        btnMonthlyChart.setOnClickListener {
            isShowingMonthlyChart = true
            btnMonthlyChart.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF3D00"))
            btnWeeklyChart.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#221918"))
            chartTitleText.text = "الرسم البياني (هذا الشهر)"
            refreshChart()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNfcIntent(intent)
    }

    private fun handleNfcIntent(intent: Intent?) {
        if (intent?.action == NfcAdapter.ACTION_TAG_DISCOVERED ||
            intent?.action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
            intent?.action == NfcAdapter.ACTION_TECH_DISCOVERED
        ) {
            val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            }

            intent.action = Intent.ACTION_MAIN // consume the NFC intent so it is never processed twice

            if (tag != null) handleTag(tag)
        }
    }

    private fun handleTag(tag: Tag) {
        val scannedUid = tag.id.joinToString(":") { byte -> "%02X".format(byte) }
        val prefs = getSharedPreferences("StudyVaultPrefs", Context.MODE_PRIVATE)
        val savedUid = prefs.getString("SAVED_NFC_UID", null)

        if (savedUid == null) {
            prefs.edit().putString("SAVED_NFC_UID", scannedUid).apply()
            Toast.makeText(this, "تم تسجيل الكارت! المسه مرة أخرى لقفل السوشيال", Toast.LENGTH_LONG).show()
            refreshAllUI()
            return
        }

        if (scannedUid == savedUid) {
            val isCurrentlyActive = prefs.getBoolean("IS_APP_ACTIVE", false)
            if (!isCurrentlyActive) {
                prefs.edit()
                    .putBoolean("IS_APP_ACTIVE", true)
                    .putBoolean("IS_SPENDING_FLAMES_MODE", false)
                    .apply()
                StudySessionService.statusMessage = "تم قفل السوشيال بالكارت 🔒 اضغط 'ابدأ احسب' عندما تبدأ المذاكرة"
                Toast.makeText(this, "🔒 تم تفعيل قفل السوشيال بالكارت!", Toast.LENGTH_SHORT).show()
            } else {
                prefs.edit()
                    .putBoolean("IS_APP_ACTIVE", false)
                    .putBoolean("IS_SPENDING_FLAMES_MODE", false)
                    .apply()
                if (StudySessionService.isStudying) {
                    stopService(Intent(this, StudySessionService::class.java))
                }
                StudySessionService.statusMessage = "تم إيقاف البرنامج بالكارت 🔓 (حد الأمان 3 ساعات ما زال نشطاً)"
                Toast.makeText(this, "🔓 تم فك قفل السوشيال بالكارت", Toast.LENGTH_SHORT).show()
            }
            refreshAllUI()
        } else {
            Toast.makeText(this, "كارت غير مسجل! استخدم كارت الغرفة المسجل.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshAllUI() {
        val prefs = getSharedPreferences("StudyVaultPrefs", Context.MODE_PRIVATE)
        val isAppActive = prefs.getBoolean("IS_APP_ACTIVE", false)
        val isSpendingFlames = prefs.getBoolean("IS_SPENDING_FLAMES_MODE", false)
        val now = System.currentTimeMillis()
        val penaltyEnd = prefs.getLong("PENALTY_END_TIME", 0L)
        val bingeLockEnd = prefs.getLong("BINGE_LOCK_END_TIME", 0L)

        nfcStateText.text = when {
            nfcProblemText() != null -> nfcProblemText()
            now < bingeLockEnd -> "⛔ استراحة إجبارية بعد 3 ساعات سوشيال (متبقي ${(bingeLockEnd - now) / 60000L + 1} د)"
            now < penaltyEnd -> "⛔ عقوبة رفع الموبايل نشطة (متبقي ${(penaltyEnd - now) / 60000L + 1} د)"
            isAppActive && isSpendingFlames -> "🔥 وضع صرف الشعلات مفتوح (يخصم 6 🔥 كل دقيقة)"
            isAppActive -> "🔒 البرنامج شغال بالكارت (فيسبوك وإنستا ويوتيوب مقفولين)"
            else -> "🔓 البرنامج مقفول بالكارت (السوشيال مفتوح بحد أقصى 3 ساعات)"
        }

        val mins = StudySessionService.studySeconds / 60L
        val secs = StudySessionService.studySeconds % 60L
        timerText.text = String.format(Locale.US, "%02d:%02d", mins, secs)
        statusText.text = StudySessionService.statusMessage

        if (StudySessionService.isStudying) {
            btnStartEndSession.text = "إنهاء الجلسة"
            btnStartEndSession.setBackgroundResource(R.drawable.bg_danger_button)
        } else {
            btnStartEndSession.text = "ابدأ احسب"
            btnStartEndSession.setBackgroundResource(R.drawable.bg_flame_button)
        }
        btnPauseResume.text = if (StudySessionService.isPaused) "استئناف" else "إيقاف مؤقت"
        btnSpendFlames.text = if (isSpendingFlames) {
            "🔒 إيقاف صرف الشعلات وقفل السوشيال"
        } else {
            "🔥 فتح السوشيال بالشعلات (6 🔥 / دقيقة)"
        }

        val availableMins = gamificationManager.getAvailableSocialMinutes()
        rewardText.text = "متاح: $availableMins د سوشيال (الواتساب مجاني)"

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
        val bingeSecs = if (prefs.getString("BINGE_DATE", "") == today) prefs.getLong("BINGE_SOCIAL_SECONDS", 0L) else 0L
        bingeStatsText.text = "سوشيال اليوم: ${bingeSecs / 60L} / 180 دقيقة (يقفل ساعة عند 3 ساعات)"

        todayStatsText.text = formatMinutes(statsManager.getTodayMinutes())
        weekStatsText.text = formatMinutes(statsManager.getWeekMinutes())
        monthStatsText.text = formatMinutes(statsManager.getMonthMinutes())
        yearStatsText.text = formatMinutes(statsManager.getYearMinutes())

        val profile = gamificationManager.getProfileStatus()
        val streak = gamificationManager.getValidCurrentStreak()
        val rate = gamificationManager.getFlamesPerFiveMinutes()

        profileRankText.text = "${profile.rankEmoji} المستوى ${profile.level} • ${profile.rankTitle}"
        flamesCountText.text = "🔥 ${profile.spendableFlames}"
        levelProgressBar.progress = profile.progressPercent
        nextLevelText.text = "XP: ${profile.totalXp} • متبقي ${profile.xpToNextLevel} للمستوى ${profile.level + 1}"
        streakInfoText.text = "ستريك: $streak يوم ($rate🔥/5د)"

        recordsText.text = "أطول جلسة: ${gamificationManager.getBestSessionMins()} د | " +
                "أعلى يوم: ${gamificationManager.getBestDayMins()} د | " +
                "أطول ستريك: ${gamificationManager.getBestStreakDays()} يوم"

        val badges = gamificationManager.getUnlockedBadges()
        badgesText.text = if (badges.isEmpty()) {
            "الجوائز المفتوحة: ابدأ المذاكرة لفتح أول جائزة!"
        } else {
            "الجوائز المفتوحة:\n" + badges.joinToString("\n• ", prefix = "• ")
        }

        val a11yOn = isAccessibilityEnabled()
        btnAccessibility.text = if (a11yOn) "✅ قفل السوشيال مفعّل" else "تفعيل قفل السوشيال"
        btnAccessibility.backgroundTintList =
            ColorStateList.valueOf(Color.parseColor(if (a11yOn) "#2E7D32" else "#B71C1C"))

        refreshChart()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val full = "$packageName/${SocialBlockerService::class.java.name}"
        val short = "$packageName/.SocialBlockerService"
        return enabled.split(':').any { it.equals(full, true) || it.equals(short, true) }
    }

    private fun refreshChart() {
        if (isShowingMonthlyChart) {
            weeklyChart.setData(statsManager.getCurrentMonthChartData(), isMonthly = true)
        } else {
            weeklyChart.setData(statsManager.getLast7DaysChartData(), isMonthly = false)
        }
    }

    private fun formatMinutes(totalMins: Long): String {
        val hours = totalMins / 60L
        val mins = totalMins % 60L
        return if (hours > 0) "${hours}س ${mins}د" else "${mins} د"
    }

    override fun onResume() {
        super.onResume()
        uiHandler.post(uiRefreshRunnable)
        enableNfcReading()
    }

    /**
     * Reader mode talks to the NFC chip directly and skips the system tag dispatch,
     * which on Xiaomi/HyperOS often swallows tags (NDEF / MIFARE) before the app sees them.
     */
    private fun enableNfcReading() {
        val adapter = nfcAdapter
        if (adapter == null || !adapter.isEnabled) return
        val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NFC_BARCODE or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        try {
            adapter.enableReaderMode(this, { tag -> runOnUiThread { handleTag(tag) } }, flags, null)
        } catch (e: Exception) {
            // Fallback: classic foreground dispatch
            val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            adapter.enableForegroundDispatch(this, PendingIntent.getActivity(this, 0, intent, piFlags), null, null)
        }
    }

    private fun nfcProblemText(): String? = when {
        nfcAdapter == null -> "⚠️ الموبايل ده مفيهوش NFC (أو مش متاح في منطقتك)"
        nfcAdapter?.isEnabled == false -> "⚠️ الـ NFC مقفول! اضغط هنا وافتحه من الإعدادات"
        else -> null
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(uiRefreshRunnable)
        try { nfcAdapter?.disableReaderMode(this) } catch (e: Exception) {}
        try { nfcAdapter?.disableForegroundDispatch(this) } catch (e: Exception) {}
    }
}
