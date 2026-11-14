/*
 * DisasterLink Gateway - Module 3
 * ESP32 + Ai-Thinker Ra-02 / SX1278 433 MHz + Wi-Fi
 *
 * DL2: device/sequence/type + offline citizen identity + coordinates.
 * If the backend is unreachable, the gateway stores the complete packet in
 * Preferences (NVS) and ACKs the field node. When Wi-Fi/backend returns,
 * queued packets are forwarded to /api/gateway/ingest.
 */
#include <SPI.h>
#include <LoRa.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <Preferences.h>
#include <mbedtls/md.h>
#include "secrets.h"

static const uint8_t MAX_QUEUE = 16;
static const unsigned long QUEUE_FLUSH_INTERVAL_MS = 5000;
static const unsigned long WIFI_RETRY_INTERVAL_MS = 10000;
Preferences queuePrefs;
unsigned long lastQueueFlush = 0;
unsigned long lastWifiAttempt = 0;

String hmacTag(const String& value) {
  const mbedtls_md_info_t* info = mbedtls_md_info_from_type(MBEDTLS_MD_SHA256);
  unsigned char digest[32];
  mbedtls_md_context_t context;
  mbedtls_md_init(&context);
  mbedtls_md_setup(&context, info, 1);
  mbedtls_md_hmac_starts(&context, reinterpret_cast<const unsigned char*>(LORA_NETWORK_KEY), strlen(LORA_NETWORK_KEY));
  mbedtls_md_hmac_update(&context, reinterpret_cast<const unsigned char*>(value.c_str()), value.length());
  mbedtls_md_hmac_finish(&context, digest);
  mbedtls_md_free(&context);
  char tag[17];
  for (int i = 0; i < 8; ++i) sprintf(tag + i * 2, "%02x", digest[i]);
  tag[16] = '\0';
  return String(tag);
}

bool constantTimeEquals(const String& a, const String& b) {
  if (a.length() != b.length()) return false;
  uint8_t diff = 0;
  for (size_t i = 0; i < a.length(); ++i) diff |= a[i] ^ b[i];
  return diff == 0;
}

bool validDeviceId(const String& id) {
  if (id.length() < 3 || id.length() > 64) return false;
  for (size_t i = 0; i < id.length(); ++i) {
    char c = id[i];
    if (!isAlphaNumeric(c) && c != ':' && c != '_' && c != '-') return false;
  }
  return true;
}

bool splitFrame(const String& frame, String fields[], int count) {
  int start = 0;
  for (int i = 0; i < count - 1; ++i) {
    int sep = frame.indexOf('|', start);
    if (sep < 0) return false;
    fields[i] = frame.substring(start, sep);
    start = sep + 1;
  }
  fields[count - 1] = frame.substring(start);
  return true;
}

bool validNumber(const String& text, double minValue, double maxValue, double* output) {
  char* end = nullptr;
  double value = strtod(text.c_str(), &end);
  if (end == text.c_str() || *end != '\0' || !isfinite(value) || value < minValue || value > maxValue) return false;
  *output = value;
  return true;
}

String jsonEscape(const String& value) {
  String out;
  out.reserve(value.length() + 8);
  for (size_t i = 0; i < value.length(); ++i) {
    char c = value[i];
    if (c == '"' || c == '\\') { out += '\\'; out += c; }
    else if (c == '\r') out += "\\r";
    else if (c == '\n') out += "\\n";
    else out += c;
  }
  return out;
}

String queueKey(uint8_t index) { return String("q") + String(index); }
uint8_t queueCount() { return static_cast<uint8_t>(queuePrefs.getUChar("count", 0)); }
void setQueueCount(uint8_t value) { queuePrefs.putUChar("count", value); }

bool queueContains(const String& deviceId, uint32_t sequence) {
  uint8_t count = queueCount();
  for (uint8_t i = 0; i < count; ++i) {
    String frame = queuePrefs.getString(queueKey(i).c_str(), "");
    String f[13];
    if (splitFrame(frame, f, 13) && f[0] == "DL2" && f[1] == deviceId && f[2] == String(sequence)) return true;
  }
  return false;
}

bool enqueueFrame(const String& frame, const String& deviceId, uint32_t sequence) {
  if (queueContains(deviceId, sequence)) return true;
  uint8_t count = queueCount();
  if (count >= MAX_QUEUE) return false;
  queuePrefs.putString(queueKey(count).c_str(), frame.c_str());
  setQueueCount(count + 1);
  Serial.printf("Gateway offline queue: %u/%u\n", count + 1, MAX_QUEUE);
  return true;
}

String peekQueue() { return queueCount() ? queuePrefs.getString(queueKey(0).c_str(), "") : ""; }

void popQueue() {
  uint8_t count = queueCount();
  if (!count) return;
  for (uint8_t i = 1; i < count; ++i) {
    String frame = queuePrefs.getString(queueKey(i).c_str(), "");
    queuePrefs.putString(queueKey(i - 1).c_str(), frame.c_str());
  }
  queuePrefs.remove(queueKey(count - 1).c_str());
  setQueueCount(count - 1);
}

void ensureWifi() {
  if (WiFi.status() == WL_CONNECTED) return;
  if (millis() - lastWifiAttempt < WIFI_RETRY_INTERVAL_MS) return;
  lastWifiAttempt = millis();
  Serial.println("Wi-Fi unavailable; retrying...");
  WiFi.disconnect();
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
}

bool postFrameToBackend(const String& frame) {
  if (WiFi.status() != WL_CONNECTED) return false;

  // DL2|device|sequence|type|clientId|citizenId|name|phone|district|lat|lon|message|hmac
  String f[13];
  if (!splitFrame(frame, f, 13) || f[0] != "DL2") return false;
  double lat, lon;
  if (!validNumber(f[9], -90, 90, &lat) || !validNumber(f[10], -180, 180, &lon)) return false;

  HTTPClient http;
  http.setConnectTimeout(4000);
  http.setTimeout(7000);
  if (!http.begin(BACKEND_URL)) return false;
  http.addHeader("Content-Type", "application/json");
  http.addHeader("x-gateway-key", GATEWAY_KEY);

  String payload = String("{\"deviceId\":\"") + jsonEscape(f[1])
    + "\",\"sequence\":" + f[2]
    + ",\"type\":\"" + jsonEscape(f[3])
    + "\",\"clientId\":\"" + jsonEscape(f[4])
    + "\",\"citizenId\":\"" + jsonEscape(f[5])
    + "\",\"reporterName\":\"" + jsonEscape(f[6])
    + "\",\"reporterPhone\":\"" + jsonEscape(f[7])
    + "\",\"district\":\"" + jsonEscape(f[8])
    + "\",\"latitude\":" + String(lat, 6)
    + ",\"longitude\":" + String(lon, 6)
    + ",\"message\":\"" + jsonEscape(f[11]) + "\"}";

  int status = http.POST(payload);
  String response = http.getString();
  Serial.printf("Backend HTTP %d\n", status);
  if (status < 200 || status >= 300) {
    Serial.println("Backend response:");
    Serial.println(response);
  }
  http.end();
  return status >= 200 && status < 300;
}

void sendAck(const String& deviceId, uint32_t sequence) {
  LoRa.idle();
  LoRa.beginPacket();
  LoRa.print(String("ACK|DL2|") + deviceId + "|" + String(sequence));
  LoRa.endPacket();
  Serial.printf("ACK -> %s seq=%u\n", deviceId.c_str(), sequence);
}

void handlePacket(const String& frame) {
  String f[13];
  if (!splitFrame(frame, f, 13)) { Serial.println("RX rejected: bad field count"); return; }

  String unsignedFrame = f[0] + "|" + f[1] + "|" + f[2] + "|" + f[3] + "|"
    + f[4] + "|" + f[5] + "|" + f[6] + "|" + f[7] + "|" + f[8] + "|" + f[9] + "|" + f[10] + "|" + f[11];

  if (f[0] != "DL2" || !validDeviceId(f[1]) ||
      (f[3] != "SOS" && f[3] != "LOCATION") ||
      !constantTimeEquals(hmacTag(unsignedFrame), f[12])) {
    Serial.println("RX rejected: invalid/unauthenticated frame");
    return;
  }

  char* end = nullptr;
  unsigned long long rawSeq = strtoull(f[2].c_str(), &end, 10);
  if (f[2].isEmpty() || *end != '\0' || rawSeq > 0xFFFFFFFFULL) return;
  uint32_t seq = static_cast<uint32_t>(rawSeq);
  double lat, lon;
  if (!validNumber(f[9], -90, 90, &lat) || !validNumber(f[10], -180, 180, &lon)) return;

  if (queueContains(f[1], seq)) {
    sendAck(f[1], seq);
    return;
  }

  // If the backend is reachable, persist there. Otherwise persist locally.
  // In both cases the field node can stop retrying after the ACK.
  if (postFrameToBackend(frame)) {
    sendAck(f[1], seq);
    Serial.println("RX delivered to backend");
  } else if (enqueueFrame(frame, f[1], seq)) {
    sendAck(f[1], seq);
    Serial.println("RX stored in NVS for later delivery");
  } else {
    Serial.println("RX LOST: queue full; NO ACK so field node retries");
  }
}

void flushQueue() {
  if (!queueCount() || WiFi.status() != WL_CONNECTED) return;
  if (millis() - lastQueueFlush < QUEUE_FLUSH_INTERVAL_MS) return;
  lastQueueFlush = millis();

  while (queueCount()) {
    String frame = peekQueue();
    if (frame.isEmpty()) { popQueue(); continue; }
    String f[13];
    if (!splitFrame(frame, f, 13)) { popQueue(); continue; }
    uint32_t seq = static_cast<uint32_t>(strtoul(f[2].c_str(), nullptr, 10));
    if (!postFrameToBackend(frame)) break;
    popQueue();
    sendAck(f[1], seq);
    Serial.printf("Queued packet delivered: %s seq=%u\n", f[1].c_str(), seq);
  }
}

void connectWifiAtStartup() {
  WiFi.mode(WIFI_STA);
  Serial.printf("Connecting to Wi-Fi SSID: %s\n", WIFI_SSID);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - start < 15000) {
    delay(250);
    Serial.print('.');
  }
  Serial.println();
  if (WiFi.status() == WL_CONNECTED) {
    Serial.print("Wi-Fi connected. Gateway IP: ");
    Serial.println(WiFi.localIP());
  } else {
    Serial.println("Wi-Fi unavailable: OFFLINE BUFFERING ENABLED");
  }
}

void setup() {
  Serial.begin(115200);
  delay(500);
  queuePrefs.begin("dl-queue", false);

  SPI.begin(LORA_SCK, LORA_MISO, LORA_MOSI, LORA_NSS);
  LoRa.setPins(LORA_NSS, LORA_RESET, LORA_DIO0);
  if (!LoRa.begin(LORA_FREQUENCY)) {
    Serial.println("LoRa initialization failed - check power, SPI pins and frequency");
    while (true) delay(1000);
  }
  LoRa.enableCrc();
  connectWifiAtStartup();
  Serial.printf("DisasterLink Gateway READY | queue=%u/%u\n", queueCount(), MAX_QUEUE);
}

void loop() {
  ensureWifi();
  flushQueue();
  int packetSize = LoRa.parsePacket();
  if (packetSize > 0) {
    String frame;
    while (LoRa.available()) frame += static_cast<char>(LoRa.read());
    Serial.printf("LoRa RX %d bytes\n", packetSize);
    handlePacket(frame);
  }
  delay(5);
}
