package app.openmeasure

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.openmeasure.ui.Dimens
import app.openmeasure.ui.OmColors
import app.openmeasure.ui.OmShapes

/** First launch: app name, three short tips and Continue (which also records the accuracy disclaimer as seen). */
@Composable
fun WelcomeScreen(onContinue: () -> Unit) {
    Box(Modifier.fillMaxSize().background(OmColors.Black).windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 440.dp).verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineLarge, color = Color.White,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.welcome_tagline), style = MaterialTheme.typography.bodyLarge, color = OmColors.TextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 28.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Tip(AppIcons.Measure, R.string.welcome_tip_move)
                Tip(AppIcons.Plus, R.string.welcome_tip_place)
                Tip(AppIcons.Level, R.string.welcome_tip_estimates)
            }
            Text(
                stringResource(R.string.disclaimer_body), style = MaterialTheme.typography.bodySmall, color = OmColors.TextSecondary,
                modifier = Modifier.padding(top = 24.dp),
            )
            Box(
                Modifier.padding(top = 24.dp).fillMaxWidth().heightIn(min = 52.dp).clip(OmShapes.pill).background(OmColors.Accent)
                    .clickable(role = Role.Button, onClick = onContinue),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.welcome_continue), style = MaterialTheme.typography.titleMedium, color = OmColors.Black)
            }
        }
    }
}

@Composable
private fun Tip(icon: ImageVector, textRes: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(Dimens.touch).clip(OmShapes.card).background(OmColors.Surface), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = OmColors.Accent, modifier = Modifier.size(24.dp))
        }
        Text(
            stringResource(textRes), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium), color = Color.White,
        )
    }
}
