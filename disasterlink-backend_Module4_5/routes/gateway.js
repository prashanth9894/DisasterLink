const express = require('express');
const crypto = require('crypto');

const {
  db,
  MIN_RANK_TO_MANAGE_TEAMS
} = require('../db/db');

const {
  requireGatewayKey,
  requireAuth,
  requireMinAuthorityRank
} = require('../middleware/auth');

const router = express.Router();

const DEVICE_ID_RE = /^[A-Za-z0-9:_-]{3,64}$/;
const TEXT_RE = /^[^|\r\n]*$/;

function validCoordinates(latitude, longitude) {
  return Number.isFinite(latitude) &&
    Number.isFinite(longitude) &&
    latitude >= -90 &&
    latitude <= 90 &&
    longitude >= -180 &&
    longitude <= 180;
}

function cleanText(value, max) {
  const text = String(value ?? '').trim();

  return TEXT_RE.test(text) && text.length <= max
    ? text
    : null;
}

function toPublic(e) {
  return {
    id: e.id,
    clientId: e.client_id,
    citizenId: e.citizen_id,

    reporterName: e.reporter_name || '',
    reporterPhone: e.reporter_phone || '',
    reporterGovernmentId: e.reporter_government_id || '',

    type: e.type,
    message: e.message,

    latitude: e.latitude,
    longitude: e.longitude,

    district: e.district,
    status: e.status,
    createdAt: e.created_at,
  };
}


/*
|--------------------------------------------------------------------------
| POST /api/gateway/ingest
|--------------------------------------------------------------------------
|
| ESP32 Gateway -> Backend
|
| The Gateway has already verified the LoRa packet HMAC.
| This endpoint verifies the Gateway itself using x-gateway-key.
|
| Offline SOS example:
|
| {
|   "deviceId": "FIELD-01",
|   "sequence": 12,
|   "type": "SOS",
|   "latitude": 13.0827,
|   "longitude": 80.2707,
|   "message": "Emergency",
|   "citizenId": "USER-123",
|   "reporterName": "Prashanth",
|   "reporterPhone": "9876543210",
|   "district": "Chennai"
| }
|
|--------------------------------------------------------------------------
*/

router.post(
  '/ingest',
  requireGatewayKey,
  (req, res) => {

    const {
      deviceId,
      sequence,
      type,
      latitude,
      longitude,
      message,
      citizenId,
      reporterName,
      reporterPhone,
      district,
      reporterGovernmentId
    } = req.body || {};


    // ---------------------------------------------------------------------
    // Validate device
    // ---------------------------------------------------------------------

    if (!DEVICE_ID_RE.test(deviceId || '')) {
      return res.status(400).json({
        error: 'Invalid deviceId'
      });
    }


    // ---------------------------------------------------------------------
    // Validate sequence
    // ---------------------------------------------------------------------

    if (
      !Number.isSafeInteger(sequence) ||
      sequence < 0 ||
      sequence > 0xFFFFFFFF
    ) {
      return res.status(400).json({
        error: 'sequence must be an unsigned 32-bit integer'
      });
    }


    // ---------------------------------------------------------------------
    // Validate event type
    // ---------------------------------------------------------------------

    if (!['SOS', 'LOCATION'].includes(type)) {
      return res.status(400).json({
        error: 'type must be SOS or LOCATION'
      });
    }


    // ---------------------------------------------------------------------
    // Validate GPS
    // ---------------------------------------------------------------------

    if (!validCoordinates(latitude, longitude)) {
      return res.status(400).json({
        error: 'Invalid coordinates'
      });
    }


    // ---------------------------------------------------------------------
    // Sanitize received data
    // ---------------------------------------------------------------------

    const safeCitizenId =
      cleanText(citizenId, 64) ||
      `LORA:${deviceId}`;

    const safeName =
      cleanText(reporterName, 60) ||
      'Offline citizen';

    const safePhone =
      cleanText(reporterPhone, 20) ||
      '';

    const safeGovernmentId =
      cleanText(reporterGovernmentId, 64) ||
      '';

    const safeDistrict =
      cleanText(district, 60) ||
      '';

    const safeMessage =
      cleanText(message, 200) ||
      `Offline ${type} relayed via field device ${deviceId}`;


    // ---------------------------------------------------------------------
    // Update Gateway/LoRa device last seen
    // ---------------------------------------------------------------------

    db.prepare(`
      INSERT INTO lora_devices (
        device_id,
        last_seen_at
      )
      VALUES (?, ?)

      ON CONFLICT(device_id)
      DO UPDATE SET
        last_seen_at = excluded.last_seen_at
    `).run(
      deviceId,
      Date.now()
    );


    // =====================================================================
    // SOS
    // =====================================================================

    if (type === 'SOS') {
  const packet = db.prepare(`
    INSERT OR IGNORE INTO lora_packets (
      device_id,
      sequence,
      type,
      received_at
    )
    VALUES (?, ?, ?, ?)
  `).run(
    deviceId,
    sequence,
    type,
    Date.now()
  );

  if (packet.changes === 0) {
    console.log(
      `Duplicate LoRa SOS ignored: ${deviceId}:${sequence}`
    );

    return res.json({
      duplicate: true,
      deviceId,
      sequence
    });
  }

  const clientId = `LORA:${deviceId}:${sequence}`;
  const emergencyId = crypto.randomUUID();

  try {
    db.prepare(`
      INSERT INTO emergencies (
        id,
        client_id,
        citizen_id,
        type,
        message,
        latitude,
        longitude,
        district,
        reporter_name,
        reporter_phone,
        reporter_government_id,
        status
      )
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).run(
      emergencyId,
      clientId,
      safeCitizenId,
      'SOS (LoRa relay)',
      safeMessage,
      latitude,
      longitude,
      safeDistrict,
      safeName,
      safePhone,
      safeGovernmentId,
      'REPORTED'
    );

    console.log(`LoRa SOS stored: ${clientId}`);

    return res.status(201).json({
      relayed: 'SOS',
      emergencyId,
      clientId,
      citizenId: safeCitizenId,
      offline: true
    });

  } catch (error) {
    console.error('Failed to store LoRa SOS:', error);

    try {
      db.prepare(`
        DELETE FROM lora_packets
        WHERE device_id = ?
        AND sequence = ?
      `).run(
        deviceId,
        sequence
      );
    } catch (rollbackError) {
      console.error(
        'Failed to rollback LoRa packet:',
        rollbackError
      );
    }

    return res.status(500).json({
      error: 'Failed to store offline SOS'
    });
  }
}


    // =====================================================================
    // LOCATION
    // =====================================================================

    const device = db.prepare(`
      SELECT *
      FROM lora_devices
      WHERE device_id = ?
    `).get(deviceId);


    if (!device?.team_id) {

      return res.status(404).json({
        error:
          `Device ${deviceId} is not linked to a rescue team yet.`
      });
    }


    /*
     * Prevent duplicate LOCATION packets.
     */

    const packet = db.prepare(`
      INSERT OR IGNORE INTO lora_packets (
        device_id,
        sequence,
        type,
        received_at
      )
      VALUES (?, ?, ?, ?)
    `).run(
      deviceId,
      sequence,
      type,
      Date.now()
    );


    if (packet.changes === 0) {

      return res.json({
        duplicate: true,
        deviceId,
        sequence
      });
    }


    // ---------------------------------------------------------------------
    // Update rescue team location
    // ---------------------------------------------------------------------

    const result = db.prepare(`
      UPDATE rescue_teams
      SET
        latitude = ?,
        longitude = ?,
        updated_at = ?
      WHERE id = ?
    `).run(
      latitude,
      longitude,
      Date.now(),
      device.team_id
    );


    if (result.changes === 0) {

      return res.status(404).json({
        error: 'Linked team no longer exists'
      });
    }


    return res.json({
      relayed: 'LOCATION',
      teamId: device.team_id,
      latitude,
      longitude,
      offline: true
    });
  }
);


/*
|--------------------------------------------------------------------------
| POST /api/gateway/devices
|--------------------------------------------------------------------------
|
| Authority links a physical ESP32/LoRa field device
| to a registered rescue team.
|
|--------------------------------------------------------------------------
*/

router.post(
  '/devices',
  requireAuth,
  requireMinAuthorityRank(MIN_RANK_TO_MANAGE_TEAMS),
  (req, res) => {

    const {
      deviceId,
      label,
      teamId
    } = req.body || {};


    if (!DEVICE_ID_RE.test(deviceId || '')) {

      return res.status(400).json({
        error: 'Invalid deviceId'
      });
    }


    if (
      label != null &&
      (
        typeof label !== 'string' ||
        label.length > 100
      )
    ) {

      return res.status(400).json({
        error: 'label must be at most 100 characters'
      });
    }


    if (
      teamId &&
      !db.prepare(`
        SELECT 1
        FROM rescue_teams
        WHERE id = ?
      `).get(teamId)
    ) {

      return res.status(404).json({
        error: 'teamId does not match a registered rescue team'
      });
    }


    db.prepare(`
      INSERT INTO lora_devices (
        device_id,
        label,
        team_id,
        registered_by
      )
      VALUES (?, ?, ?, ?)

      ON CONFLICT(device_id)
      DO UPDATE SET
        label = excluded.label,
        team_id = excluded.team_id
    `).run(
      deviceId,
      label || '',
      teamId || null,
      req.user.id
    );


    const device = db.prepare(`
      SELECT *
      FROM lora_devices
      WHERE device_id = ?
    `).get(deviceId);


    return res.status(201).json(device);
  }
);


/*
|--------------------------------------------------------------------------
| GET /api/gateway/devices
|--------------------------------------------------------------------------
|
| Authority can see registered LoRa devices.
|
|--------------------------------------------------------------------------
*/

router.get(
  '/devices',
  requireAuth,
  requireMinAuthorityRank(MIN_RANK_TO_MANAGE_TEAMS),
  (_req, res) => {

    const devices = db.prepare(`
      SELECT *
      FROM lora_devices
      ORDER BY created_at DESC
    `).all();


    return res.json(devices);
  }
);


module.exports = router;