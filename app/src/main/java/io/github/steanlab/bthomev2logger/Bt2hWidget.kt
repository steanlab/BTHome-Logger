package io.github.steanlab.bthomev2logger

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val WIDGET_PREFS_NAME = "bt2h_widget_data"

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
        WIDGET_PREFS_NAME,
        Context.MODE_PRIVATE
    )

    val firstSensor = WidgetSensorData.fromPreferences(
        preferences = preferences,
        prefix = "first"
    )

    val secondSensor = WidgetSensorData.fromPreferences(
        preferences = preferences,
        prefix = "second"
    )

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFFF9F7FA)))
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Text(
            text = "BTHomeV2 Logger",
            style = TextStyle(
                color = ColorProvider(Color(0xFF1E1E23)),
                fontWeight = FontWeight.Bold
            )
        )

        Spacer(modifier = GlanceModifier.height(8.dp))

        WidgetSensorRow(
            defaultTitle = "Sensor 1",
            sensor = firstSensor
        )

        Spacer(modifier = GlanceModifier.height(8.dp))

        WidgetSensorRow(
            defaultTitle = "Sensor 2",
            sensor = secondSensor
        )
    }
}

@Composable
private fun WidgetSensorRow(
    defaultTitle: String,
    sensor: WidgetSensorData
) {
    val titleColor = ColorProvider(Color(0xFF46464E))
    val mainColor = ColorProvider(Color(0xFF141419))
    val mutedColor = ColorProvider(Color(0xFF6E6E78))

    Column(
        modifier = GlanceModifier.fillMaxWidth()
    ) {
        Text(
            text = if (sensor.name.isNotBlank()) {
                sensor.name
            } else {
                defaultTitle
            },
            style = TextStyle(
                color = titleColor,
                fontWeight = FontWeight.Bold
            )
        )

        if (sensor.hasData) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format(
                        Locale.getDefault(),
                        "%.1f °C",
                        sensor.temperature
                    ),
                    style = TextStyle(
                        color = mainColor,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = GlanceModifier.width(12.dp))

                Text(
                    text = String.format(
                        Locale.getDefault(),
                        "%.1f %%",
                        sensor.humidity
                    ),
                    style = TextStyle(color = mainColor)
                )

                Spacer(modifier = GlanceModifier.width(12.dp))

                Text(
                    text = String.format(
                        Locale.getDefault(),
                        "%.3f V",
                        sensor.voltage
                    ),
                    style = TextStyle(color = mainColor)
                )
            }

            Text(
                text = "Updated ${sensor.lastSeenText()} · ${sensor.rssi} dBm",
                style = TextStyle(color = mutedColor)
            )
        } else {
            Text(
                text = "No data received yet",
                style = TextStyle(color = mutedColor)
            )
        }
    }
}

private data class WidgetSensorData(
    val name: String,
    val mac: String,
    val temperature: Float,
    val humidity: Float,
    val voltage: Float,
    val battery: Int,
    val rssi: Int,
    val lastSeenMillis: Long
) {
    val hasData: Boolean
        get() = lastSeenMillis > 0L

    fun lastSeenText(): String {
        if (!hasData) {
            return "never"
        }

        val ageSeconds = (
            (System.currentTimeMillis() - lastSeenMillis) / 1000L
        ).coerceAtLeast(0L)

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
            preferences: SharedPreferences,
            prefix: String
        ): WidgetSensorData {
            return WidgetSensorData(
                name = preferences.getString(
                    "${prefix}_name",
                    ""
                ) ?: "",
                mac = preferences.getString(
                    "${prefix}_mac",
                    ""
                ) ?: "",
                temperature = preferences.getFloat(
                    "${prefix}_temperature",
                    0f
                ),
                humidity = preferences.getFloat(
                    "${prefix}_humidity",
                    0f
                ),
                voltage = preferences.getFloat(
                    "${prefix}_voltage",
                    0f
                ),
                battery = preferences.getInt(
                    "${prefix}_battery",
                    0
                ),
                rssi = preferences.getInt(
                    "${prefix}_rssi",
                    0
                ),
                lastSeenMillis = preferences.getLong(
                    "${prefix}_last_seen",
                    0L
                )
            )
        }
    }
}
