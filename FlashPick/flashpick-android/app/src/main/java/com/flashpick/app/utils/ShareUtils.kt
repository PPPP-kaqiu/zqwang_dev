package com.flashpick.app.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import com.flashpick.app.data.model.VideoRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ShareUtils {

    suspend fun shareRecordAsImage(context: Context, record: VideoRecord) {
        withContext(Dispatchers.IO) {
            try {
                // 1. Load Thumbnail
                var sourcePath = record.thumbnailPath
                var thumbBitmap = BitmapFactory.decodeFile(sourcePath)
                
                // Fallback if thumb missing
                if (thumbBitmap == null) {
                    // Create a placeholder bitmap
                    thumbBitmap = Bitmap.createBitmap(1080, 608, Bitmap.Config.ARGB_8888)
                    thumbBitmap.eraseColor(Color.LTGRAY)
                }

                // 2. Config Canvas
                val width = 1080 // Standard width
                val padding = 80
                val contentWidth = width - padding * 2
                
                // Paints
                val bgPaint = Paint().apply { color = Color.WHITE }
                
                val titlePaint = TextPaint().apply {
                    color = Color.BLACK
                    textSize = 64f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                }
                
                val datePaint = TextPaint().apply {
                    color = Color.GRAY
                    textSize = 36f
                    isAntiAlias = true
                }

                val summaryPaint = TextPaint().apply {
                    color = Color.DKGRAY
                    textSize = 42f
                    isAntiAlias = true
                }
                
                val footerPaint = TextPaint().apply {
                    color = Color.LTGRAY
                    textSize = 32f
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                }

                // 3. Prepare Content
                val titleText = record.title ?: "未命名记忆"
                val summaryText = record.summary ?: "暂无摘要"
                val dateText = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date(record.createdAt))
                val appName = record.appName.ifEmpty { "FlashPick" }

                // 4. Measure Layouts
                // Title Layout
                val titleLayout = StaticLayout.Builder.obtain(
                    titleText, 0, titleText.length, titlePaint, contentWidth
                ).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

                // Summary Layout
                val summaryLayout = StaticLayout.Builder.obtain(
                    summaryText, 0, summaryText.length, summaryPaint, contentWidth
                ).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(20f, 1f).build()

                // Calculate Image Height (Maintain aspect ratio)
                val imgRatio = thumbBitmap.height.toFloat() / thumbBitmap.width.toFloat()
                val displayImgHeight = (contentWidth * imgRatio).toInt()

                // Calculate Total Height
                val gap = 40
                val totalHeight = padding + 
                                  displayImgHeight + gap + 
                                  titleLayout.height + 20 + 
                                  40 /* Date height approx */ + gap + 
                                  summaryLayout.height + 120 + 
                                  40 + padding

                // 5. Draw
                val resultBitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(resultBitmap)
                
                // Background
                canvas.drawRect(0f, 0f, width.toFloat(), totalHeight.toFloat(), bgPaint)

                // Image
                val destRect = Rect(padding, padding, padding + contentWidth, padding + displayImgHeight)
                canvas.drawBitmap(thumbBitmap, null, destRect, null)

                var currentY = padding + displayImgHeight + gap.toFloat()

                // Title
                canvas.save()
                canvas.translate(padding.toFloat(), currentY)
                titleLayout.draw(canvas)
                canvas.restore()
                currentY += titleLayout.height + 20

                // Date
                canvas.drawText(dateText, padding.toFloat(), currentY + 30, datePaint)
                currentY += 40 + gap

                // Summary
                val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 2f }
                canvas.drawLine(padding.toFloat(), currentY - gap/2, (width - padding).toFloat(), currentY - gap/2, linePaint)

                canvas.save()
                canvas.translate(padding.toFloat(), currentY)
                summaryLayout.draw(canvas)
                canvas.restore()

                // Footer
                val footerY = (totalHeight - padding).toFloat()
                canvas.drawText("Generated by FlashPick • $appName", (width / 2).toFloat(), footerY, footerPaint)

                // 6. Save to File
                val cachePath = File(context.cacheDir, "images")
                cachePath.mkdirs()
                val file = File(cachePath, "flashpick_share_${System.currentTimeMillis()}.png")
                val stream = FileOutputStream(file)
                resultBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.close()

                // 7. Share
                val contentUri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    type = "image/png"
                }

                withContext(Dispatchers.Main) {
                    context.startActivity(Intent.createChooser(shareIntent, "分享记忆卡片"))
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
