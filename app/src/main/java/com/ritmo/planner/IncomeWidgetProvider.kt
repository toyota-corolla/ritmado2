package com.ritmo.planner

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.widget.RemoteViews
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Tela inicial: total recebido hoje + mini gráfico dos últimos sete dias. */
class IncomeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateOne(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) refreshAll(context)
    }

    companion object {
        const val ACTION_REFRESH = "com.ritmo.planner.REFRESH_INCOME_WIDGET"

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, IncomeWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            ids.forEach { updateOne(context, manager, it) }
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, id: Int) {
            val prefs = context.getSharedPreferences("ritmo_data", Context.MODE_PRIVATE)
            val entries = try { JSONArray(prefs.getString("income", "[]")) } catch (_: Exception) { JSONArray() }
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            var todayTotal = 0.0
            val totals = linkedMapOf<String, Double>()
            val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -6)
            repeat(7) {
                totals[keyFormat.format(calendar.time)] = 0.0
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            for (i in 0 until entries.length()) {
                val item = entries.optJSONObject(i) ?: continue
                val date = item.optString("date")
                val amount = item.optDouble("amount", 0.0)
                if (date == today) todayTotal += amount
                if (totals.containsKey(date)) totals[date] = (totals[date] ?: 0.0) + amount
            }

            val views = RemoteViews(context.packageName, R.layout.widget_income)
            views.setTextViewText(R.id.widget_income_value, "R$ " + String.format(Locale("pt", "BR"), "%,.2f", todayTotal))
            views.setImageViewBitmap(R.id.widget_income_chart, makeSparkline(totals.values.toList()))
            val open = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(context, 4101, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            manager.updateAppWidget(id, views)
        }

        private fun makeSparkline(values: List<Double>): Bitmap {
            val width = 348
            val height = 174
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF16899B.toInt(); style = Paint.Style.STROKE
                strokeWidth = 9f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            }
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF16899B.toInt(); style = Paint.Style.FILL }
            val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x3373BFC0; strokeWidth = 2f }
            val left = 12f; val right = width - 12f; val top = 22f; val bottom = height - 22f
            canvas.drawLine(left, bottom, right, bottom, guide)
            val max = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
            val path = Path()
            val coords = values.mapIndexed { index, value ->
                val x = left + (right - left) * index / (values.size - 1).coerceAtLeast(1)
                val y = bottom - ((value / max).toFloat() * (bottom - top))
                x to y
            }
            coords.forEachIndexed { index, point -> if (index == 0) path.moveTo(point.first, point.second) else path.lineTo(point.first, point.second) }
            canvas.drawPath(path, line)
            coords.forEach { (x, y) -> canvas.drawCircle(x, y, 7f, dot) }
            return bitmap
        }
    }
}
