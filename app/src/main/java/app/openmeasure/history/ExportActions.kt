package app.openmeasure.history

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import app.openmeasure.R
import app.openmeasure.data.HistoryItem
import app.openmeasure.data.MeasurementKind
import app.openmeasure.measure.export.CsvExporter
import app.openmeasure.measure.export.ExportKind
import app.openmeasure.measure.export.ExportRow
import app.openmeasure.measure.units.UnitSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** F17 export: files go to the app cache and are shared through FileProvider; nothing leaves the device unless the user picks a target. */
internal object ExportActions {
    private const val DIR = "exports"

    fun toRow(item: HistoryItem) = ExportRow(
        item.createdAtMillis,
        when (item.kind) {
            MeasurementKind.LINE_PATH -> ExportKind.LINE_PATH
            MeasurementKind.RECTANGLE -> ExportKind.RECTANGLE
            MeasurementKind.HEIGHT -> ExportKind.HEIGHT
        },
        when (item.kind) {
            MeasurementKind.LINE_PATH -> item.totalLengthMeters
            MeasurementKind.RECTANGLE -> item.areaSquareMeters
            MeasurementKind.HEIGHT -> item.heightMeters
        },
        item.confidenceLevel,
        item.note,
    )

    suspend fun exportCsv(context: Context, items: List<HistoryItem>, units: UnitSystem): Uri = withContext(Dispatchers.IO) {
        val file = newFile(context, "csv")
        // UTF-8 BOM so spreadsheet apps decode the unit symbols correctly.
        file.writeText("﻿" + CsvExporter.toCsv(items.map(::toRow), units), Charsets.UTF_8)
        uriFor(context, file)
    }

    suspend fun exportPdf(context: Context, items: List<HistoryItem>, units: UnitSystem): Uri = withContext(Dispatchers.IO) {
        val file = newFile(context, "pdf")
        PdfExporter.write(context, items, units, file)
        uriFor(context, file)
    }

    fun fileShareIntent(context: Context, uri: Uri, mime: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, context.getString(R.string.capture_share)).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Single item: text summary plus the photo when there is one. */
    fun itemShareIntent(context: Context, item: HistoryItem, units: UnitSystem): Intent {
        val text = buildString {
            append(valueText(item, units)).append(" · ").append(context.getString(kindRes(item.kind)))
            append(" · ").append(context.getString(R.string.history_confidence, context.getString(confidenceRes(item.confidenceLevel))))
            item.note?.takeIf { it.isNotBlank() }?.let { append('\n').append(it) }
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, text)
            if (item.photoUri != null) {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, Uri.parse(item.photoUri))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
        }
        return Intent.createChooser(send, context.getString(R.string.capture_share)).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun newFile(context: Context, ext: String): File {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // keep only the latest export
        return File(dir, "openmeasure-history-${System.currentTimeMillis()}.$ext")
    }

    private fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
