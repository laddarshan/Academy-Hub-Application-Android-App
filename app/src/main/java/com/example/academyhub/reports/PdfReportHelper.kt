package com.example.academyhub.reports

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfReportHelper {

    data class ReportRow(val columns: List<String>, val rowType: String = "normal")

    fun generatePdf(
        context: Context, reportTitle: String, subtitle: String,
        summaryItems: List<Pair<String, String>>,
        tableHeaders: List<String>, tableRows: List<ReportRow>,
        fileName: String
    ): Intent? {
        return try {
            val document = PdfDocument()
            val pageW = 595; val pageH = 842  // A4 portrait
            var pageNum = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create()
            var page     = document.startPage(pageInfo)
            var canvas   = page.canvas
            var yPos     = 0f

            // ── Paint definitions ────────────────────────────────────────────
            val tealColor  = Color.parseColor("#075E54")
            val tealLight  = Color.parseColor("#E8F5E9")
            val tealDark   = Color.parseColor("#004D40")

            val pHeader = Paint().apply { color = tealColor }
            val pWhite  = Paint().apply { textSize = 20f; isFakeBoldText = true; color = Color.WHITE }
            val pSubtit = Paint().apply { textSize = 11f; color = Color.parseColor("#B2DFDB") }
            val pDate   = Paint().apply { textSize = 9f;  color = Color.parseColor("#B2DFDB") }
            val pLabel  = Paint().apply { textSize = 10f; color = Color.parseColor("#555555") }
            val pValue  = Paint().apply { textSize = 10f; isFakeBoldText = true; color = tealColor }
            val pSumBg  = Paint().apply { color = tealLight }
            val pSumTit = Paint().apply { textSize = 11f; isFakeBoldText = true; color = tealDark }
            val pTblHdr = Paint().apply { color = tealColor }
            val pColHdr = Paint().apply { textSize = 10f; isFakeBoldText = true; color = Color.WHITE }
            val pNormal = Paint().apply { textSize = 9.5f; color = Color.parseColor("#222222") }
            val pGreen  = Paint().apply { textSize = 9.5f; isFakeBoldText = true; color = Color.parseColor("#2E7D32") }
            val pRed    = Paint().apply { textSize = 9.5f; isFakeBoldText = true; color = Color.parseColor("#B71C1C") }
            val pOrange = Paint().apply { textSize = 9.5f; isFakeBoldText = true; color = Color.parseColor("#E65100") }
            val pRowAlt = Paint().apply { color = Color.parseColor("#F1F8F6") }
            val pBorder = Paint().apply { color = Color.parseColor("#CCCCCC"); strokeWidth = 0.5f }
            val pLine   = Paint().apply { color = Color.parseColor("#DDDDDD"); strokeWidth = 0.5f }
            val pFoot   = Paint().apply { textSize = 8f; color = Color.parseColor("#AAAAAA") }

            fun newPage() {
                document.finishPage(page)
                pageNum++
                pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create()
                page     = document.startPage(pageInfo)
                canvas   = page.canvas
                yPos     = 20f
            }

            fun checkPage(needed: Float = 30f) { if (yPos + needed > 810f) newPage() }

            // ── Header bar ────────────────────────────────────────────────────
            canvas.drawRect(0f, 0f, pageW.toFloat(), 68f, pHeader)
            canvas.drawText(reportTitle, 18f, 32f, pWhite)
            canvas.drawText(subtitle, 18f, 50f, pSubtit)
            val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
            canvas.drawText("Generated: $dateStr", (pageW - 200).toFloat(), 50f, pDate)
            yPos = 80f

            // ── Summary box ───────────────────────────────────────────────────
            if (summaryItems.isNotEmpty()) {
                val boxH = 26f + summaryItems.size * 18f
                canvas.drawRect(14f, yPos, (pageW - 14).toFloat(), yPos + boxH, pSumBg)
                canvas.drawText("Summary", 22f, yPos + 16f, pSumTit)
                yPos += 24f
                summaryItems.forEach { (label, value) ->
                    canvas.drawText("• $label:", 22f, yPos + 12f, pLabel)
                    canvas.drawText(value, 190f, yPos + 12f, pValue)
                    yPos += 18f
                }
                yPos += 10f
            }

            // ── Table ─────────────────────────────────────────────────────────
            if (tableHeaders.isNotEmpty()) {
                checkPage(28f)
                val colW = (pageW - 30f) / tableHeaders.size.coerceAtLeast(1)

                // Table header row
                canvas.drawRect(15f, yPos, (pageW - 15).toFloat(), yPos + 22f, pTblHdr)
                tableHeaders.forEachIndexed { i, h ->
                    val x = 15f + i * colW
                    val maxLen = if (h.length > 30) h.substring(0, 27) + "…" else h
                    canvas.drawText(maxLen, x + 4f, yPos + 15f, pColHdr)
                }
                yPos += 22f

                tableRows.forEachIndexed { rowIdx, row ->
                    checkPage(20f)
                    if (rowIdx % 2 == 1) {
                        canvas.drawRect(15f, yPos, (pageW - 15).toFloat(), yPos + 19f, pRowAlt)
                    }
                    val paint = when (row.rowType) {
                        "green"  -> pGreen
                        "red"    -> pRed
                        "orange" -> pOrange
                        else     -> pNormal
                    }
                    row.columns.forEachIndexed { i, col ->
                        val x = 15f + i * colW
                        val maxLen = if (col.length > 20) col.substring(0, 17) + "…" else col
                        canvas.drawText(maxLen, x + 4f, yPos + 13f, paint)
                    }
                    canvas.drawLine(15f, yPos + 19f, (pageW - 15).toFloat(), yPos + 19f, pLine)
                    yPos += 19f
                }

                // Table outer border
                val tableTop = 80f + (if (summaryItems.isNotEmpty()) 26f + summaryItems.size * 18f + 10f else 0f)
                canvas.drawRect(15f, tableTop, (pageW - 15).toFloat(), yPos, pBorder.apply { style = Paint.Style.STROKE; strokeWidth = 1f })
            }

            // ── Footer ────────────────────────────────────────────────────────
            val footY = 828f
            canvas.drawLine(15f, footY, (pageW - 15).toFloat(), footY, pLine)
            canvas.drawText("Academy Hub — $reportTitle", 15f, footY + 11f, pFoot)
            canvas.drawText("Page $pageNum", (pageW - 45).toFloat(), footY + 11f, pFoot)

            document.finishPage(page)

            // ── Save & return intent ───────────────────────────────────────────
            val file = File(context.cacheDir, fileName)
            document.writeTo(FileOutputStream(file))
            document.close()

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            e.printStackTrace(); null
        }
    }
}
