package com.example.mijialogger

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelUuid
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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

class MainActivity : ComponentActivity() {

    private var temperature by mutableStateOf<Float?>(null)
    private var humidity by mutableStateOf<Float?>(null)
    private var battery by mutableStateOf<Int?>(null)
    private var targetMac by mutableStateOf("")
    private var rssi by mutableIntStateOf(0)
    private var scanStatus by mutableStateOf("Preparing Bluetooth scan…")
    private var isScanning by mutableStateOf(false)

    private val bthomeUuid =
        ParcelUuid.fromString("0000FCD2-0000-1000-8000-00805F9B34FB")

    private var bluetoothAdapter: BluetoothAdapter? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (hasRequiredPermissions()) {
                startBleScan()
            } else {
                scanStatus = "Bluetooth permission is required to scan."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                        mac = targetMac,
                        temperature = temperature,
                        humidity = humidity,
                        battery = battery,
                        rssi = rssi,
                        isScanning = isScanning
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

        scanStatus = "Scanning for BTHome advertisements…"
        isScanning = true

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

            parseBthomeData(
                macAddress = result.device.address,
                signalStrength = result.rssi,
                payload = serviceData
            )
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            scanStatus = "BLE scan failed: ${scanErrorName(errorCode)} ($errorCode)"
        }
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

        val deviceInfo = payload[0].toInt() and 0xFF
        val encrypted = (deviceInfo and 0x01) != 0
        val version = (deviceInfo shr 5) and 0x07

        if (version != 2) return

        if (encrypted) {
            scanStatus = "Encrypted BTHome packet received from $macAddress."
            return
        }

        var index = 1
        var parsedTemperature: Float? = null
        var parsedHumidity: Float? = null
        var parsedBattery: Int? = null

        while (index < payload.size) {
            val objectId = payload[index].toInt() and 0xFF
            index += 1

            when (objectId) {
                0x01 -> {
                    if (index >= payload.size) break
                    parsedBattery = payload[index].toInt() and 0xFF
                    index += 1
                }

                0x02 -> {
                    if (index + 1 >= payload.size) break

                    val low = payload[index].toInt() and 0xFF
                    val high = payload[index + 1].toInt() and 0xFF

                    val rawTemperature = ((high shl 8) or low).toShort()
                    parsedTemperature = rawTemperature * 0.01f
                    index += 2
                }

                0x03 -> {
                    if (index + 1 >= payload.size) break

                    val low = payload[index].toInt() and 0xFF
                    val high = payload[index + 1].toInt() and 0xFF

                    val rawHumidity = (high shl 8) or low
                    parsedHumidity = rawHumidity * 0.01f
                    index += 2
                }

                else -> {
                    Log.d(
                        LOG_TAG,
                        "Unknown BTHome object 0x${objectId.toString(16)}; packet ignored."
                    )
                    return
                }
            }
        }

        if (parsedTemperature == null && parsedHumidity == null) return

        targetMac = macAddress
        rssi = signalStrength

        parsedTemperature?.let { temperature = it }
        parsedHumidity?.let { humidity = it }
        parsedBattery?.let { battery = it }

        scanStatus = "Fresh BTHome data received."
        logToFile(
            mac = macAddress,
            temp = parsedTemperature,
            hum = parsedHumidity,
            batteryPercent = parsedBattery,
            signalStrength = signalStrength
        )
    }

    private fun logToFile(
        mac: String,
        temp: Float?,
        hum: Float?,
        batteryPercent: Int?,
        signalStrength: Int
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

            val logLine =
                "[$timestamp] MAC: $mac | Temp: $temperatureText | " +
                    "Humidity: $humidityText | Battery: $batteryText | " +
                    "RSSI: $signalStrength dBm\n"

            logFile.appendText(logLine)
            Log.d(LOG_TAG, "Saved: $logLine")
        } catch (exception: Exception) {
            Log.e(LOG_TAG, "Could not write log file.", exception)
            scanStatus = "Data received, but file logging failed."
        }
    }

    companion object {
        private const val LOG_TAG = "MijiaLogger"
    }
}

@Composable
private fun LoggerScreen(
    status: String,
    mac: String,
    temperature: Float?,
    humidity: Float?,
    battery: Int?,
    rssi: Int,
    isScanning: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
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

        Spacer(modifier = Modifier.height(30.dp))

        if (mac.isEmpty()) {
            if (isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(50.dp))
            }
            return@Column
        }

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
                horizontalAlignment = Alignment.CenterHorizontally
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
                    text = "Sensor: $mac",
                    fontSize = 14.sp,
                    color = Color.DarkGray
                )

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
            }
        }
    }
}
