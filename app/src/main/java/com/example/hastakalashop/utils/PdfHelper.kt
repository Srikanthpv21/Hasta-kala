package com.example.hastakalashop.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.util.Log
import com.example.hastakalashop.ui.quickbill.QuickBillFragment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfHelper {

    private const val TAG = "PdfHelper"
    private const val PDF_FILENAME = "Hasta_Kala_Receipt.pdf" // Fixed name: overwrites old receipts (Fix #8)

    fun generateMultiItemReceipt(context: Context, cartItems: List<QuickBillFragment.CartEntry>): File? {
        val pdfDocument = PdfDocument()
        val pageWidth   = 400
        val pageHeight  = 600 + (cartItems.size * 30)
        val pageInfo    = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page        = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        val paint = Paint()

        val sdf     = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        val dateStr = sdf.format(Date())

        // Header
        paint.color = Color.parseColor("#1B5E20")
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 80f, paint)
        paint.color = Color.WHITE
        paint.textSize = 24f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("HASTA-KALA SHOP", pageWidth / 2f, 45f, paint)
        paint.textSize = 10f
        paint.isFakeBoldText = false
        canvas.drawText("Handcrafted with Love", pageWidth / 2f, 65f, paint)

        // Receipt info
        paint.color = Color.BLACK
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 10f
        paint.color = Color.GRAY
        canvas.drawText("RECEIPT NO: #${System.currentTimeMillis().toString().takeLast(6)}", 30f, 110f, paint)
        canvas.drawText("DATE: $dateStr", 30f, 130f, paint)
        paint.color = Color.LTGRAY
        canvas.drawLine(30f, 150f, pageWidth - 30f, 150f, paint)

        // Column headers
        paint.color = Color.BLACK
        paint.isFakeBoldText = true
        paint.textSize = 11f
        canvas.drawText("ITEM DESCRIPTION", 30f, 180f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("TOTAL", pageWidth - 30f, 180f, paint)

        // Items
        var yPos = 220f
        paint.isFakeBoldText = false
        cartItems.forEach { entry ->
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 12f
            paint.color = Color.BLACK
            canvas.drawText(entry.item.name, 30f, yPos, paint)

            paint.textSize = 9f
            paint.color = Color.DKGRAY
            canvas.drawText("Qty: ${entry.quantity} x ₹${entry.item.price}", 30f, yPos + 15f, paint)

            paint.textAlign = Paint.Align.RIGHT
            paint.textSize = 12f
            paint.color = Color.BLACK
            canvas.drawText("₹${String.format("%.2f", entry.price)}", pageWidth - 30f, yPos + 5f, paint)

            yPos += 45f
        }

        // Divider
        paint.color = Color.LTGRAY
        canvas.drawLine(30f, yPos, pageWidth - 30f, yPos, paint)
        yPos += 40f

        // Grand total
        val grandTotal = cartItems.sumOf { it.price }
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 16f
        paint.isFakeBoldText = true
        paint.color = Color.parseColor("#1B5E20")
        canvas.drawText("GRAND TOTAL", 150f, yPos, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format("%.2f", grandTotal)}", pageWidth - 30f, yPos, paint)

        // Footer
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = false
        paint.color = Color.GRAY
        paint.textSize = 10f
        canvas.drawText("Thank you for supporting local artisans!", pageWidth / 2f, pageHeight - 60f, paint)
        canvas.drawText("Hasta-Kala: Supporting Indian Artisans", pageWidth / 2f, pageHeight - 40f, paint)

        // Border
        paint.style = Paint.Style.STROKE
        paint.color = Color.LTGRAY
        paint.strokeWidth = 2f
        canvas.drawRect(10f, 10f, pageWidth - 10f, pageHeight - 10f, paint)

        pdfDocument.finishPage(page)

        // Fix #8: Use a fixed filename so old receipts are overwritten, not accumulated
        val file = File(context.cacheDir, PDF_FILENAME)
        return try {
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            file
        } catch (e: Exception) {
            // Fix #12: Use tagged Log.e instead of printStackTrace()
            Log.e(TAG, "Failed to generate PDF receipt", e)
            pdfDocument.close()
            null
        }
    }
}
