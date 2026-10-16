package com.example.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.SettingsRepository
import kotlinx.coroutines.launch

enum class AppScreen {
    HOME,
    SETTINGS,
    CALCULATOR
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsRepo = SettingsRepository.getInstance(applicationContext)

        setContent {
            val scope = rememberCoroutineScope()
            val userSettings by settingsRepo.settingsFlow.collectAsStateWithLifecycle()
            var currentScreen by remember { mutableStateOf(AppScreen.HOME) }

            // Predictive back handling for custom screen stack
            if (currentScreen != AppScreen.HOME) {
                BackHandler {
                    currentScreen = AppScreen.HOME
                }
            }

            // Arabic-first RTL enforcement
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                CaptainPersonalTheme {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                    ) {
                        when (currentScreen) {
                            AppScreen.HOME -> HomeScreen(
                                settings = userSettings,
                                onUpdateMonitoring = { active ->
                                    scope.launch { settingsRepo.updateMonitoring(active) }
                                },
                                onNavigateToSettings = { currentScreen = AppScreen.SETTINGS },
                                onNavigateToCalculator = { currentScreen = AppScreen.CALCULATOR }
                            )

                            AppScreen.SETTINGS -> SettingsScreen(
                                settings = userSettings,
                                onUpdateMinPerKm = { minVal ->
                                    scope.launch { settingsRepo.updateMinPerKm(minVal) }
                                },
                                onUpdateTolerance = { tol ->
                                    scope.launch { settingsRepo.updateTolerance(tol) }
                                },
                                onUpdateDistanceMode = { mode ->
                                    scope.launch { settingsRepo.updateDistanceMode(mode) }
                                },
                                onUpdateJudgeBy = { judge ->
                                    scope.launch { settingsRepo.updateJudgeBy(judge) }
                                },
                                onUpdateDeductCommission = { deduct ->
                                    scope.launch { settingsRepo.updateDeductCommission(deduct) }
                                },
                                onUpdateCommissionPercent = { pct ->
                                    scope.launch { settingsRepo.updateCommissionPercent(pct) }
                                },
                                onUpdateDoubleCheckOcr = { ocr ->
                                    scope.launch { settingsRepo.updateDoubleCheckOcr(ocr) }
                                },
                                onUpdateShowSuggestedChips = { chips ->
                                    scope.launch { settingsRepo.updateShowSuggestedChips(chips) }
                                },
                                onUpdateShowCardBadges = { badges ->
                                    scope.launch { settingsRepo.updateShowCardBadges(badges) }
                                },
                                onUpdateIndicatorSize = { size ->
                                    scope.launch { settingsRepo.updateIndicatorSize(size) }
                                },
                                onResetPosition = {
                                    scope.launch { settingsRepo.resetIndicatorPosition() }
                                },
                                onBack = { currentScreen = AppScreen.HOME }
                            )

                            AppScreen.CALCULATOR -> CalculatorScreen(
                                settings = userSettings,
                                onBack = { currentScreen = AppScreen.HOME }
                            )
                        }
                    }
                }
            }
        }
    }
}
