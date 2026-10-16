package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.DistanceMode
import com.example.domain.JudgeBasis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "captain_settings")

enum class IndicatorSize {
    SMALL,
    MEDIUM,
    LARGE
}

data class UserSettings(
    val myMinimumPerKm: BigDecimal = BigDecimal("3.0"),
    val tolerancePercent: Int = 10,
    val distanceMode: DistanceMode = DistanceMode.PICKUP_AND_TRIP,
    val judgeBy: JudgeBasis = JudgeBasis.GROSS,
    val deductCommission: Boolean = false,
    val commissionPercent: Int = 10,
    val doubleCheckWithOcr: Boolean = false,
    val showSuggestedChips: Boolean = true,
    val showCardBadges: Boolean = true,
    val indicatorSize: IndicatorSize = IndicatorSize.MEDIUM,
    val isMonitoringActive: Boolean = true,
    val savedIndicatorX: Int = -1,
    val savedIndicatorY: Int = -1
)

class SettingsRepository(private val context: Context) {

    private val KEY_MIN_PER_KM = stringPreferencesKey("min_per_km")
    private val KEY_TOLERANCE = intPreferencesKey("tolerance_percent")
    private val KEY_DISTANCE_MODE = stringPreferencesKey("distance_mode")
    private val KEY_JUDGE_BY = stringPreferencesKey("judge_by")
    private val KEY_DEDUCT_COMMISSION = booleanPreferencesKey("deduct_commission")
    private val KEY_COMMISSION_PERCENT = intPreferencesKey("commission_percent")
    private val KEY_DOUBLE_CHECK_OCR = booleanPreferencesKey("double_check_ocr")
    private val KEY_SHOW_SUGGESTED_CHIPS = booleanPreferencesKey("show_suggested_chips")
    private val KEY_SHOW_CARD_BADGES = booleanPreferencesKey("show_card_badges")
    private val KEY_INDICATOR_SIZE = stringPreferencesKey("indicator_size")
    private val KEY_MONITORING_ACTIVE = booleanPreferencesKey("monitoring_active")
    private val KEY_SAVED_X = intPreferencesKey("saved_x")
    private val KEY_SAVED_Y = intPreferencesKey("saved_y")

    private val scope = CoroutineScope(Dispatchers.IO)

    val settingsFlow: StateFlow<UserSettings> = context.dataStore.data.map { pref ->
        UserSettings(
            myMinimumPerKm = pref[KEY_MIN_PER_KM]?.let { safeBigDecimal(it) } ?: BigDecimal("3.0"),
            tolerancePercent = pref[KEY_TOLERANCE] ?: 10,
            distanceMode = try {
                DistanceMode.valueOf(pref[KEY_DISTANCE_MODE] ?: DistanceMode.PICKUP_AND_TRIP.name)
            } catch (_: Exception) {
                DistanceMode.PICKUP_AND_TRIP
            },
            judgeBy = try {
                JudgeBasis.valueOf(pref[KEY_JUDGE_BY] ?: JudgeBasis.GROSS.name)
            } catch (_: Exception) {
                JudgeBasis.GROSS
            },
            deductCommission = pref[KEY_DEDUCT_COMMISSION] ?: false,
            commissionPercent = pref[KEY_COMMISSION_PERCENT] ?: 10,
            doubleCheckWithOcr = pref[KEY_DOUBLE_CHECK_OCR] ?: false,
            showSuggestedChips = pref[KEY_SHOW_SUGGESTED_CHIPS] ?: true,
            showCardBadges = pref[KEY_SHOW_CARD_BADGES] ?: true,
            indicatorSize = try {
                IndicatorSize.valueOf(pref[KEY_INDICATOR_SIZE] ?: IndicatorSize.MEDIUM.name)
            } catch (_: Exception) {
                IndicatorSize.MEDIUM
            },
            isMonitoringActive = pref[KEY_MONITORING_ACTIVE] ?: true,
            savedIndicatorX = pref[KEY_SAVED_X] ?: -1,
            savedIndicatorY = pref[KEY_SAVED_Y] ?: -1
        )
    }.stateIn(scope, SharingStarted.Eagerly, UserSettings())

    suspend fun updateMonitoring(active: Boolean) {
        context.dataStore.edit { it[KEY_MONITORING_ACTIVE] = active }
    }

    suspend fun updateMinPerKm(value: BigDecimal) {
        context.dataStore.edit { it[KEY_MIN_PER_KM] = value.toPlainString() }
    }

    suspend fun updateTolerance(tolerance: Int) {
        context.dataStore.edit { it[KEY_TOLERANCE] = tolerance.coerceIn(0, 30) }
    }

    suspend fun updateDistanceMode(mode: DistanceMode) {
        context.dataStore.edit { it[KEY_DISTANCE_MODE] = mode.name }
    }

    suspend fun updateJudgeBy(judgeBy: JudgeBasis) {
        context.dataStore.edit { it[KEY_JUDGE_BY] = judgeBy.name }
    }

    suspend fun updateDeductCommission(deduct: Boolean) {
        context.dataStore.edit { it[KEY_DEDUCT_COMMISSION] = deduct }
    }

    suspend fun updateCommissionPercent(percent: Int) {
        context.dataStore.edit { it[KEY_COMMISSION_PERCENT] = percent.coerceIn(0, 50) }
    }

    suspend fun updateDoubleCheckOcr(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DOUBLE_CHECK_OCR] = enabled }
    }

    suspend fun updateShowSuggestedChips(show: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_SUGGESTED_CHIPS] = show }
    }

    suspend fun updateShowCardBadges(show: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_CARD_BADGES] = show }
    }

    suspend fun updateIndicatorSize(size: IndicatorSize) {
        context.dataStore.edit { it[KEY_INDICATOR_SIZE] = size.name }
    }

    suspend fun saveIndicatorPosition(x: Int, y: Int) {
        context.dataStore.edit {
            it[KEY_SAVED_X] = x
            it[KEY_SAVED_Y] = y
        }
    }

    suspend fun resetIndicatorPosition() {
        context.dataStore.edit {
            it.remove(KEY_SAVED_X)
            it.remove(KEY_SAVED_Y)
        }
    }

    private fun safeBigDecimal(value: String): BigDecimal? {
        return try {
            BigDecimal(value)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        @Volatile
        private var instance: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
        }

        // Shared in-memory debug dump buffer for the "نسخ فحص الشاشة" feature
        @Volatile
        var latestScreenDump: String = "لم يتم فحص شاشة حتى الآن. تأكد من تفعيل الخدمة وفتح تطبيق إندرايف."
    }
}
