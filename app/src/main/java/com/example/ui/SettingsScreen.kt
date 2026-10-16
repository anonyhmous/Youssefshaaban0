package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.IndicatorSize
import com.example.data.UserSettings
import com.example.domain.DistanceMode
import com.example.domain.JudgeBasis
import com.example.reading.NumberParser
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    onUpdateMinPerKm: (BigDecimal) -> Unit,
    onUpdateTolerance: (Int) -> Unit,
    onUpdateDistanceMode: (DistanceMode) -> Unit,
    onUpdateJudgeBy: (JudgeBasis) -> Unit,
    onUpdateDeductCommission: (Boolean) -> Unit,
    onUpdateCommissionPercent: (Int) -> Unit,
    onUpdateDoubleCheckOcr: (Boolean) -> Unit,
    onUpdateShowSuggestedChips: (Boolean) -> Unit,
    onUpdateShowCardBadges: (Boolean) -> Unit,
    onUpdateIndicatorSize: (IndicatorSize) -> Unit,
    onResetPosition: () -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()

    var minPriceInput by remember(settings.myMinimumPerKm) {
        mutableStateOf(settings.myMinimumPerKm.toPlainString())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إعدادات الحساب والمؤشر", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "الرجوع"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: الحساب (Calculations)
            Text(
                text = "إعدادات الحساب",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Minimum EGP / km
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("حدي الأدنى لسعر الكيلومتر (ج/كم)", fontWeight = FontWeight.SemiBold)
                    Text("يقبل الأرقام العربية والإنجليزية", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = minPriceInput,
                        onValueChange = { input ->
                            minPriceInput = input
                            val normalized = NumberParser.normalize(input)
                            val parsed = try {
                                BigDecimal(normalized)
                            } catch (_: Exception) {
                                null
                            }
                            if (parsed != null && parsed > BigDecimal.ZERO) {
                                onUpdateMinPerKm(parsed)
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("min_price_input")
                    )
                }
            }

            // Tolerance % (Orange)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("نسبة التسامح للون البرتقالي", fontWeight = FontWeight.SemiBold)
                        Text("${settings.tolerancePercent}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("الحد الأدنى للقبول المشروط قبل التحول للون الأحمر", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = settings.tolerancePercent.toFloat(),
                        onValueChange = { onUpdateTolerance(it.toInt()) },
                        valueRange = 0f..30f,
                        steps = 5,
                        modifier = Modifier.testTag("tolerance_slider")
                    )
                }
            }

            // Distance Mode
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("طريقة احتساب المسافة", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = settings.distanceMode == DistanceMode.PICKUP_AND_TRIP,
                            onClick = { onUpdateDistanceMode(DistanceMode.PICKUP_AND_TRIP) },
                            label = { Text("التقاط + رحلة (شامل)") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = settings.distanceMode == DistanceMode.TRIP_ONLY,
                            onClick = { onUpdateDistanceMode(DistanceMode.TRIP_ONLY) },
                            label = { Text("مسافة الرحلة فقط") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Deduct Commission & Commission %
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("خصم عمولة التطبيق", fontWeight = FontWeight.SemiBold)
                            Text("لحساب الصافي الذي تستلمه في جيبك", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.deductCommission,
                            onCheckedChange = onUpdateDeductCommission,
                            modifier = Modifier.testTag("deduct_commission_switch")
                        )
                    }

                    if (settings.deductCommission) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("نسبة العمولة", fontSize = 14.sp)
                            Text("${settings.commissionPercent}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = settings.commissionPercent.toFloat(),
                            onValueChange = { onUpdateCommissionPercent(it.toInt()) },
                            valueRange = 0f..50f,
                            steps = 9
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("التقييم واللون يعتمدان على:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = settings.judgeBy == JudgeBasis.GROSS,
                                onClick = { onUpdateJudgeBy(JudgeBasis.GROSS) },
                                label = { Text("الإجمالي (قبل العمولة)") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = settings.judgeBy == JudgeBasis.NET,
                                onClick = { onUpdateJudgeBy(JudgeBasis.NET) },
                                label = { Text("الصافي (بعد العمولة)") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Section 2: خيارات العرض والقراءة
            Text(
                text = "خيارات العرض والقراءة",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Double check with OCR switch
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("التحقق الإضافي بواسطة OCR", fontWeight = FontWeight.SemiBold)
                        Text("مطابقة قراءة شجرة النصوص مع قراءة الصورة البصرية", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = settings.doubleCheckWithOcr,
                        onCheckedChange = onUpdateDoubleCheckOcr,
                        modifier = Modifier.testTag("ocr_switch")
                    )
                }
            }

            // Show suggested chips
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("عرض تقييم الأسعار المقترحة", fontWeight = FontWeight.SemiBold)
                        Text("ملصق سعر الكيلومتر فوق أزرار زيادة السعر", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = settings.showSuggestedChips,
                        onCheckedChange = onUpdateShowSuggestedChips
                    )
                }
            }

            // Show card badges in feed
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("شارات تقييم البطاقات المتعددة", fontWeight = FontWeight.SemiBold)
                        Text("شارة صغيرة لكل طلب معروض في قائمة الطلبات", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = settings.showCardBadges,
                        onCheckedChange = onUpdateShowCardBadges
                    )
                }
            }

            // Indicator Size
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("حجم المؤشر العائم", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IndicatorSize.values().forEach { size ->
                            val label = when (size) {
                                IndicatorSize.SMALL -> "صغير"
                                IndicatorSize.MEDIUM -> "متوسط"
                                IndicatorSize.LARGE -> "كبير"
                            }
                            FilterChip(
                                selected = settings.indicatorSize == size,
                                onClick = { onUpdateIndicatorSize(size) },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Reset Position Button
            Button(
                onClick = onResetPosition,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reset_position_button")
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(8.dp))
                Text("إعادة تعيين موضع المؤشر إلى الافتراضي", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
