package com.example.mijialogger

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelUuid
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DiscoveredSensor(
    val mac: String,
    val name: String,
    val rssi: Int
)

class MainActivity : ComponentActivity() {

    private var temperature by mutableStateOf<Float?>(null)
    private var humidity by mutableStateOf<Float?>(null)
    private var battery by mutableStateOf<Int?>(null)
    private var voltage by mutableStateOf<Float?>(null)

    private var selectedMac by mutableStateOf<String?>(null)
    private var selectedName by mutableStateOf<String?>(null)

    private var rssi by mutableIntStateOf(0)
    private var scanStatus by mutableStateOf("Preparing Bluetooth scan…")
    private var isScanning by mutableStateOf(false)

    private var lastPayloadHex by mutableStateOf("No BTHome packet received yet.")
    private var diagnosticText by mutableStateOf("")

    private val discoveredSensors = mutableStateListOf<DiscoveredSensor>()

    private val bthomeUuid =
        ParcelUuid.fromString("0000FCD2-0000-1000-8000-00805F9B34FB")

    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var preferences: SharedPreferences

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasRequiredPermissions()) {
                startBleScan()
            } else {
                scanStatus = "Bluetooth permission is required to scan."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        selectedMac = preferences.getString(KEY_SELECTED_MAC, null)
        selectedName = preferences.getString(KEY_SELECTED_NAME, null)

        val bluetoothManager =
            getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LoggerScreen(
                        status = scanStatus,
                        selectedMac = selectedMac,
                        selectedName = selectedName,
                        sensors = discoveredSensors,
                        temperature = temperature,
                        humidity = humidity,
                        battery = battery,
                        voltage = voltage,
                        rssi = rssi,
                        isScanning = isScanning,
                        payloadHex = lastPayloadHex,
                        diagnosticText = diagnosticText,
                        onSelectSensor = { sensor ->
                            selectSensor(sensor)
                        },
                        onChangeSensor = {
                            clearSelectedSensor()
                        }
                    )
                }
            }
        }

        checkPermissionsAndStart()
    }

    override fun onDestroy() {
        stopBleScan()
        super.onDestroy()
    }

    private fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return requiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(this, permission) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkPermissionsAndStart() {
        if (hasRequiredPermissions()) {
            startBleScan()
        } else {
            scanStatus = "Requesting Bluetooth permission…"
            permissionLauncher.launch(requiredPermissions())
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        val adapter = bluetoothAdapter

        if (adapter == null) {
            scanStatus = "This device does not support Bluetooth."
            return
        }

        if (!adapter.isEnabled) {
            scanStatus = "Bluetooth is turned off. Please enable it."
            return
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            scanStatus = "BLE scanner is unavailable."
            return
        }

        stopBleScan()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        isScanning = true
        scanStatus = if (selectedMac == null) {
            "Scanning for BTHome sensors. Select one from the list."
        } else {
            "Looking for selected sensor: $selectedMac"
        }

        scanner.startScan(null, settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false
    }

    private val scanCallback = object : ScanCallback() {

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            val serviceData =
                result.scanRecord?.getServiceData(bthomeUuid) ?: return

            val mac = result.device.address
            val name = result.scanRecord?.deviceName
                ?: result.device.name
                ?: "BTHome device"

            addOrUpdateSensor(
                DiscoveredSensor(
                    mac = mac,
                    name = name,
                    rssi = result.rssi
                )
            )

            val chosenMac = selectedMac

            if (chosenMac == null) {
                scanStatus =
                    "Found ${discoveredSensors.size} BTHome sensor(s). Select one."
                return
            }

            if (!mac.equals(chosenMac, ignoreCase = true)) {
                return
            }

            parseBthomeData(
                macAddress = mac,
                signalStrength = result.rssi,
                payload = serviceData
            )
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            scanStatus = "BLE scan failed: ${scanErrorName(errorCode)} ($errorCode)"
        }
    }

    private fun addOrUpdateSensor(sensor: DiscoveredSensor) {
        val index = discoveredSensors.indexOfFirst {
            it.mac.equals(sensor.mac, ignoreCase = true)
        }

        if (index >= 0) {
            discoveredSensors[index] = sensor
        } else {
            discoveredSensors.add(sensor)
        }

        val sorted = discoveredSensors.sortedByDescending { it.rssi }
        discoveredSensors.clear()
        discoveredSensors.addAll(sorted)
    }

    private fun selectSensor(sensor: DiscoveredSensor) {
        selectedMac = sensor.mac
        selectedName = sensor.name

        preferences.edit()
            .putString(KEY_SELECTED_MAC, sensor.mac)
            .putString(KEY_SELECTED_NAME, sensor.name)
            .apply()

        temperature = null
        humidity = null
        battery = null
        voltage = null
        rssi = 0
        lastPayloadHex = "Waiting for BTHome data…"
        diagnosticText = ""

        scanStatus = "Selected: ${sensor.name} (${sensor.mac})"
    }

    private fun clearSelectedSensor() {
        preferences.edit()
            .remove(KEY_SELECTED_MAC)
            .remove(KEY_SELECTED_NAME)
            .apply()

        selectedMac = null
        selectedName = null

        temperature = null
        humidity = null
        battery = null
        voltage = null
        rssi = 0
        lastPayloadHex = "No BTHome packet received yet."
        diagnosticText = ""

        scanStatus = "Scanning for BTHome sensors. Select one from the list."
    }

    private fun scanErrorName(errorCode: Int): String {
        return when (errorCode) {
            ScanCallback.SCAN_FAILED_ALREADY_STARTED ->
                "already started"
            ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED ->
                "app registration failed"
            ScanCallback.SCAN_FAILED_INTERNAL_ERROR ->
                "internal error"
            ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED ->
                "feature unsupported"
            ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES ->
                "out of hardware resources"
            ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY ->
                "scanning too frequently"
            else ->
                "unknown error"
        }
    }

    private fun parseBthomeData(
        macAddress: String,
        signalStrength: Int,
        payload: ByteArray
    ) {
        if (payload.isEmpty()) return

        val payloadHex = payload.joinToString(" ") {
            "%02X".format(it.toInt() and 0xFF)
        }

        lastPayloadHex = payloadHex
        Log.d(LOG_TAG, "BTHome from $macAddress: $payloadHex")

        val deviceInfo = payload[0].toInt() and 0xFF
        val encrypted = (deviceInfo and 0x01) != 0
        val version = (deviceInfo shr 5) and 0x07

        if (version != 2) {
            diagnosticText = "Unsupported BTHome version: $version"
            scanStatus = "Unsupported BTHome version: $version"
            return
        }

        if (encrypted) {
            diagnosticText = "Encrypted BTHome v2 packet."
            scanStatus = "Selected sensor uses encrypted BTHome packets."
            return
        }

        var index = 1
        var parsedTemperature: Float? = null
        var parsedHumidity: Float? = null
        var parsedBattery: Int? = null
        var parsedVoltage: Float? = null
        var unknownObjectId: Int? = null

        while (index < payload.size) {
            val objectId = payload[index].toInt() and 0xFF
            index += 1

            when (objectId) {
                0x00 -> {
                    // Packet ID / event counter: uint8
                    if (index >= payload.size) {
                        diagnosticText = "Truncated packet ID field."
                        break
                    }
                    index += 1
                }

                0x01 -> {
                    // Battery: uint8, percent
                    if (index >= payload.size) {
                        diagnosticText = "Truncated battery field."
                        break
                    }
                    parsedBattery = payload[index].toInt() and 0xFF
                    index += 1
                }

                0x02 -> {
                    // Temperature: signed int16 LE, 0.01 °C
                    if (index + 1 >= payload.size) {
                        diagnosticText = "Truncated temperature field."
                        break
                    }

                    val low = payload[index].toInt() and 0xFF
                    val high = payload[index + 1].toInt() and 0xFF
                    val rawTemperature = ((high shl 8) or low).toShort()

                    parsedTemperature = rawTemperature * 0.01f
                    index += 2
                }

                0x03 -> {
                    // Humidity: uint16 LE, 0.01 %
                    if (index + 1 >= payload.size) {
                        diagnosticText = "Truncated humidity field."
                        break
                    }

                    val low = payload[index].toInt() and 0xFF
                    val high = payload[index + 1].toInt() and 0xFF
                    val rawHumidity = (high shl 8) or low

                    parsedHumidity = rawHumidity * 0.01f
                    index += 2
                }

                0x0C -> {
                    // Voltage: uint16 LE, 0.001 V
                    if (index + 1 >= payload.size) {
                        diagnosticText = "Truncated voltage field."
                        break
                    }

                    val low = payload[index].toInt() and 0xFF
                    val high = payload[index + 1].toInt() and 0xFF
                    val rawVoltage = (high shl 8) or low

                    parsedVoltage = rawVoltage * 0.001f
                    index += 2
                }

                else -> {
                    unknownObjectId = objectId
                    diagnosticText =
                        "Unsupported BTHome object: 0x%02X".format(objectId)
                    Log.w(
                        LOG_TAG,
                        "Unsupported BTHome object: 0x%02X; payload: %s"
                            .format(objectId, payloadHex)
                    )
                    break
                }
            }

            if (unknownObjectId != null) {
                break
            }
        }

        rssi = signalStrength

        parsedTemperature?.let { temperature = it }
        parsedHumidity?.let { humidity = it }
        parsedBattery?.let { battery = it }
        parsedVoltage?.let { voltage = it }

        if (parsedTemperature != null || parsedHumidity != null) {
            diagnosticText = if (unknownObjectId != null) {
                "Data decoded; packet also has 0x%02X.".format(unknownObjectId)
            } else {
                "BTHome v2 decoded successfully."
            }

            scanStatus = "Fresh data received from selected sensor."

            logToFile(
                mac = macAddress,
                temp = parsedTemperature,
                hum = parsedHumidity,
                batteryPercent = parsedBattery,
                voltageValue = parsedVoltage,
                signalStrength = signalStrength,
                payloadHex = payloadHex
            )
        } else if (unknownObjectId != null) {
            scanStatus = diagnosticText
        } else {
            diagnosticText = "No temperature or humidity fields in this packet."
            scanStatus = diagnosticText
        }
    }

    private fun logToFile(
        mac: String,
        temp: Float?,
        hum: Float?,
        batteryPercent: Int?,
        voltageValue: Float?,
        signalStrength: Int,
        payloadHex: String
    ) {
        try {
            val documentsDirectory =
                getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return

            val logFile = File(documentsDirectory, "bthome_logs.txt")

            val timestamp = SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.getDefault()
            ).format(Date())

            val temperatureText = temp?.let {
                String.format(Locale.US, "%.2f °C", it)
            } ?: "n/a"

            val humidityText = hum?.let {
                String.format(Locale.US, "%.2f %%", it)
            } ?: "n/a"

            val batteryText = batteryPercent?.let {
                "$it %"
            } ?: "n/a"

            val voltageText = voltageValue?.let {
                String.format(Locale.US, "%.3f V", it)
            } ?: "n/a"

            val line =
                "[$timestamp] MAC: $mac | Temp: $temperatureText | " +
                    "Humidity: $humidityText | Battery: $batteryText | " +
                    "Voltage: $voltageText | RSSI: $signalStrength dBm | " +
                    "Payload: $payloadHex\n"

            logFile.appendText(line)
            Log.d(LOG_TAG, "Saved: $line")
        } catch (exception: Exception) {
            Log.e(LOG_TAG, "Could not write log file.", exception)
            scanStatus = "Data received, but saving the log failed."
        }
    }

    companion object {
        private const val LOG_TAG = "MijiaLogger"

        private const val PREFS_NAME = "mijia_logger_settings"
        private const val KEY_SELECTED_MAC = "selected_sensor_mac"
        private const val KEY_SELECTED_NAME = "selected_sensor_name"
    }
}

@Composable
private fun LoggerScreen(
    status: String,
    selectedMac: String?,
    selectedName: String?,
    sensors: List<DiscoveredSensor>,
    temperature: Float?,
    humidity: Float?,
    battery: Int?,
    voltage: Float?,
    rssi: Int,
    isScanning: Boolean,
    payloadHex: String,
    diagnosticText: String,
    onSelectSensor: (DiscoveredSensor) -> Unit,
    onChangeSensor: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Mijia Temp Logger",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = status,
            fontSize = 16.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (selectedMac == null) {
            SensorSelectionScreen(
                sensors = sensors,
                isScanning = isScanning,
                onSelectSensor = onSelectSensor
            )
        } else {
            SelectedSensorScreen(
                selectedName = selectedName ?: "BTHome sensor",
                selectedMac = selectedMac,
                temperature = temperature,
                humidity = humidity,
                battery = battery,
                voltage = voltage,
                rssi = rssi,
                payloadHex = payloadHex,
                diagnosticText = diagnosticText,
                onChangeSensor = onChangeSensor
            )
        }
    }
}

@Composable
private fun SensorSelectionScreen(
    sensors: List<DiscoveredSensor>,
    isScanning: Boolean,
    onSelectSensor: (DiscoveredSensor) -> Unit
) {
    if (isScanning) {
        CircularProgressIndicator(modifier = Modifier.size(36.dp))
        Spacer(modifier = Modifier.height(16.dp))
    }

    Text(
        text = "Choose a BTHome sensor",
        fontSize = 20.sp,
        fontWeight = FontWeight.Medium
    )

    Spacer(modifier = Modifier.height(12.dp))

    if (sensors.isEmpty()) {
        Text(
            text = "No BTHome transmitters found yet.",
            color = Color.Gray
        )
        return
    }

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(
            items = sensors,
            key = { sensor -> sensor.mac }
        ) { sensor ->
            ListItem(
                headlineContent = {
                    Text(sensor.name)
                },
                supportingContent = {
                    Text("${sensor.mac}  •  ${sensor.rssi} dBm")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onSelectSensor(sensor)
                    }
            )

            Divider()
        }
    }
}

@Composable
private fun SelectedSensorScreen(
    selectedName: String,
    selectedMac: String,
    temperature: Float?,
    humidity: Float?,
    battery: Int?,
    voltage: Float?,
    rssi: Int,
    payloadHex: String,
    diagnosticText: String,
    onChangeSensor: () -> Unit
) {
    Text(
        text = "Selected: $selectedName",
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium
    )

    Text(
        text = selectedMac,
        fontSize = 14.sp,
        color = Color.DarkGray
    )

    Spacer(modifier = Modifier.height(16.dp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = temperature?.let {
                    String.format(Locale.getDefault(), "%.1f °C", it)
                } ?: "— °C",
                fontSize = 58.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFE91E63)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = humidity?.let {
                    String.format(Locale.getDefault(), "%.1f %%", it)
                } ?: "— %",
                fontSize = 36.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF2196F3)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Signal: $rssi dBm",
                fontSize = 14.sp,
                color = Color.DarkGray
            )

            battery?.let {
                Text(
                    text = "Battery: $it %",
                    fontSize = 14.sp,
                    color = Color.DarkGray
                )
            }

            voltage?.let {
                Text(
                    text = String.format(Locale.getDefault(), "Voltage: %.3f V", it),
                    fontSize = 14.sp,
                    color = Color.DarkGray
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    if (diagnosticText.isNotEmpty()) {
        Text(
            text = diagnosticText,
            fontSize = 13.sp,
            color = Color.DarkGray
        )

        Spacer(modifier = Modifier.height(8.dp))
    }

    Text(
        text = "BTHome payload:",
        fontSize = 12.sp,
        color = Color.Gray
    )

    Text(
        text = payloadHex,
        fontSize = 12.sp,
        color = Color.Gray
    )

    Spacer(modifier = Modifier.height(20.dp))

    Button(onClick = onChangeSensor) {
        Text("Change sensor")
    }
}
