package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import com.example.data.UserSettings
import com.example.domain.CoreMath
import com.example.domain.CoreMathResult
import com.example.domain.IndicatorColor
import com.example.reading.NumberParser
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    settings: UserSettings,
    onBack: () -> Unit
) {
    var priceText by remember { mutableStateOf("55") }
    var pickupText by remember { mutableStateOf("3.5") }
    var tripText by remember { mutableStateOf("15.7") }
    var chip1Text by remember { mutableStateOf("58") }
    var chip2Text by remember { mutableStateOf("61") }
    var chip3Text by remember { mutableStateOf("66") }

    val calculationResult by remember(
        priceText,
        pickupText,
        tripText,
        chip1Text,
        chip2Text,
        chip3Text,
        settings
    ) {
        derivedStateOf {
            val pNorm = NumberParser.normalize(priceText)
            val pickNorm = NumberParser.normalize(pickupText)
            val tripNorm = NumberParser.normalize(tripText)

            val chips = listOfNotNull(
                parseChip(chip1Text),
                parseChip(chip2Text),
                parseChip(chip3Text)
            )

            CoreMath.calculate(
                priceStr = pNorm,
                pickupKmStr = pickNorm,
                tripKmStr = tripNorm,
                myMinimumPerKm = settings.myMinimumPerKm,
                tolerancePercent = settings.tolerancePercent,
                distanceMode = settings.distanceMode,
                deductCommission = settings.deductCommission,
                commissionPercent = settings.commissionPercent,
                judgeBy = settings.judgeBy,
                suggestedPrices = chips
            )
        }
    }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("حاسبة سعر الكيلومتر المباشرة", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("calc_back_button")) {
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
            // Live Result Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when (val res = calculationResult) {
                        is CoreMathResult.Success -> getCardColor(res.color)
                        else -> Color(0xFF334155)
                    }
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("calc_result_card")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (val res = calculationResult) {
                        is CoreMathResult.Success -> {
                            Text(
                                text = res.displayText,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )

                            if (res.otherPerKm != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (settings.judgeBy == com.example.domain.JudgeBasis.NET)
                                        "الإجمالي: ${res.otherPerKm} ج/كم"
                                    else
                                        "الصافي: ${res.otherPerKm} ج/كم",
                                    fontSize = 16.sp,
                                    color = Color(0xFFE2E8F0)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "أقل سعر مقبول: ${res.minAcceptablePrice} ج",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFEF08A)
                            )
                            Text(
                                text = "إجمالي المسافة: ${res.totalKm} كم",
                                fontSize = 14.sp,
                                color = Color(0xFFCBD5E1)
                            )

                            if (res.chips.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    res.chips.forEach { chip ->
                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 4.dp)
                                                .background(
                                                    getCardColor(chip.color),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "${chip.price.toInt()}ج ➔ ${chip.perKm}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        is CoreMathResult.Suspicious -> {
                            Text(
                                text = res.reason,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFCA5A5)
                            )
                        }
                        is CoreMathResult.Missing -> {
                            Text(
                                text = res.reason,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
            }

            // Input fields
            Text("بيانات الطلب", fontSize = 16.sp, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it },
                label = { Text("السعر المعروض (جنيه)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("calc_price_input")
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = pickupText,
                    onValueChange = { pickupText = it },
                    label = { Text("مسافة الالتقاط (كم)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("calc_pickup_input")
                )

                OutlinedTextField(
                    value = tripText,
                    onValueChange = { tripText = it },
                    label = { Text("مسافة الرحلة (كم)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("calc_trip_input")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("الأسعار المقترحة (الشرائح)", fontSize = 16.sp, fontWeight = FontWeight.Bold)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = chip1Text,
                    onValueChange = { chip1Text = it },
                    label = { Text("شريحة 1") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = chip2Text,
                    onValueChange = { chip2Text = it },
                    label = { Text("شريحة 2") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = chip3Text,
                    onValueChange = { chip3Text = it },
                    label = { Text("شريحة 3") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun parseChip(input: String): BigDecimal? {
    val norm = NumberParser.normalize(input)
    return try {
        val bd = BigDecimal(norm)
        if (bd > BigDecimal.ZERO) bd else null
    } catch (_: Exception) {
        null
    }
}

private fun getCardColor(color: IndicatorColor): Color {
    return when (color) {
        IndicatorColor.GREEN -> Color(0xFF00C853)
        IndicatorColor.ORANGE -> Color(0xFFFF9100)
        IndicatorColor.RED -> Color(0xFFD50000)
        IndicatorColor.GREY -> Color(0xFF616161)
    }
}
