package com.ruggerocadamuro.myapplication.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ruggerocadamuro.myapplication.R
import com.ruggerocadamuro.myapplication.data.settings.AppLanguage
import com.ruggerocadamuro.myapplication.data.settings.AppSettings
import com.ruggerocadamuro.myapplication.data.settings.SpeedUnit
import com.ruggerocadamuro.myapplication.data.settings.TempUnit
import com.ruggerocadamuro.myapplication.data.settings.ThemeMode
import com.ruggerocadamuro.myapplication.ui.components.AccentColorChooser
import com.ruggerocadamuro.myapplication.ui.components.ChoiceCard
import com.ruggerocadamuro.myapplication.ui.components.GlassButton
import com.ruggerocadamuro.myapplication.ui.components.GlassCard
import com.ruggerocadamuro.myapplication.ui.components.GlassOutlineButton
import com.ruggerocadamuro.myapplication.ui.components.GlassSurface
import com.ruggerocadamuro.myapplication.ui.components.LanguageChooser
import com.ruggerocadamuro.myapplication.ui.components.NumericSetting
import com.ruggerocadamuro.myapplication.ui.components.rememberLanguageApplier
import com.ruggerocadamuro.myapplication.ui.settings.SettingsViewModel
import com.ruggerocadamuro.myapplication.ui.theme.AccentPalette

/** Repository pubblico del progetto: unico link mostrato nel ringraziamento. */
private const val GITHUB_URL = "https://github.com/RuggeroCadamuroITA/VescViewer"

/** Passaggi configurabili (il benvenuto non conta e non ha indicatore). */
private const val CONFIG_STEPS = 6

private const val STEP_WELCOME = 0
private const val STEP_LANGUAGE = 1
private const val STEP_THEME = 2
private const val STEP_UNITS = 3
private const val STEP_VEHICLE = 4
private const val STEP_ALERTS = 5
private const val STEP_SUMMARY = 6

/**
 * Setup guidato del primo avvio.
 *
 * E' volutamente obbligatorio: l'app non mostra la dashboard finche'
 * `setupCompleted` non e' true, non esiste un pulsante "salta" e ogni
 * passaggio ha una scelta da fare prima di poter proseguire (le uniche pagine
 * "libere" sono il benvenuto e il riepilogo).
 */
@Composable
fun SetupScreen(viewModel: SettingsViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val applyLanguage = rememberLanguageApplier { viewModel.setLanguage(it) }

    var step by rememberSaveable { mutableIntStateOf(STEP_WELCOME) }
    // Flag di scelta: servono a pretendere una selezione esplicita, cosa che il
    // valore persistito da solo non racconta (ha gia' un default).
    var themePicked by rememberSaveable { mutableStateOf(false) }
    var accentPicked by rememberSaveable { mutableStateOf(false) }
    var speedPicked by rememberSaveable { mutableStateOf(false) }
    var tempPicked by rememberSaveable { mutableStateOf(false) }
    var vehicleConfirmed by rememberSaveable { mutableStateOf(false) }
    var alertsConfirmed by rememberSaveable { mutableStateOf(false) }

    // Il tasto indietro torna al passaggio precedente, non esce dal setup.
    BackHandler(enabled = step > STEP_WELCOME) { step-- }

    val languagePicked = settings.language != null
    val canContinue = when (step) {
        STEP_LANGUAGE -> languagePicked
        STEP_THEME -> themePicked && accentPicked
        STEP_UNITS -> speedPicked && tempPicked
        STEP_VEHICLE -> vehicleConfirmed
        STEP_ALERTS -> alertsConfirmed
        else -> true
    }

    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent) { padding ->
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding)
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
            SetupBrandHeader(step)
            if (step > STEP_WELCOME) {
                SetupProgressHeader(step = step, total = CONFIG_STEPS)
                Spacer(Modifier.height(14.dp))
            }

            AnimatedContent(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                targetState = step,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220)) + slideInHorizontally { it / 8 }) togetherWith
                        fadeOut(animationSpec = tween(150))
                },
                label = "setup_step_transition"
            ) { currentStep ->
                Column(
                    modifier = Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (currentStep) {
                    STEP_WELCOME -> WelcomeStep()
                    STEP_LANGUAGE -> LanguageStep(
                        selected = settings.language,
                        onSelect = applyLanguage
                    )

                    STEP_THEME -> ThemeStep(
                        settings = settings,
                        themePicked = themePicked,
                        accentPicked = accentPicked,
                        onTheme = { viewModel.setThemeMode(it); themePicked = true },
                        onAccent = { viewModel.setAccentColor(it); accentPicked = true }
                    )

                    STEP_UNITS -> UnitsStep(
                        settings = settings,
                        speedPicked = speedPicked,
                        tempPicked = tempPicked,
                        onSpeed = { viewModel.setSpeedUnit(it); speedPicked = true },
                        onTemp = { viewModel.setTempUnit(it); tempPicked = true }
                    )

                    STEP_VEHICLE -> VehicleStep(
                        settings = settings,
                        confirmed = vehicleConfirmed,
                        onConfirm = { vehicleConfirmed = !vehicleConfirmed },
                        onPolePairs = viewModel::setPolePairs,
                        onWheel = viewModel::setWheelDiameterCm,
                        onGear = viewModel::setGearRatio,
                        onCells = viewModel::setBatteryCells
                    )

                    STEP_ALERTS -> AlertsStep(
                        settings = settings,
                        confirmed = alertsConfirmed,
                        onConfirm = { alertsConfirmed = !alertsConfirmed },
                        onLowBattery = viewModel::setLowBatteryAlertEnabled,
                        onTemperature = viewModel::setHighTemperatureAlertEnabled
                    )

                    else -> SummaryStep(settings)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            GlassSurface(
                Modifier.fillMaxWidth().navigationBarsPadding(),
                shape = RoundedCornerShape(22.dp),
                glowColor = MaterialTheme.colorScheme.primary
            ) {
                Box(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    SetupNavigation(
                        step = step,
                        canContinue = canContinue,
                        onBack = { step-- },
                        onNext = { step++ },
                        onFinish = { viewModel.completeSetup() }
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun SetupBrandHeader(step: Int) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "VESCVIEWER",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.6.sp
        )
        Spacer(Modifier.weight(1f))
        Text(
            if (step == STEP_WELCOME) stringResource(R.string.setup_label) else stringResource(R.string.setup_config_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
private fun SetupProgressHeader(step: Int, total: Int) {
    val progress by animateFloatAsState(
        targetValue = step.toFloat() / total,
        animationSpec = tween(350),
        label = "setup_progress"
    )
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.setup_progress, step, total),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.battery_percent, (step * 100) / total),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(6.dp).clip(CircleShape)
        )
    }
}

@Composable
private fun SetupStepTitle(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StepHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

/* ------------------------------------------------------------------ */
/* Passaggio 0: benvenuto, ringraziamento e link GitHub               */
/* ------------------------------------------------------------------ */

@Composable
private fun WelcomeStep() {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.size(132.dp)
        )
            Text(
                stringResource(R.string.setup_welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        Text(
            stringResource(R.string.setup_welcome_thanks),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            stringResource(R.string.setup_welcome_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            stringResource(R.string.setup_welcome_steps),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        GitHubCard { uriHandler.openUri(GITHUB_URL) }
    }
}

/** Riga cliccabile con il marchio GitHub, il nome del repo e il link. */
@Composable
private fun GitHubCard(onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = MaterialTheme.colorScheme.primary
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_github),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.setup_github_label),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    stringResource(R.string.setup_github_author),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* Passaggio 1: lingua                                                */
/* ------------------------------------------------------------------ */

@Composable
private fun LanguageStep(selected: AppLanguage?, onSelect: (AppLanguage) -> Unit) {
    SetupStepTitle(
        title = stringResource(R.string.setup_language_title),
        description = stringResource(R.string.setup_language_desc)
    )
    LanguageChooser(selected = selected, onSelect = onSelect)
    if (selected == null) StepHint(stringResource(R.string.setup_choose_hint))
}

/* ------------------------------------------------------------------ */
/* Passaggio 2: tema e colore accento                                 */
/* ------------------------------------------------------------------ */

@Composable
private fun ThemeStep(
    settings: AppSettings,
    themePicked: Boolean,
    accentPicked: Boolean,
    onTheme: (ThemeMode) -> Unit,
    onAccent: (Int) -> Unit
) {
    SetupStepTitle(
        title = stringResource(R.string.setup_theme_title),
        description = stringResource(R.string.setup_theme_desc)
    )
    Text(
        stringResource(R.string.setting_theme),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ThemeMode.entries.forEach { mode ->
            ChoiceCard(
                label = themeLabel(mode),
                selected = themePicked && settings.themeMode == mode,
                onClick = { onTheme(mode) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    Text(
        stringResource(R.string.setting_accent),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
    AccentColorChooser(
        selectedIndex = if (accentPicked) settings.accentColorIndex else null,
        onSelect = onAccent
    )
    if (!themePicked || !accentPicked) StepHint(stringResource(R.string.setup_choose_hint))
}

/* ------------------------------------------------------------------ */
/* Passaggio 3: unità di misura                                       */
/* ------------------------------------------------------------------ */

@Composable
private fun UnitsStep(
    settings: AppSettings,
    speedPicked: Boolean,
    tempPicked: Boolean,
    onSpeed: (SpeedUnit) -> Unit,
    onTemp: (TempUnit) -> Unit
) {
    SetupStepTitle(
        title = stringResource(R.string.setup_units_title),
        description = stringResource(R.string.setup_units_desc)
    )
    Text(
        stringResource(R.string.setting_speed_unit),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ChoiceCard(
            label = stringResource(R.string.unit_kmh),
            selected = speedPicked && settings.speedUnit == SpeedUnit.KMH,
            onClick = { onSpeed(SpeedUnit.KMH) },
            modifier = Modifier.fillMaxWidth()
        )
        ChoiceCard(
            label = stringResource(R.string.unit_mph),
            selected = speedPicked && settings.speedUnit == SpeedUnit.MPH,
            onClick = { onSpeed(SpeedUnit.MPH) },
            modifier = Modifier.fillMaxWidth()
        )
    }
    Text(
        stringResource(R.string.setting_temp_unit),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ChoiceCard(
            label = stringResource(R.string.unit_celsius),
            selected = tempPicked && settings.tempUnit == TempUnit.CELSIUS,
            onClick = { onTemp(TempUnit.CELSIUS) },
            modifier = Modifier.fillMaxWidth()
        )
        ChoiceCard(
            label = stringResource(R.string.unit_fahrenheit),
            selected = tempPicked && settings.tempUnit == TempUnit.FAHRENHEIT,
            onClick = { onTemp(TempUnit.FAHRENHEIT) },
            modifier = Modifier.fillMaxWidth()
        )
    }
    if (!speedPicked || !tempPicked) StepHint(stringResource(R.string.setup_choose_hint))
}

/* ------------------------------------------------------------------ */
/* Passaggio 4: veicolo e batteria                                    */
/* ------------------------------------------------------------------ */

@Composable
private fun VehicleStep(
    settings: AppSettings,
    confirmed: Boolean,
    onConfirm: () -> Unit,
    onPolePairs: (Int) -> Unit,
    onWheel: (Float) -> Unit,
    onGear: (Float) -> Unit,
    onCells: (Int) -> Unit
) {
    SetupStepTitle(
        title = stringResource(R.string.setup_vehicle_title),
        description = stringResource(R.string.setup_vehicle_desc)
    )
    NumericSetting(
        label = stringResource(R.string.setting_pole_pairs),
        valueText = "${settings.polePairs}",
        value = settings.polePairs.toFloat(),
        min = 1f,
        max = 30f,
        integer = true,
        onChange = { onPolePairs(it.toInt()) }
    )
    NumericSetting(
        label = stringResource(R.string.setting_wheel_diameter),
        valueText = "%.1f".format(settings.wheelDiameterCm),
        value = settings.wheelDiameterCm,
        min = 5f,
        max = 200f,
        integer = false,
        onChange = onWheel,
        unitSuffix = stringResource(R.string.unit_cm)
    )
    NumericSetting(
        label = stringResource(R.string.setting_gear_ratio),
        valueText = "%.2f".format(settings.gearRatio),
        value = settings.gearRatio,
        min = 0.1f,
        max = 50f,
        integer = false,
        onChange = onGear
    )
    NumericSetting(
        label = stringResource(R.string.setting_battery_cells),
        valueText = "${settings.batteryCells}",
        value = settings.batteryCells.toFloat(),
        min = 2f,
        max = 20f,
        integer = true,
        onChange = { onCells(it.toInt()) }
    )
    ChoiceCard(
        label = stringResource(R.string.setup_vehicle_confirm),
        selected = confirmed,
        onClick = onConfirm,
        modifier = Modifier.fillMaxWidth()
    )
    if (!confirmed) StepHint(stringResource(R.string.setup_choose_hint))
}

/* ------------------------------------------------------------------ */
/* Passaggio 5: avvisi                                                */
/* ------------------------------------------------------------------ */

@Composable
private fun AlertsStep(
    settings: AppSettings,
    confirmed: Boolean,
    onConfirm: () -> Unit,
    onLowBattery: (Boolean) -> Unit,
    onTemperature: (Boolean) -> Unit
) {
    SetupStepTitle(
        title = stringResource(R.string.setup_alerts_title),
        description = stringResource(R.string.setup_alerts_desc)
    )
    ChoiceCard(
        label = stringResource(R.string.setup_low_battery_alert, settings.lowBatteryAlertPercent),
        selected = settings.lowBatteryAlertEnabled,
        onClick = { onLowBattery(!settings.lowBatteryAlertEnabled) },
        modifier = Modifier.fillMaxWidth()
    )
    ChoiceCard(
        label = stringResource(R.string.setup_high_temperature_alert, settings.highTemperatureAlertC),
        selected = settings.highTemperatureAlertEnabled,
        onClick = { onTemperature(!settings.highTemperatureAlertEnabled) },
        modifier = Modifier.fillMaxWidth()
    )
    ChoiceCard(
        label = stringResource(R.string.setup_alerts_confirm),
        selected = confirmed,
        onClick = onConfirm,
        modifier = Modifier.fillMaxWidth()
    )
    if (!confirmed) StepHint(stringResource(R.string.setup_alerts_hint))
}

/* ------------------------------------------------------------------ */
/* Passaggio 6: riepilogo                                             */
/* ------------------------------------------------------------------ */

@Composable
private fun SummaryStep(settings: AppSettings) {
    SetupStepTitle(
        title = stringResource(R.string.setup_summary_title),
        description = stringResource(R.string.setup_summary_desc)
    )
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = MaterialTheme.colorScheme.primary
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryLine(
                stringResource(R.string.setup_summary_language),
                settings.language?.nativeName ?: "—"
            )
            HorizontalDivider()
            SummaryLine(stringResource(R.string.setup_summary_theme), themeLabel(settings.themeMode))
            HorizontalDivider()
            SummaryLine(
                stringResource(R.string.setup_summary_accent),
                if (settings.accentColorIndex in AccentPalette.indices) {
                    AccentPalette[settings.accentColorIndex].labelRes.let { stringResource(it) }
                } else {
                    stringResource(R.string.accent_auto)
                }
            )
            HorizontalDivider()
            SummaryLine(
                stringResource(R.string.setup_summary_units),
                stringResource(
                    if (settings.speedUnit == SpeedUnit.MPH) R.string.unit_mph else R.string.unit_kmh
                ) + " · " + stringResource(
                    if (settings.tempUnit == TempUnit.FAHRENHEIT) R.string.unit_fahrenheit
                    else R.string.unit_celsius
                )
            )
            HorizontalDivider()
            SummaryLine(
                stringResource(R.string.setup_summary_vehicle),
                stringResource(
                    R.string.setup_summary_vehicle_value,
                    settings.polePairs,
                    settings.wheelDiameterCm,
                    settings.batteryCells,
                    settings.gearRatio
                )
            )
        }
    }
    Text(
        stringResource(R.string.setup_alarm_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

/* ------------------------------------------------------------------ */
/* Barra di navigazione del setup                                     */
/* ------------------------------------------------------------------ */

@Composable
private fun SetupNavigation(
    step: Int,
    canContinue: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit
) {
    when (step) {
        STEP_WELCOME -> GlassButton(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.setup_start), fontSize = 16.sp)
        }

        STEP_SUMMARY -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassOutlineButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.setup_back))
            }
            GlassButton(
                onClick = onFinish,
                modifier = Modifier.weight(1.4f)
            ) {
                Text(stringResource(R.string.setup_finish), maxLines = 1, fontSize = 13.sp)
            }
        }

        else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassOutlineButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.setup_back))
            }
            GlassButton(
                onClick = onNext,
                enabled = canContinue,
                modifier = Modifier.weight(1.4f)
            ) {
                Text(stringResource(R.string.setup_next))
            }
        }
    }
}

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
        ThemeMode.SYSTEM -> R.string.theme_system
    }
)
