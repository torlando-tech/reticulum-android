<p align="center">
  <img src="scripts/rns_logo.png" alt="Reticulum Network Stack" width="256">
</p>

# Reticulum for Android

A shared instance daemon for [Reticulum](https://reticulum.network/) on Android. Runs the [reference Python implementation](https://github.com/markqvist/Reticulum) via [Chaquopy](https://chaquo.com/chaquopy/) as a background service, so other Reticulum apps on the device can share a single network stack without each needing to bundle their own.

Similar in approach to [Columba](https://github.com/liberatedsystems/Columba_Mailing_List), which also embeds the Python RNS stack via Chaquopy. The Kotlin BLE/RNode bridge used here is extracted from Columba as well, since pyjnius is not available in the Chaquopy environment.

Reticulum is a cryptography-based networking protocol for building resilient, decentralized communication systems over any available transport — TCP, UDP, I2P, LoRa (via RNode), or local WiFi/BLE.

## Features

- Shared instance — other RNS apps on the device connect through this one
- Full Reticulum stack running as a foreground service (separate process for clean lifecycle)
- Configure and manage interfaces: TCP client/server, UDP, AutoInterface, I2P, RNode (LoRa)
- RNode setup wizard with frequency/bandwidth/SF/CR configuration
- TCP client wizard with optional SOCKS proxy support (for Tor)
- Live interface stats and traffic monitoring
- Auto-discovery of peers on local networks

## Requirements

- Android 8.0+ (API 26)
- For RNode: a compatible LoRa device connected via USB or Bluetooth

## Building

```
git clone https://github.com/torlando-tech/reticulum-android.git
cd reticulum-android
./gradlew assembleDebug
```

Requires JDK 17. Python 3.11 dependencies (RNS, cryptography) are fetched automatically by Chaquopy during the build.

## License

MIT
