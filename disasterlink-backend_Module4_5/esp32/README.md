# DisasterLink — Module 3 Offline Hardware / LoRa

## What this module implements

```text
Citizen Android app
      | BLE / Nordic UART
      v
Field ESP32 + Ra-02
      | LoRa 433 MHz, HMAC + ACK + NVS outbox
      v
Gateway ESP32 + Ra-02
      | Wi-Fi/LAN only (internet is NOT required)
      v
Node/Express backend
      v
SQLite
      v
Authority / Rescue app
```

The gateway can receive and persist a report even when its Wi-Fi/backend is temporarily unavailable. When the LAN/backend becomes reachable again, the gateway flushes its NVS queue.

## Hardware

For the confirmed Ai-Thinker Ra-02 / SX1278 433 MHz modules, use the same wiring on both ESP32 boards:

| Ra-02 | ESP32 |
|---|---|
| VCC | 3V3 |
| GND | GND |
| SCK | GPIO18 |
| MISO | GPIO19 |
| MOSI | GPIO23 |
| NSS/CS | GPIO5 |
| RST | GPIO14 |
| DIO0 | GPIO26 |
| ANT | 433 MHz antenna |

Optional field SOS button: GPIO27 to GND. **Never power Ra-02 from 5V. Keep the antenna connected.**

## Firmware

Install Arduino IDE, the Espressif ESP32 board package, and the **LoRa by Sandeep Mistry** library. Select **ESP32 Dev Module**.

Copy:

```powershell
Copy-Item esp32\field-node\config.h.example esp32\field-node\config.h
Copy-Item esp32\gateway\secrets.h.example esp32\gateway\secrets.h
```

Field `config.h` and gateway `secrets.h` must use the same `LORA_NETWORK_KEY`, frequency and LoRa pins. Gateway `BACKEND_URL` must point to the PC/server LAN IP, not localhost.

## Module 3 BLE contract

Phone writes:

```text
LOCATION|lat|lon
SOS|clientId|governmentId|name|phone|district|lat|lon|message
PING
```

The SOS includes the local Room emergency ID (`clientId`) plus citizen identity fields. The field node converts it to a signed DL2 frame.

## DL2 LoRa frame

```text
DL2|deviceId|sequence|type|clientId|citizenId|name|phone|district|lat|lon|message|hmac
```

The final HMAC is HMAC-SHA256 truncated to the first 8 bytes (16 hex characters). Both boards must use the same network key.

## Backend / SQLite

The gateway calls:

```text
POST /api/gateway/ingest
x-gateway-key: <GATEWAY_KEY>
```

The server writes LoRa SOS reports to `data/disasterlink.db` and stores the reporter name/phone/government ID. Duplicate `(deviceId, sequence)` packets are ignored.

## Test order

### Test A — radio only
1. Disconnect both LoRa modules while flashing if upload is unreliable.
2. Upload `field-node.ino` to the field ESP32.
3. Upload `gateway.ino` to the gateway ESP32.
4. Reconnect both Ra-02 modules.
5. Open both Serial Monitors at 115200.
6. Gateway should show `DisasterLink Gateway READY`. Field node should show `FIELD_NODE_READY|OFFLINE_LORA_READY`.

### Test B — Android -> field -> gateway
1. Start the backend with `npm start`.
2. Make sure the gateway can reach the backend over the same LAN. Internet is not required for this local path.
3. Open Android app and log in.
4. Open **ESP32 Connection** and connect to `DisasterLink Field Node`.
5. Wait for `ESP32 ready`.
6. Press **Send SOS via Field Node**.
7. Field Serial Monitor should show `LoRa TX attempt ...`.
8. Gateway Serial Monitor should show `LoRa RX ...` and `Backend HTTP 201`.
9. Field phone status should eventually show `ACK|SOS|...`.
10. Check the authority/rescue emergency list or SQLite database.

### Test C — prove offline buffering
1. Keep field + gateway powered.
2. Temporarily make the gateway unable to reach the backend/Wi-Fi.
3. Send an SOS from Android.
4. Gateway should say `RX stored in NVS for later delivery`.
5. Restore the gateway LAN/backend connection.
6. Within a few seconds it should say `Queued packet delivered`.

## Important architecture note

This two-board kit is a LoRa star/link, not a multi-hop mesh yet: one field node talks to one gateway. A true multi-hop mesh needs additional field/repeater nodes and a routing/TTL layer. Module 3 establishes the reliable offline BLE -> LoRa -> gateway -> backend transport with persistent buffering.
