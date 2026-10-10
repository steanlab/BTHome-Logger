package com.example.mijialogger

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelUuid
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var temp by mutableFloatStateOf(0f)
    private var humidity by mutableFloatStateOf(0f)
    private var targetMac by mutableStateOf("")
    private var scanStatus by mutableStateOf("Ожидаем права (Нужен GPS для Bluetooth)...")

    private val UUID_BTHOME = ParcelUuid.fromString("0000FCD2-0000-1000-8000-00805F9B34FB")

    private val reqPerms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { startBLEScan() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppUI(scanStatus, targetMac, temp, humidity)
                }
            }
        }
        val p = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        reqPerms.launch(p)
    }

    @SuppressLint("MissingPermission")
    private fun startBLEScan() {
        scanStatus = "Слушаю эфир..."
        val bm = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val s = bm.adapter?.bluetoothLeScanner
        if (s == null) { scanStatus = "Включите Bluetooth!"; return }

        s.startScan(
            listOf(ScanFilter.Builder().setServiceData(UUID_BTHOME, byteArrayOf(), byteArrayOf()).build()),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            object : ScanCallback() {
                override fun onScanResult(ct: Int, res: ScanResult) {
                    res.scanRecord?.getServiceData(UUID_BTHOME)?.let { parse(res.device.address, it) }
                }
            }
        )
    }

    private fun parse(mac: String, p: ByteArray) {
        if (p.isEmpty() || (p[0].toInt() shr 5 and 0x07) != 2 || (p[0].toInt() and 1) == 1) return
        var i = 1
        while (i < p.size) {
            val o = p[i++].toInt() and 0xFF
            if (o == 0x02 && i + 1 < p.size) {
                temp = ((p[i+1].toInt() shl 8) or (p[i].toInt() and 0xFF)).toShort() * 0.01f
                i += 2
            } else if (o == 0x03 && i + 1 < p.size) {
                humidity = ((p[i+1].toInt() and 0xFF shl 8) or (p[i].toInt() and 0xFF)) * 0.01f
                i += 2
            } else if (o == 0x01) { i++ } else break
        }
        if (temp != 0f) {
            targetMac = mac; scanStatus = "Успешно!"; logData(mac, temp, humidity)
        }
    }

    private fun logData(m: String, t: Float, h: Float) {
        try {
            val d = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "MijiaLog.txt")
                .appendText("[$d] MAC: $m | $t C | $h %\r\n")
        } catch (e: Exception){}
    }
}

@Composable
fun AppUI(st: String, mac: String, t: Float, h: Float) {
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("BTHome Scanner", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(st, color = Color.Gray); Spacer(Modifier.height(30.dp))
        if (mac.isNotEmpty()) {
            Card(elevation = CardDefaults.cardElevation(8.dp), modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "%.1f °C".format(t), fontSize = 54.sp, color = Color(0xFFE91E63), fontWeight = FontWeight.Black)
                    Text(text = "%.1f %%".format(h), fontSize = 34.sp, color = Color(0xFF2196F3))
                    Spacer(Modifier.height(20.dp)); Text("Датчик: $mac", color = Color.DarkGray)
                }
            }
        } else CircularProgressIndicator()
    }
}
