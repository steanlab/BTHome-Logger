# BTHomeV2 Logger

**BTHomeV2 Logger** is a lightweight Android application for receiving and displaying live temperature and humidity data from Bluetooth Low Energy (BLE) sensors broadcasting in the **BTHome v2** format. The app was created for a simple everyday reason: to check the temperature inside a refrigerator without getting out of bed. It scans nearby BLE advertisements, decodes BTHome v2 measurements, displays the latest values in the app, stores a local text log, and can show the latest readings in an Android home-screen widget.

> **Status:** Work in progress / experimental project.

## Background

I bought an inexpensive **Xiaomi Mi Temperature and Humidity Monitor 2**, usually sold as:

- Xiaomi Mijia `LYWSD03MMC`
- Xiaomi Mi `LYWSD03MMC`
- Xiaomi Temperature & Humidity Monitor 2

The original firmware is designed for Xiaomi's ecosystem. However, this sensor can be reflashed with the excellent [PVVX ATC MiThermometer firmware](https://pvvx.github.io/ATC_MiThermometer/). The firmware can be installed over Bluetooth using the web-based [Telink BLE OTA Flasher](https://pvvx.github.io/ATC_MiThermometer/TelinkMiFlasher.html). After flashing, the sensor can be configured to use **BTHome v2** advertising. 

BTHome is an open Bluetooth Low Energy format for broadcasting sensor data. Instead of requiring a phone to pair with the sensor or maintain a GATT connection, the sensor periodically transmits short BLE advertising packets into the surrounding area. A compatible receiver can passively listen for those packets and decode their contents. This approach has several useful properties:

- No cloud account is required.
- No Xiaomi app is required.
- No pairing is required.
- No permanent BLE connection is required.
- Multiple receivers can listen to the same sensor.
- The sensor can remain low-power because it only broadcasts short packets.
- The data can stay entirely local.

PVVX firmware supports several advertising formats, including Xiaomi/Mi Beacon, ATC, custom PVVX and BTHome v2. This project focuses specifically on **unencrypted BTHome v2** advertising packets. I could not find a simple Android application that continuously decodes these packets locally and presents the readings in a convenient home-screen widget. So I decided to make one.

## What BTHome v2 broadcasts

BTHome v2 is a BLE advertising format identified by the 16-bit service UUID:

```text
0xFCD2
```

A BTHome v2 packet contains a device-information byte followed by one or more typed measurement objects.

BTHomeV2 Logger currently recognizes these common fields:

| BTHome object ID | Measurement | Format |
|---:|---|---|
| `0x00` | Packet ID / counter | Unsigned 8-bit integer |
| `0x01` | Battery level | Unsigned 8-bit percentage |
| `0x02` | Temperature | Signed 16-bit little-endian value × 0.01 °C |
| `0x03` | Humidity | Unsigned 16-bit little-endian value × 0.01 % |
| `0x0C` | Battery voltage | Unsigned 16-bit little-endian value × 0.001 V |

For example, a sensor can advertise:

```text
Temperature: 4.25 °C
Humidity:    74.60%
Battery:     87%
Voltage:     2.945 V
```

The application receives these broadcasts passively. It does not connect to, pair with, configure, or modify the sensor.

## Features

- Scans for nearby BLE devices advertising BTHome v2 service data.
- Detects BTHome v2 service UUID `0xFCD2`.
- Shows discovered sensor names, MAC addresses and RSSI values.
- Lets you select a sensor from the detected-device list.
- Remembers the selected sensor after the application is restarted.
- Displays live temperature and humidity measurements.
- Displays battery percentage when it is included in the advertising packet.
- Displays battery voltage when the sensor transmits BTHome object `0x0C`.
- Displays Bluetooth RSSI signal strength.
- Detects encrypted BTHome packets and reports that they cannot be decoded yet.
- Displays the last received BTHome payload in hexadecimal form for troubleshooting.
- Saves received readings to a local text log.
- Includes an Android home-screen widget for viewing recent readings.
- Uses only local Bluetooth Low Energy scanning.
- Does not require an account, cloud service, Internet connection or Xiaomi application.

## Screenshots

Add screenshots here when available.

```text
screenshots/
├── sensor-selection.png
├── live-readings.png
└── home-screen-widget.png
```

## Requirements

### Supported sensor

The application is intended for BLE temperature/humidity sensors configured to broadcast **unencrypted BTHome v2** data.

The main tested target is:

```text
Xiaomi Mijia LYWSD03MMC
```

with PVVX / ATC MiThermometer firmware configured to use:

```text
Advertising type: BTHome v2
```

Other BTHome v2-compatible sensors may also work if they broadcast the supported object types.

### Android device

- Android 8.0 or newer is recommended.
- Android 12 and newer require the `Nearby devices` Bluetooth permission.
- The app has been tested on a Google Pixel device running Android 17 beta.
- Bluetooth must be enabled.
- The sensor must be within Bluetooth range.

## Flashing and configuring the sensor

> Flashing custom firmware is done at your own risk. Read the PVVX documentation carefully before proceeding.

1. Open the [PVVX ATC MiThermometer project page](https://pvvx.github.io/ATC_MiThermometer/).
2. Open the [Telink BLE OTA Flasher](https://pvvx.github.io/ATC_MiThermometer/TelinkMiFlasher.html).
3. Flash compatible PVVX firmware to the Xiaomi `LYWSD03MMC`.
4. Open the sensor configuration page.
5. Set the advertising type to:

   ```text
   BTHome v2
   ```

6. For the current version of BTHomeV2 Logger, make sure that encryption is disabled.
7. Optionally configure a meaningful BLE name, such as:

   ```text
   ATC_Fridge
   ATC_Room
   ATC_Bedroom
   ```

A descriptive BLE name makes it easier to identify the correct sensor in the application.

## Installation

### Download a prebuilt APK

1. Open the repository's **Actions** tab.
2. Open the latest successful Android build workflow.
3. Download the artifact:

   ```text
   BTHomeV2-Logger
   ```

4. Extract the ZIP archive.
5. Install the APK on your Android device.

Android may ask for permission to install applications from the browser or file manager you use.

### Build from source

Requirements:

- JDK 17
- Android SDK with API level 34
- Gradle 8.5 or a compatible Gradle wrapper
- Android Studio is recommended

Build a debug APK:

```bash
gradle assembleDebug --no-daemon
```

The generated APK is normally located at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Usage

1. Install and open **BTHomeV2 Logger**.
2. Enable Bluetooth if requested.
3. Grant the Bluetooth / Nearby devices permission.
4. Wait while the app scans for BTHome v2 transmitters.
5. Select the required sensor from the list.
6. Wait for the next advertising packet.
7. The main screen will show:

   - Temperature
   - Humidity
   - Battery percentage, if broadcast
   - Battery voltage, if broadcast
   - RSSI signal level
   - Latest BTHome payload
   - Decoder diagnostic status

8. Use **Change sensor** to return to the sensor-selection screen.

## Local log file

Every successfully decoded measurement is appended to a text file:

```text
bthome_logs.txt
```

Example entry:

```text
[2026-10-11 14:32:18] MAC: A4:C1:38:05:06:63 | Temp: 4.25 °C | Humidity: 74.60% | Battery: 87% | Voltage: 2.945 V | RSSI: -49 dBm | Payload: 40 00 01 57 02 A9 01 03 24 1D 0C 81 0B
```

The file is stored in the app-specific Documents directory:

```text
Android/data/com.example.mijialogger/files/Documents/bthome_logs.txt
```

Depending on the Android version and file manager, access to `Android/data` may be restricted by scoped-storage rules.

## Home-screen widget

The project includes a home-screen widget intended to show the latest saved readings from up to two sensors.

To add it on a Pixel launcher:

1. Long-press an empty area of the home screen.
2. Choose **Widgets**.
3. Find **BTHomeV2 Logger**.
4. Drag the widget to the home screen.
5. Open the app and wait for a new packet from the selected sensor.

The widget displays the latest cached data. It does not scan Bluetooth by itself.

## Privacy

BTHomeV2 Logger is designed to operate locally.

- No account is required.
- No analytics are included.
- No data is sent to a server.
- No cloud service is required.
- BLE data is processed on the Android device.
- Logs are stored locally on the device.

## Limitations

- The current implementation supports only unencrypted BTHome v2 packets.
- BTHome packets using unsupported object IDs may be shown as diagnostic messages instead of fully decoded measurements.
- The app currently focuses on temperature, humidity, battery level and battery voltage.
- The app currently receives live data while BLE scanning is active.
- The widget shows saved readings; it is not an independent BLE scanner.
- Continuous background scanning is not implemented yet.
- BLE MAC addresses may not be permanently stable for all devices and firmware configurations.
- Some Android devices or vendor firmware may handle BLE scan results differently.

## Planned features

- Explicit selection and pinning of two sensors for the widget.
- Improved multi-sensor dashboard.
- Foreground-service mode for continuous background BLE monitoring.
- Widget freshness indicator with color-coded age states.
- Notification alerts for temperature or humidity thresholds.
- Configurable temperature units: Celsius and Fahrenheit.
- CSV export.
- Share/export local logs.
- Support for additional
