package com.patryk.speedometer.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.patryk.speedometer.data.db.Sample
import com.patryk.speedometer.data.db.Session
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ExportUtils {

    fun shareCsv(context: Context, session: Session, samples: List<Sample>) {
        val filename = exportFilename(session, "csv")
        val content = buildCsv(samples)
        shareFile(context, filename, content, "text/csv")
    }

    fun shareGpx(context: Context, session: Session, samples: List<Sample>) {
        val filename = exportFilename(session, "gpx")
        val content = buildGpx(session, samples)
        shareFile(context, filename, content, "application/gpx+xml")
    }

    private fun exportFilename(session: Session, ext: String): String {
        val name = session.label.ifBlank {
            SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date(session.startMs))
        }.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        return "speedometer_${name}.$ext"
    }

    private fun buildCsv(samples: List<Sample>): String {
        val sb = StringBuilder()
        sb.appendLine("timestamp_ms,speed_mps,lat,lng,altitude_m,accuracy_m")
        for (s in samples) {
            sb.appendLine("${s.timestampMs},${s.speedMps},${s.lat},${s.lng},${s.altitudeM},${s.accuracyM}")
        }
        return sb.toString()
    }

    private fun buildGpx(session: Session, samples: List<Sample>): String {
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val name = session.label.ifBlank {
            SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(session.startMs))
        }
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<gpx version="1.1" creator="Speedometer" xmlns="http://www.topografix.com/GPX/1/1">""")
        sb.appendLine("  <trk>")
        sb.appendLine("    <name>${escapeXml(name)}</name>")
        sb.appendLine("    <trkseg>")
        for (s in samples) {
            sb.appendLine("""      <trkpt lat="${s.lat}" lon="${s.lng}">""")
            sb.appendLine("        <ele>${s.altitudeM}</ele>")
            sb.appendLine("        <time>${iso.format(Date(s.timestampMs))}</time>")
            sb.appendLine("        <extensions><speed>${s.speedMps}</speed></extensions>")
            sb.appendLine("      </trkpt>")
        }
        sb.appendLine("    </trkseg>")
        sb.appendLine("  </trk>")
        sb.append("</gpx>")
        return sb.toString()
    }

    private fun escapeXml(s: String) = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun shareFile(context: Context, filename: String, content: String, mimeType: String) {
        val dir = File(context.cacheDir, "exports").also { it.mkdirs() }
        val file = File(dir, filename)
        file.writeText(content)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, null))
    }
}
