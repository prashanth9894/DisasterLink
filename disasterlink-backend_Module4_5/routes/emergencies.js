const express = require('express');
const crypto = require('crypto');
const { db } = require('../db/db');
const { requireAuth, optionalAuth, requireRole } = require('../middleware/auth');

const router = express.Router();

function toPublic(e) {
  return { id: e.id, clientId: e.client_id, citizenId: e.citizen_id, reporterName: e.reporter_name || '', reporterPhone: e.reporter_phone || '', reporterGovernmentId: e.reporter_government_id || '', type: e.type, message: e.message, latitude: e.latitude, longitude: e.longitude, district: e.district, status: e.status, createdAt: e.created_at };
}

// Citizens (and anonymous SOS) create emergencies. Auth optional so an SOS
// can still go out if a citizen isn't logged in, matching the Android app's
// anonymous-SOS behaviour.
router.post('/', optionalAuth, (req, res) => {
  const { type, message, latitude, longitude, district, clientId, citizenId, reporterName, reporterPhone, reporterGovernmentId } = req.body || {};
  if (!type) return res.status(400).json({ error: 'Emergency type is required' });
  if (latitude != null && (!Number.isFinite(latitude) || latitude < -90 || latitude > 90)) return res.status(400).json({ error: 'latitude must be between -90 and 90' });
  if (longitude != null && (!Number.isFinite(longitude) || longitude < -180 || longitude > 180)) return res.status(400).json({ error: 'longitude must be between -180 and 180' });

  // clientId lets the offline-sync worker retry safely: if a phone created
  // this emergency locally (e.g. while it had no signal) and the upload
  // succeeds but the response is lost (radio drops right after), the next
  // periodic retry sends the SAME clientId. We look it up first instead of
  // blindly inserting again, so a flaky connection can't create duplicates.
  if (clientId) {
    const existing = db.prepare('SELECT * FROM emergencies WHERE client_id = ?').get(clientId);
    if (existing) return res.status(200).json(toPublic(existing));
  }

  const id = crypto.randomUUID();
  const authenticatedCitizenId = req.user?.role === 'CITIZEN' ? req.user.id : null;
  const safeCitizenId = authenticatedCitizenId || String(citizenId || 'ANONYMOUS').trim().slice(0, 64) || 'ANONYMOUS';
  const reporter = authenticatedCitizenId
    ? db.prepare('SELECT name, phone, government_id FROM users WHERE id = ?').get(authenticatedCitizenId)
    : null;
  db.prepare(`
    INSERT INTO emergencies
      (id, client_id, citizen_id, type, message, latitude, longitude, district,
       reporter_name, reporter_phone, reporter_government_id)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  `).run(
    id, clientId || null, safeCitizenId, type, message || '', latitude ?? null, longitude ?? null, district || '',
    reporter?.name || reporterName || '', reporter?.phone || reporterPhone || '', reporter?.government_id || reporterGovernmentId || '',
  );
  res.status(201).json(toPublic(db.prepare('SELECT * FROM emergencies WHERE id = ?').get(id)));
});

// Authority/rescue: list every emergency.
router.get('/', requireAuth, requireRole('AUTHORITY', 'RESCUE'), (_req, res) => {
  res.json(db.prepare('SELECT * FROM emergencies ORDER BY created_at DESC').all().map(toPublic));
});

// Citizen: list their own emergencies.
router.get('/mine', requireAuth, requireRole('CITIZEN'), (req, res) => {
  res.json(db.prepare('SELECT * FROM emergencies WHERE citizen_id = ? ORDER BY created_at DESC').all(req.user.id).map(toPublic));
});

router.get('/:id', requireAuth, (req, res) => {
  const e = db.prepare('SELECT * FROM emergencies WHERE id = ?').get(req.params.id);
  if (!e) return res.status(404).json({ error: 'Not found' });
  if (req.user.role === 'CITIZEN' && e.citizen_id !== req.user.id) return res.status(403).json({ error: 'You may only view your own emergencies' });
  res.json(toPublic(e));
});

router.patch('/:id/status', requireAuth, requireRole('AUTHORITY'), (req, res) => {
  const { status } = req.body || {};
  const valid = ['REPORTED', 'ACKNOWLEDGED', 'ASSIGNED', 'RESOLVED', 'REJECTED'];
  if (!valid.includes(status)) return res.status(400).json({ error: `status must be one of ${valid.join(', ')}` });
  if (status === 'RESOLVED') {
    const task = db.prepare(`
      SELECT status FROM tasks
      WHERE emergency_id = ?
      ORDER BY created_at DESC LIMIT 1
    `).get(req.params.id);
    if (!task || task.status !== 'COMPLETED') {
      return res.status(409).json({ error: 'Emergency can only be resolved after its response task is completed through authority verification' });
    }
  }
  const result = db.prepare('UPDATE emergencies SET status = ? WHERE id = ?').run(status, req.params.id);
  if (result.changes === 0) return res.status(404).json({ error: 'Not found' });
  res.json(toPublic(db.prepare('SELECT * FROM emergencies WHERE id = ?').get(req.params.id)));
});

module.exports = router;
