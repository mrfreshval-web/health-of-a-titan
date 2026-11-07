package com.healthofatitan.app.feature.week

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.healthofatitan.app.domain.today.DayMode
import com.healthofatitan.app.domain.today.week.ShareCard
import com.healthofatitan.app.domain.today.week.TitanWeekLook
import java.io.File
import kotlin.random.Random

/**
 * «Поделиться» (A52 §2.7, Agent 7): the image of the Titan of the week only — the sky, the columns,
 * the figure, «Титан недели» and «Держи своё небо». No number of health, no days, no goals
 * ([ShareCard] holds every text of it). The file lives in the cache and leaves only through the
 * system share sheet.
 */
object WeekShare {
    const val SIZE = 1080
    private const val DIR = "week_share"

    private fun modeColor(mode: DayMode): Int = when (mode) {
        DayMode.RAZGON -> Color(0xFFFFB067)
        DayMode.ROVNO -> Color(0xFF7DE3D1)
        DayMode.BEREZHNO -> Color(0xFFA8A3FF)
    }.toArgb()

    fun render(context: Context, look: TitanWeekLook): Bitmap {
        val card = ShareCard.of(look)
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val sky = WeekTitanArt.sky(look.sky, dark = true).map { it.toArgb() }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, SIZE.toFloat(), sky[0], sky[1], Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), paint)
        paint.shader = null
        val rnd = Random(look.seed)
        paint.color = android.graphics.Color.WHITE
        repeat(40) {
            paint.alpha = (60 + rnd.nextInt(140))
            c.drawCircle(rnd.nextFloat() * SIZE, rnd.nextFloat() * SIZE * 0.5f, 2f + rnd.nextFloat() * 3f, paint)
        }
        val accent = modeColor(look.mode)
        val base = SIZE * 0.86f
        val maxH = SIZE * 0.5f
        val w = SIZE * 0.06f
        listOf(0.1f, 0.22f, 0.78f, 0.9f).forEachIndexed { i, x ->
            val h = maxH * (0.25f + 0.75f * look.columns.getOrElse(i) { 0f })
            paint.color = accent
            paint.alpha = 150
            c.drawRoundRect(RectF(SIZE * x - w / 2, base - h, SIZE * x + w / 2, base), w / 2, w / 2, paint)
        }
        ContextCompat.getDrawable(context, com.healthofatitan.app.core.ui.R.drawable.titan_line)?.mutate()?.let { d ->
            d.colorFilter = PorterDuffColorFilter(android.graphics.Color.WHITE, PorterDuff.Mode.SRC_IN)
            val side = (SIZE * 0.62f).toInt()
            val left = (SIZE - side) / 2
            d.setBounds(left, (base - side).toInt(), left + side, base.toInt())
            d.draw(c)
        }
        // «Печать недели» (A53, D-050): the medallion rim with its seven notches, all carved
        paint.style = Paint.Style.STROKE
        paint.color = android.graphics.Color.rgb(0xB9, 0xB2, 0xA3)
        paint.alpha = 220
        paint.strokeWidth = 10f
        val r = SIZE * 0.38f
        c.drawCircle(SIZE / 2f, SIZE * 0.55f, r, paint)
        paint.color = accent
        paint.strokeWidth = 8f
        paint.strokeCap = Paint.Cap.ROUND
        val oval = RectF(SIZE / 2f - r, SIZE * 0.55f - r, SIZE / 2f + r, SIZE * 0.55f + r)
        repeat(7) { i -> c.drawArc(oval, -90f + i * 360f / 7f - 15f, 30f, false, paint) }
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        paint.color = android.graphics.Color.WHITE
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        paint.textSize = 64f
        paint.textAlign = Paint.Align.CENTER
        c.drawText(card.title, SIZE / 2f, 120f, paint)
        paint.textSize = 40f
        paint.color = accent
        c.drawText(card.slogan, SIZE / 2f, SIZE - 60f, paint)
        return bmp
    }

    fun file(context: Context, look: TitanWeekLook): File {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val f = File(dir, "titan-${look.weekStart}.png")
        f.outputStream().use { render(context, look).compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }

    fun share(context: Context, look: TitanWeekLook) {
        runCatching {
            val f = file(context, look)
            val uri = FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, f)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(null, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, ShareCard.of(look).title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    const val AUTHORITY_SUFFIX = ".weekshare"
}
