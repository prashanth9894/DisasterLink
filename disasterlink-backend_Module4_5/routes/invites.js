const express = require('express');
const crypto = require('crypto');
const { db, MIN_RANK_TO_ISSUE_INVITES } = require('../db/db');
const { requireAdminKeyOrMinRank } = require('../middleware/auth');

const router = express.Router();
const VALID_AUTHORITY_TYPES = ['VAO', 'TAHSILDAR', 'DISTRICT_COLLECTOR', 'STATE_DISASTER_MANAGEMENT_AUTHORITY', 'CM'];

function generateCode() {
  // Human-typeable: 3 groups of 4 alphanumerics, e.g. AB3F-7K2M-QZ91
  const raw = crypto.randomBytes(6).toString('hex').toUpperCase(); // 12 hex chars
  return `${raw.slice(0, 4)}-${raw.slice(4, 8)}-${raw.slice(8, 12)}`;
}

// POST /api/invites  { authorityType, district?, expiresInHours? }
// Requires District Collector+ JWT, or the x-admin-key bootstrap header.
router.post('/', requireAdminKeyOrMinRank(MIN_RANK_TO_ISSUE_INVITES), (req, res) => {
  const { authorityType, district, expiresInHours } = req.body || {};
  if (!VALID_AUTHORITY_TYPES.includes(authorityType)) return res.status(400).json({ error: 'Invalid authority type' });

  const code = generateCode();
  const hours = Number.isFinite(expiresInHours) && expiresInHours > 0 ? expiresInHours : 72;
  const expiresAt = Date.now() + hours * 60 * 60 * 1000;
  const issuedBy = req.isBootstrapAdmin ? 'BOOTSTRAP_ADMIN' : req.user.id;

  db.prepare('INSERT INTO invite_codes (code, authority_type, district, issued_by, expires_at) VALUES (?, ?, ?, ?, ?)')
    .run(code, authorityType, district || null, issuedBy, expiresAt);

  res.status(201).json({ code, authorityType, district: district || null, expiresAt });
});

// GET /api/invites  - list codes issued by the caller (or all, for bootstrap admin), for auditing.
router.get('/', requireAdminKeyOrMinRank(MIN_RANK_TO_ISSUE_INVITES), (req, res) => {
  const rows = req.isBootstrapAdmin
    ? db.prepare('SELECT * FROM invite_codes ORDER BY created_at DESC').all()
    : db.prepare('SELECT * FROM invite_codes WHERE issued_by = ? ORDER BY created_at DESC').all(req.user.id);
  res.json(rows.map(r => ({
    code: r.code, authorityType: r.authority_type, district: r.district,
    used: !!r.used_by, expiresAt: r.expires_at, createdAt: r.created_at,
  })));
});

module.exports = router;
