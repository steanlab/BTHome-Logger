package com.example.mijialogger

import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Bt2hWidget : GlanceAppWidget() {

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId
    ) {
        provideContent {
            Bt2hWidgetContent(context)
        }
    }
}

class Bt2hWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = Bt2hWidget()
}

@Composable
private fun Bt2hWidgetContent(context: Context) {
    val preferences = context.getSharedPreferences(
        "bt2h_widget_data",
        Context.MODE_PRIVATE
    )

    val first = WidgetSensorData.fromPreferences(preferences, "first")
    val second = WidgetSensorData.fromPreferences(preferences, "second")

    val openAppIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color.rgb(249, 247, 250)))
            .padding(12.dp)
            .clickable(actionStartActivity(openAppIntent))
    ) {
        Text(
            text = "BT2H Logger",
            style = TextStyle(
                color = ColorProvider(Color.rgb(30, 30, 35)),
                fontWeight = FontWeight.Bold
            )
        )

        Spacer(modifier = GlanceModifier.height(8.dp))

        WidgetSensorRow(
            title = "Sensor 1",
            sensor = first
        )

        Spacer(modifier = GlanceModifier.height(8.dp))

        WidgetSensorRow(
            title = "Sensor 2",
            sensor = second
        )
    }
}

@Composable
private fun WidgetSensorRow(
    title: String,
    sensor: WidgetSensorData
) {
    val titleColor = ColorProvider(Color.rgb(70, 70, 78))
    val mainColor = ColorProvider(Color.rgb(20, 20, 25))
    val mutedColor = ColorProvider(Color.rgb(110, 110, 120))

    Column(
        modifier = GlanceModifier.fillMaxWidth()
    ) {
        Text(
            text = if (sensor.name.isNotEmpty()) sensor.name else title,
            style = TextStyle(
                color = titleColor,
                fontWeight = FontWeight.Bold
            )
        )

        if (!sensor.hasData) {
            Text(
                text = "No data received yet",
                style = TextStyle(color = mutedColor)
            )
            return
        }

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = String.format(Locale.getDefault(), "%.1f °C", sensor.temperature),
                style = TextStyle(
                    color = mainColor,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = GlanceModifier.width(12.dp))

            Text(
                text = String.format(Locale.getDefault(), "%.1f %%", sensor.humidity),
                style = TextStyle(color = mainColor)
            )

            Spacer(modifier = GlanceModifier.width(12.dp))

            Text(
                text = String.format(Locale.getDefault(), "%.3f V", sensor.voltage),
                style = TextStyle(color = mainColor)
            )
        }

        Text(
            text = "Updated ${sensor.lastSeenText()} · ${sensor.rssi} dBm",
            style = TextStyle(color = mutedColor)
        )
    }
}

private data class WidgetSensorData(
    val name: String,
    val temperature: Float,
    val humidity: Float,
    val voltage: Float,
    val rssi: Int,
    val lastSeenMillis: Long
) {
    val hasData: Boolean
        get() = lastSeenMillis > 0

    fun lastSeenText(): String {
        if (lastSeenMillis <= 0L) return "never"

        val now = System.currentTimeMillis()
        val ageSeconds = ((now - lastSeenMillis) / 1000L).coerceAtLeast(0L)

        return when {
            ageSeconds < 60L -> "${ageSeconds}s ago"
            ageSeconds < 3600L -> "${ageSeconds / 60L}m ago"
            ageSeconds < 86400L -> "${ageSeconds / 3600L}h ago"
            else -> SimpleDateFormat(
                "dd MMM HH:mm",
                Locale.getDefault()
            ).format(Date(lastSeenMillis))
        }
    }

    companion object {
        fun fromPreferences(
            preferences: android.content.SharedPreferences,
            prefix: String
        ): WidgetSensorData {
            return WidgetSensorData(
                name = preferences.getString("${prefix}_name", "") ?: "",
                temperature = preferences.getFloat("${prefix}_temperature", 0f),
                humidity = preferences.getFloat("${prefix}_humidity", 0f),
                voltage = preferences.getFloat("${prefix}_voltage", 0f),
                rssi = preferences.getInt("${prefix}_rssi", 0),
                lastSeenMillis = preferences.getLong("${prefix}_last_seen", 0L)
            )
        }
    }
}
