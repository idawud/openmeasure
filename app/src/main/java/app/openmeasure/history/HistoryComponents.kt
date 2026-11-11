package app.openmeasure.history

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.openmeasure.AppIcons
import app.openmeasure.R
import app.openmeasure.data.HistoryItem
import app.openmeasure.data.MeasurementKind
import app.openmeasure.measure.units.AreaFormatter
import app.openmeasure.measure.units.LengthFormatter
import app.openmeasure.measure.units.UnitSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Headline value of an entry in the user's unit, e.g. "1.23 m" or "1.25 m²". */
internal fun valueText(item: HistoryItem, units: UnitSystem): String = when (item.kind) {
    MeasurementKind.LINE_PATH -> item.totalLengthMeters?.let { LengthFormatter.format(it, units) }
    MeasurementKind.RECTANGLE -> item.areaSquareMeters?.let { AreaFormatter.format(it, units) }
    MeasurementKind.HEIGHT -> item.heightMeters?.let { LengthFormatter.format(it, units) }
} ?: "—"

internal fun dateText(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

@Composable
internal fun kindLabel(kind: MeasurementKind): String = stringResource(
    when (kind) {
        MeasurementKind.LINE_PATH -> R.string.history_kind_line
        MeasurementKind.RECTANGLE -> R.string.history_kind_rect
        MeasurementKind.HEIGHT -> R.string.history_kind_height
    },
)

@Composable
internal fun confidenceLabel(level: String): String = stringResource(
    when (level) {
        "HIGH" -> R.string.confidence_high
        "MEDIUM" -> R.string.confidence_medium
        else -> R.string.confidence_low
    },
)

internal fun kindIcon(kind: MeasurementKind) = when (kind) {
    MeasurementKind.LINE_PATH -> AppIcons.Measure
    MeasurementKind.RECTANGLE -> AppIcons.Rectangle
    MeasurementKind.HEIGHT -> AppIcons.Height
}

@Composable
internal fun HistoryRow(item: HistoryItem, units: UnitSystem, onClick: () -> Unit) {
    val kind = kindLabel(item.kind)
    val value = valueText(item, units)
    val date = dateText(item.createdAtMillis)
    val conf = stringResource(R.string.history_confidence, confidenceLabel(item.confidenceLevel))
    val desc = stringResource(R.string.history_row_desc, kind, value, conf, date)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp).semantics(mergeDescendants = true) { contentDescription = desc },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Thumbnail(item.photoUri, item.kind, Modifier.size(56.dp))
        Column(Modifier.weight(1f)) {
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text("$kind · $conf", style = MaterialTheme.typography.bodyMedium)
            Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            item.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
        }
    }
}

/** Photo thumbnail loaded off the main thread; falls back to the kind icon when absent, deleted or unreadable. */
@Composable
internal fun Thumbnail(photoUri: String?, kind: MeasurementKind, modifier: Modifier = Modifier, sizePx: Int = 160) {
    val context = LocalContext.current
    var bitmap by remember(photoUri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(photoUri) {
        bitmap = if (photoUri == null) null else withContext(Dispatchers.IO) { loadThumb(context, Uri.parse(photoUri), sizePx) }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b != null) {
            Image(b.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(kindIcon(kind), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun loadThumb(context: Context, uri: Uri, sizePx: Int): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val opts = BitmapFactory.Options().apply { inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / sizePx).coerceAtLeast(1) }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }
} catch (e: Exception) {
    null // missing, deleted or permission-revoked photo
}
