const { db } = require('../db/db');

console.log('\nLoRa devices');
console.table(db.prepare(`
  SELECT device_id AS deviceId, label, team_id AS teamId, last_seen_at AS lastSeenAt
  FROM lora_devices ORDER BY last_seen_at DESC
`).all());

console.log('\nRecent LoRa packets');
console.table(db.prepare(`
  SELECT device_id AS deviceId, sequence, type, received_at AS receivedAt
  FROM lora_packets ORDER BY received_at DESC LIMIT 20
`).all());

console.log('\nSOS emergencies relayed by LoRa');
console.table(db.prepare(`
  SELECT id, citizen_id AS source, type, latitude, longitude, status, created_at AS createdAt
  FROM emergencies WHERE citizen_id LIKE 'LORA:%' ORDER BY created_at DESC LIMIT 20
`).all());
