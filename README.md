# BTHome Logger

**BTHome Logger** is a lightweight Android application for receiving and
displaying live data from Bluetooth Low Energy (BLE) sensors that broadcast
**unencrypted BTHome v1 or BTHome v2** advertisements.

It scans nearby BLE advertisements, displays supported sensor readings, stores
a local text log, and can show the latest readings in an Android home-screen
widget. The app works locally: it does not require a cloud account, an
Internet connection, pairing, or a permanent GATT connection to a sensor.

> **Status:** Work in progress / experimental project.

## Supported BTHome advertisements

BTHome Logger detects and logs only **unencrypted** BTHome BLE
advertisements:

- BTHome v1, identified by service UUID `0x181C`
- BTHome v2, identified by service UUID `0xFCD2` with encryption disabled

Encrypted BTHome advertisements are intentionally ignored. The app does not
request, store, or use encryption keys, and it does not decrypt sensor data.

## Background

The original target sensor for this project is the inexpensive
**Xiaomi Mi Temperature and Humidity Monitor 2**, commonly sold as:

- Xiaomi Mijia `LYWSD03MMC`
- Xiaomi Mi `LYWSD03MMC`
- Xiaomi Temperature & Humidity Monitor 2

The sensor can be reflashed with
[PVVX ATC MiThermometer firmware](https://pvvx.github.io/ATC_MiThermometer/).
PVVX firmware can be installed over Bluetooth with the web-based
[Telink BLE OTA Flasher](https://pvvx.github.io/ATC_MiThermometer/TelinkMiFlasher.html).

BTHome is an open Bluetooth Low Energy format for broadcasting sensor data.
A sensor periodically sends short advertising packets. The phone passively
listens for them; it does not pair with or configure the sensor.

This has several useful properties:

- No cloud account is required
- No Xiaomi app is required
- No pairing is required
- No permanent BLE connection is required
- Multiple receivers can listen to the same sensor
- The sensor remains low-power
- The data stays entirely local

## Supported readings

The application currently displays and logs supported temperature, humidity,
battery, and voltage measurements when they are included in a compatible
unencrypted BTHome advertisement.

The main UI shows:

- Temperature
- Humidity
- Battery percentage, when available
- Battery voltage, when available
- Bluetooth RSSI signal strength
- The most recently received payload in hexadecimal form
- Decoder diagnostic status

Not every BTHome device broadcasts the same object types. Unsupported object
types are not decoded.

## Features

- Scans nearby BLE sensors broadcasting unencrypted BTHome v1 or v2 data
- Ignores encrypted BTHome advertisements
- Shows discovered sensor names, MAC addresses, protocol version, and RSSI
- Lets you select a sensor from the detected-device list
- Remembers the selected sensor after the app restarts
- Shows live supported measurements
- Saves decoded readings to a local text log
- Includes an Android home-screen widget for recent readings
- Uses only local Bluetooth Low Energy scanning
- Requires no account, cloud service, Internet connection, or Xiaomi app

## Requirements

### Supported sensors

BTHome Logger is intended for BLE sensors configured to broadcast
**unencrypted BTHome v1 or BTHome v2** data.

The main tested target is:

```text
Xiaomi Mijia LYWSD03MMC
```

with PVVX / ATC MiThermometer firmware configured for an unencrypted BTHome
advertising mode.

Other BTHome-compatible sensors may work when they transmit supported
measurement objects.

### Android device

- Android 8.0 or newer
- Bluetooth enabled
- Android 12 and newer: Nearby devices Bluetooth permission
- The sensor must be within Bluetooth range

## Configure a sensor

> Flashing custom firmware is done at your own risk. Read the PVVX
> documentation carefully before proceeding.

1. Open the
   [PVVX ATC MiThermometer project page](https://pvvx.github.io/ATC_MiThermometer/).
2. Open the
   [Telink BLE OTA Flasher](https://pvvx.github.io/ATC_MiThermometer/TelinkMiFlasher.html).
3. Flash compatible PVVX firmware to the Xiaomi `LYWSD03MMC`.
4. Open the sensor configuration page.
5. Select an unencrypted BTHome v1 or BTHome v2 advertising mode.
6. Make sure encryption is disabled.
7. Optionally configure a recognisable BLE name, for example:

   ```text
   ATC_Fridge
   ATC_Room
   ATC_Bedroom
   ```

A descriptive BLE name makes selecting the correct sensor easier.

## Installation

### Download a prebuilt APK

1. Open the repository's **Actions** tab.
2. Open the latest successful Android build workflow.
3. Download the APK artifact.
4. Extract the downloaded ZIP archive.
5. Install the APK on your Android device.

Android may ask you to allow installations from the browser or file manager
used to open the APK.

### Build from source

Requirements:

- JDK 17
- Android SDK with API level 34
- The included Gradle Wrapper
- Android Studio is recommended

Build a debug APK:

```bash
./gradlew assembleDebug --no-daemon
```

The generated APK is normally located at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Usage

1. Install and open **BTHome Logger**.
2. Enable Bluetooth if requested.
3. Grant the Bluetooth / Nearby devices permission.
4. Wait while the app scans for unencrypted BTHome v1 and v2 transmitters.
5. Select the required sensor from the list.
6. Wait for the next advertising packet.
7. The main screen shows available sensor readings, RSSI, payload, and
   decoder status.
8. Use **Change sensor** to return to the sensor-selection screen.

## Local log file

Every successfully decoded measurement is appended to:

```text
bthome_logs.txt
```

Example entry:

```text
[2026-10-12 00:00:00] BTHome v2 | MAC: A4:C1:38:05:06:63 | Temp: 4.25 °C | Humidity: 74.60% | Battery: 87% | Voltage: 2.945 V | RSSI: -49 dBm | Payload: 40 00 01 57 02 A9 01 03 24 1D 0C 81 0B
```

The file is stored in the app-specific Documents directory:

```text
Android/data/io.github.steanlab.bthomev2logger/files/Documents/bthome_logs.txt
```

Depending on Android version and file manager, access to `Android/data` can be
restricted by scoped-storage rules.

## Home-screen widget

The project includes a home-screen widget that displays recently saved
readings from up to two sensors.

To add it on a Pixel launcher:

1. Long-press an empty area of the home screen.
2. Choose **Widgets**.
3. Find **BTHome Logger**.
4. Drag the widget to the home screen.
5. Open the app and wait for a new packet from the selected sensor.

The widget displays cached data. It does not scan Bluetooth independently.

## Privacy

BTHome Logger operates locally.

- No account is required
- No analytics are included
- No data is sent to a server
- No cloud service is required
- BLE data is processed on the Android device
- Logs are stored locally
- Encryption keys are not requested, stored, or used

## Limitations

- Only unencrypted BTHome v1 and v2 advertisements are processed
- Encrypted BTHome advertisements are ignored
- Only supported measurement object types are decoded
- The app currently focuses on temperature, humidity, battery level, and
  battery voltage
- The app receives live data only while BLE scanning is active
- The widget shows saved readings; it is not an independent BLE scanner
- Continuous background scanning is not implemented
- BLE MAC addresses may not be permanently stable for all sensors and firmware
- Some Android devices may report BLE scan results differently

## Planned features

- Explicit selection and pinning of two sensors for the widget
- Improved multi-sensor dashboard
- Foreground-service mode for continuous background BLE monitoring
- Widget freshness indicator with colour-coded age states
- Notification alerts for temperature or humidity thresholds
- Configurable temperature units: Celsius and Fahrenheit
- CSV export
- Share/export local logs
- Support for additional unencrypted BTHome object types

## Author

Created and maintained by **Siarhei Besarab**
([@steanlab](https://github.com/steanlab)), 2026.