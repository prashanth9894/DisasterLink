/*
 * DisasterLink Field Node - Module 3
 * ESP32 + Ai-Thinker Ra-02 / SX1278 433 MHz + BLE NUS
 *
 * BLE: Android -> field node. LoRa: field node -> gateway.
 * No phone internet or Wi-Fi is required for the SOS relay.
 * Outbound frames are stored in ESP32 NVS when no gateway ACK arrives.
 */
#include <SPI.h>
#include <LoRa.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <Preferences.h>
#include <LittleFS.h>
#include <mbedtls/md.h>
#include <mbedtls/base64.h>
#include "config.h"

static const char* NUS_SERVICE_UUID = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E";
static const char* NUS_RX_UUID = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E";
static const char* NUS_TX_UUID = "6E400003-B5A3-F393-E0A9-E50E24DCCA9E";
static const uint8_t MAX_OUTBOX = 8;
static const unsigned long ACK_TIMEOUT_MS = 3000;
static const unsigned long OUTBOX_RETRY_MS = 15000;

BLECharacteristic* txCharacteristic = nullptr;
Preferences outboxPrefs;
portMUX_TYPE commandMux = portMUX_INITIALIZER_UNLOCKED;
volatile bool commandReady = false;
String pendingCommand;

String lastUserId = "ANONYMOUS";
String lastUserName = "";
String lastUserPhone = "";
String lastDistrict = "";
String lastClientId = "";
double lastLatitude = 0;
double lastLongitude = 0;
bool hasLocation = false;
uint32_t nextSequence = 0;
unsigned long lastButtonAt = 0;
unsigned long lastOutboxAttempt = 0;
File evidenceWriteFile;
static const size_t MAX_EVIDENCE_BYTES = 184320;
static const size_t EVIDENCE_CHUNK_BYTES = 96;

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

String cleanField(String value, size_t maxLen) {
  value.replace("|", "/");
  value.replace("\r", " ");
  value.replace("\n", " ");
  value.replace("\"", "'");
  value.trim();
  if (value.length() > maxLen) value = value.substring(0, maxLen);
  return value;
}

void notifyPhone(const String& value) {
  if (txCharacteristic) {
    txCharacteristic->setValue(value.c_str());
    txCharacteristic->notify();
  }
  Serial.println(value);
}

bool parseCoordinate(const String& text, double* output) {
  char* end = nullptr;
  double value = strtod(text.c_str(), &end);
  if (end == text.c_str() || *end != '\0' || !isfinite(value)) return false;
  *output = value;
  return true;
}

bool parseLocationCommand(const String& command) {
  int first = command.indexOf('|');
  int second = command.indexOf('|', first + 1);
  if (first < 0 || second < 0 || command.indexOf('|', second + 1) >= 0) return false;
  double lat, lon;
  if (!parseCoordinate(command.substring(first + 1, second), &lat) || !parseCoordinate(command.substring(second + 1), &lon)) return false;
  if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return false;
  lastLatitude = lat;
  lastLongitude = lon;
  hasLocation = true;
  return true;
}

// SOS|clientId|governmentId|name|phone|district|lat|lon|message
bool parseSosCommand(const String& command, String* clientId, String* userId, String* name, String* phone,
                     String* district, double* lat, double* lon, String* message) {
  String f[9];
  int start = 0;
  for (int i = 0; i < 8; ++i) {
    int sep = command.indexOf('|', start);
    if (sep < 0) return false;
    f[i] = command.substring(start, sep);
    start = sep + 1;
  }
  f[8] = command.substring(start);
  if (f[0] != "SOS" || f[1].isEmpty() || f[2].isEmpty() || f[3].isEmpty() || f[6].isEmpty() || f[7].isEmpty()) return false;
  if (!parseCoordinate(f[6], lat) || !parseCoordinate(f[7], lon)) return false;
  if (*lat < -90 || *lat > 90 || *lon < -180 || *lon > 180) return false;
  *clientId = cleanField(f[1], 32);
  *userId = cleanField(f[2], 20);
  *name = cleanField(f[3], 30);
  *phone = cleanField(f[4], 16);
  *district = cleanField(f[5], 20);
  *message = cleanField(f[8], 40);
  return true;
}

// DL2|device|sequence|type|citizenId|name|phone|district|lat|lon|message|hmac
String makeFrame(const String& eventType, uint32_t sequence, const String& clientId, const String& userId,
                 const String& name, const String& phone, const String& district,
                 double lat, double lon, const String& message) {
  // Compact DL2 frame so normal SOS packets remain below the LoRa payload limit.
  String unsignedFrame = String("DL2|") + DEVICE_ID + "|" + String(sequence) + "|" + eventType
    + "|" + cleanField(clientId, 32) + "|" + cleanField(userId, 20)
    + "|" + cleanField(name, 30) + "|" + cleanField(phone, 16) + "|" + cleanField(district, 20)
    + "|" + String(lat, 6) + "|" + String(lon, 6) + "|" + cleanField(message, 40);
  return unsignedFrame + "|" + hmacTag(unsignedFrame);
}

uint8_t outboxCount() { return static_cast<uint8_t>(outboxPrefs.getUChar("count", 0)); }
void setOutboxCount(uint8_t count) { outboxPrefs.putUChar("count", count); }
String outboxKey(uint8_t index) { return String("m") + String(index); }

bool queueFrame(const String& frame) {
  uint8_t count = outboxCount();
  if (count >= MAX_OUTBOX) return false;
  outboxPrefs.putString(outboxKey(count).c_str(), frame.c_str());
  setOutboxCount(count + 1);
  return true;
}

String peekFrame() { return outboxCount() ? outboxPrefs.getString(outboxKey(0).c_str(), "") : ""; }

void popFrame() {
  uint8_t count = outboxCount();
  if (!count) return;
  for (uint8_t i = 1; i < count; ++i) {
    String v = outboxPrefs.getString(outboxKey(i).c_str(), "");
    outboxPrefs.putString(outboxKey(i - 1).c_str(), v.c_str());
  }
  outboxPrefs.remove(outboxKey(count - 1).c_str());
  setOutboxCount(count - 1);
}

bool waitForAck(uint32_t sequence) {
  String expected = String("ACK|DL2|") + DEVICE_ID + "|" + String(sequence);
  unsigned long deadline = millis() + ACK_TIMEOUT_MS;
  while (millis() < deadline) {
    int size = LoRa.parsePacket();
    if (size > 0) {
      String response;
      while (LoRa.available()) response += static_cast<char>(LoRa.read());
      if (response == expected) return true;
    }
    delay(10);
  }
  return false;
}

bool sendFrameWithAck(const String& frame, uint32_t sequence) {
  for (int attempt = 1; attempt <= 3; ++attempt) {
    Serial.printf("LoRa TX attempt %d/3 SEQ=%u\n", attempt, sequence);
    LoRa.idle();
    LoRa.beginPacket();
    LoRa.print(frame);
    if (LoRa.endPacket() != 1) continue;
    if (waitForAck(sequence)) return true;
  }
  return false;
}

void sendEvent(const String& frame, uint32_t sequence, const char* type) {
  if (sendFrameWithAck(frame, sequence)) {
    notifyPhone(String("ACK|") + type + "|" + String(sequence));
  } else if (queueFrame(frame)) {
    notifyPhone(String("STATUS|QUEUED_OFFLINE|") + String(sequence));
  } else {
    notifyPhone(String("STATUS|OUTBOX_FULL|") + String(sequence));
  }
}

void flushOutbox() {
  if (!outboxCount() || millis() - lastOutboxAttempt < OUTBOX_RETRY_MS) return;
  lastOutboxAttempt = millis();
  String frame = peekFrame();
  if (frame.isEmpty()) { popFrame(); return; }

  // Extract sequence from DL2|device|sequence|...
  int p1 = frame.indexOf('|');
  int p2 = frame.indexOf('|', p1 + 1);
  int p3 = frame.indexOf('|', p2 + 1);
  if (p1 < 0 || p2 < 0 || p3 < 0) { popFrame(); return; }
  uint32_t seq = static_cast<uint32_t>(strtoul(frame.substring(p2 + 1, p3).c_str(), nullptr, 10));
  if (sendFrameWithAck(frame, seq)) {
    popFrame();
    notifyPhone(String("STATUS|QUEUED_DELIVERED|") + String(seq));
  }
}

void transmitLocation() {
  if (!hasLocation) { notifyPhone("STATUS|NO_LOCATION"); return; }
  uint32_t seq = nextSequence++;
  outboxPrefs.putUInt("nextSeq", nextSequence);
  String frame = makeFrame("LOCATION", seq, lastClientId, lastUserId, lastUserName, lastUserPhone,
                           lastDistrict, lastLatitude, lastLongitude, "");
  sendEvent(frame, seq, "LOCATION");
}

void transmitSos(const String& clientId, const String& userId, const String& name, const String& phone,
                 const String& district, double lat, double lon, const String& message) {
  lastClientId = cleanField(clientId, 32);
  if (lastClientId.isEmpty()) {
    char generated[33];
    uint32_t a = esp_random(), b = esp_random(), c = esp_random(), d = esp_random();
    snprintf(generated, sizeof(generated), "%08lx%08lx%08lx%08lx", (unsigned long)a, (unsigned long)b, (unsigned long)c, (unsigned long)d);
    lastClientId = String(generated);
  }
  lastUserId = cleanField(userId, 20);
  lastUserName = cleanField(name, 30);
  lastUserPhone = cleanField(phone, 16);
  lastDistrict = cleanField(district, 30);
  lastLatitude = lat;
  lastLongitude = lon;
  hasLocation = true;

  uint32_t seq = nextSequence++;
  outboxPrefs.putUInt("nextSeq", nextSequence);
  String frame = makeFrame("SOS", seq, lastClientId, lastUserId, lastUserName, lastUserPhone,
                           lastDistrict, lat, lon, message);
  sendEvent(frame, seq, "SOS");
}

class CommandCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* characteristic) override {
    portENTER_CRITICAL(&commandMux);
    pendingCommand = String(characteristic->getValue().c_str());
    commandReady = true;
    portEXIT_CRITICAL(&commandMux);
  }
};



String safeEvidenceTask(String value) {
  String out;
  for (size_t i = 0; i < value.length() && out.length() < 80; ++i) {
    char c = value[i];
    if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_') out += c;
    else out += '_';
  }
  return out;
}

String evidenceDir(const String& taskId) {
  return String("/evidence/") + safeEvidenceTask(taskId);
}

String evidencePath(const String& taskId, int index) {
  return evidenceDir(taskId) + "/" + String(index) + ".jpg";
}

bool splitEvidenceCommand(const String& command, String* fields, int maxFields, int* count) {
  int start = 0;
  int found = 0;
  while (found < maxFields - 1) {
    int sep = command.indexOf('|', start);
    if (sep < 0) break;
    fields[found++] = command.substring(start, sep);
    start = sep + 1;
  }
  fields[found++] = command.substring(start);
  *count = found;
  return found > 0;
}

bool decodeBase64Chunk(const String& encoded, uint8_t* output, size_t outputCapacity, size_t* decoded) {
  size_t olen = 0;
  int rc = mbedtls_base64_decode(output, outputCapacity, &olen,
                                 reinterpret_cast<const unsigned char*>(encoded.c_str()), encoded.length());
  if (rc != 0) return false;
  *decoded = olen;
  return true;
}

String encodeBase64Chunk(const uint8_t* input, size_t length) {
  unsigned char output[160];
  size_t olen = 0;
  if (mbedtls_base64_encode(output, sizeof(output), &olen, input, length) != 0) return "";
  output[olen] = '\0';
  return String(reinterpret_cast<char*>(output));
}

void handleEvidenceCommand(const String& command) {
  String f[8];
  int n = 0;
  if (!splitEvidenceCommand(command, f, 8, &n) || n < 2 || f[0] != "EVID") {
    notifyPhone("EERR|INVALID_COMMAND");
    return;
  }

  if (f[1] == "BEGIN" && n == 5) {
    String taskId = safeEvidenceTask(f[2]);
    int index = f[3].toInt();
    size_t total = static_cast<size_t>(strtoul(f[4].c_str(), nullptr, 10));
    if (taskId.isEmpty() || index < 0 || index > 9 || total == 0 || total > MAX_EVIDENCE_BYTES) {
      notifyPhone(String("EERR|BEGIN|") + f[2] + "|" + f[3]);
      return;
    }
    String dir = evidenceDir(taskId);
    if (!LittleFS.exists("/evidence")) LittleFS.mkdir("/evidence");
    if (!LittleFS.exists(dir)) LittleFS.mkdir(dir);
    if (evidenceWriteFile) evidenceWriteFile.close();
    LittleFS.remove(evidencePath(taskId, index));
    evidenceWriteFile = LittleFS.open(evidencePath(taskId, index), FILE_WRITE);
    if (!evidenceWriteFile) {
      notifyPhone(String("EERR|BEGIN|") + f[2] + "|" + f[3]);
      return;
    }
    evidenceWriteFile.close();
    notifyPhone(String("EACK|BEGIN|") + f[2] + "|" + f[3]);
    return;
  }

  if (f[1] == "PUT" && n == 6) {
    String taskId = safeEvidenceTask(f[2]);
    int index = f[3].toInt();
    size_t offset = static_cast<size_t>(strtoul(f[4].c_str(), nullptr, 10));
    String path = evidencePath(taskId, index);
    File file = LittleFS.open(path, FILE_APPEND);
    if (!file) {
      notifyPhone(String("EERR|PUT|") + f[2] + "|" + f[3] + "|" + f[4]);
      return;
    }
    size_t current = file.size();
    if (current != offset) {
      file.close();
      notifyPhone(String("EERR|PUT_OFFSET|") + f[2] + "|" + f[3] + "|" + String(current));
      return;
    }
    uint8_t decoded[128];
    size_t decodedLen = 0;
    bool ok = decodeBase64Chunk(f[5], decoded, sizeof(decoded), &decodedLen);
    if (!ok || decodedLen == 0 || current + decodedLen > MAX_EVIDENCE_BYTES) {
      file.close();
      notifyPhone(String("EERR|PUT_DATA|") + f[2] + "|" + f[3]);
      return;
    }
    size_t written = file.write(decoded, decodedLen);
    file.close();
    if (written != decodedLen) {
      notifyPhone(String("EERR|PUT_WRITE|") + f[2] + "|" + f[3]);
      return;
    }
    notifyPhone(String("EACK|PUT|") + f[2] + "|" + f[3] + "|" + String(offset + decodedLen));
    return;
  }

  if (f[1] == "END" && n == 5) {
    String taskId = safeEvidenceTask(f[2]);
    int index = f[3].toInt();
    size_t expected = static_cast<size_t>(strtoul(f[4].c_str(), nullptr, 10));
    File file = LittleFS.open(evidencePath(taskId, index), FILE_READ);
    size_t actual = file ? file.size() : 0;
    if (file) file.close();
    if (actual != expected || actual < 1024) {
      notifyPhone(String("EERR|END|") + f[2] + "|" + f[3] + "|" + String(actual));
      return;
    }
    notifyPhone(String("EACK|END|") + f[2] + "|" + f[3] + "|OK");
    return;
  }

  if (f[1] == "LIST" && n == 3) {
    String taskId = safeEvidenceTask(f[2]);
    String dirPath = evidenceDir(taskId);
    if (!LittleFS.exists(dirPath)) {
      notifyPhone(String("EVID|LIST_END|") + f[2]);
      return;
    }
    File dir = LittleFS.open(dirPath);
    if (!dir || !dir.isDirectory()) {
      notifyPhone(String("EVID|LIST_END|") + f[2]);
      return;
    }
    File file = dir.openNextFile();
    while (file) {
      String name = String(file.name());
      int slash = name.lastIndexOf('/');
      String base = slash >= 0 ? name.substring(slash + 1) : name;
      if (base.endsWith(".jpg")) {
        int dot = base.indexOf('.');
        int index = base.substring(0, dot).toInt();
        notifyPhone(String("EVID|META|") + f[2] + "|" + String(index) + "|" + String(file.size()));
      }
      file.close();
      file = dir.openNextFile();
    }
    dir.close();
    notifyPhone(String("EVID|LIST_END|") + f[2]);
    return;
  }

  if (f[1] == "GET" && n == 5) {
    String taskId = safeEvidenceTask(f[2]);
    int index = f[3].toInt();
    size_t offset = static_cast<size_t>(strtoul(f[4].c_str(), nullptr, 10));
    File file = LittleFS.open(evidencePath(taskId, index), FILE_READ);
    if (!file) {
      notifyPhone(String("EERR|") + f[2] + "|NOT_FOUND");
      return;
    }
    size_t total = file.size();
    if (offset >= total || total > MAX_EVIDENCE_BYTES) {
      file.close();
      notifyPhone(String("EERR|") + f[2] + "|INVALID_OFFSET");
      return;
    }
    file.seek(offset);
    uint8_t bytes[EVIDENCE_CHUNK_BYTES];
    size_t readLen = file.read(bytes, EVIDENCE_CHUNK_BYTES);
    file.close();
    String encoded = encodeBase64Chunk(bytes, readLen);
    if (encoded.isEmpty()) {
      notifyPhone(String("EERR|") + f[2] + "|ENCODE");
      return;
    }
    notifyPhone(String("EDATA|") + f[2] + "|" + f[3] + "|" + String(offset) + "|" + String(total) + "|" + encoded);
    return;
  }

  notifyPhone("EERR|UNSUPPORTED_EVIDENCE_COMMAND");
}

void setupBle() {
  BLEDevice::init("DisasterLink Field Node");
  BLEServer* server = BLEDevice::createServer();
  BLEService* service = server->createService(NUS_SERVICE_UUID);
  txCharacteristic = service->createCharacteristic(NUS_TX_UUID, BLECharacteristic::PROPERTY_NOTIFY);
  txCharacteristic->addDescriptor(new BLE2902());
  BLECharacteristic* rx = service->createCharacteristic(NUS_RX_UUID, BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR);
  rx->setCallbacks(new CommandCallbacks());
  service->start();
  BLEAdvertising* advertising = BLEDevice::getAdvertising();
  advertising->addServiceUUID(NUS_SERVICE_UUID);
  advertising->start();
}

void setup() {
  Serial.begin(115200);
  delay(500);
  pinMode(SOS_BUTTON_PIN, INPUT_PULLUP);
  outboxPrefs.begin("dl-outbox", false);
  // Persist the sequence counter so a reboot cannot accidentally reuse an
  // earlier (deviceId, sequence) packet identity.
  nextSequence = outboxPrefs.getUInt("nextSeq", 1);
  if (nextSequence == 0) nextSequence = 1;

  SPI.begin(LORA_SCK, LORA_MISO, LORA_MOSI, LORA_NSS);
  LoRa.setPins(LORA_NSS, LORA_RESET, LORA_DIO0);
  if (!LoRa.begin(LORA_FREQUENCY)) {
    Serial.println("LoRa initialization failed - check power, SPI pins and frequency");
    while (true) delay(1000);
  }
  LoRa.enableCrc();
  if (!LittleFS.begin(true)) {
    Serial.println("LittleFS initialization failed - evidence transfer unavailable");
  } else {
    if (!LittleFS.exists("/evidence")) LittleFS.mkdir("/evidence");
    Serial.println("LittleFS evidence storage ready");
  }
  setupBle();
  Serial.printf("DisasterLink Field Node ready. Outbox=%u/%u\n", outboxCount(), MAX_OUTBOX);
  notifyPhone("STATUS|FIELD_NODE_READY|OFFLINE_LORA_READY");
}

void loop() {
  if (commandReady) {
    portENTER_CRITICAL(&commandMux);
    String command = pendingCommand;
    commandReady = false;
    portEXIT_CRITICAL(&commandMux);

    if (command.startsWith("EVID|")) {
      handleEvidenceCommand(command);
    } else if (command.startsWith("LOCATION|")) {
      if (parseLocationCommand(command)) {
        notifyPhone("STATUS|LOCATION_STORED");
        transmitLocation();
      } else notifyPhone("STATUS|INVALID_LOCATION_COMMAND");
    } else if (command.startsWith("SOS|")) {
      String clientId, userId, name, phone, district, message;
      double lat, lon;
      if (parseSosCommand(command, &clientId, &userId, &name, &phone, &district, &lat, &lon, &message))
        transmitSos(clientId, userId, name, phone, district, lat, lon, message);
      else notifyPhone("STATUS|INVALID_SOS_COMMAND");
    } else if (command == "SOS") {
      if (hasLocation) transmitSos(lastClientId, lastUserId, lastUserName, lastUserPhone, lastDistrict, lastLatitude, lastLongitude, "Quick SOS");
      else notifyPhone("STATUS|NO_LOCATION");
    } else if (command == "PING") {
      notifyPhone(String("STATUS|PONG|OUTBOX=") + String(outboxCount()));
    } else notifyPhone("STATUS|UNKNOWN_COMMAND");
  }

  if (digitalRead(SOS_BUTTON_PIN) == LOW && millis() - lastButtonAt > 800) {
    lastButtonAt = millis();
    if (hasLocation) transmitSos(lastClientId, lastUserId, lastUserName, lastUserPhone, lastDistrict, lastLatitude, lastLongitude, "Physical SOS button");
    else notifyPhone("STATUS|NO_LOCATION");
  }

  flushOutbox();
  delay(10);
}
