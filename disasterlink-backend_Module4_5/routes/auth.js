const express = require('express');
const bcrypt = require('bcryptjs');
const crypto = require('crypto');
const { db } = require('../db/db');
const { signToken, createRefreshSession, rotateRefreshSession, revokeRefreshToken, requireAuth } = require('../middleware/auth');

const router = express.Router();

const PHONE_RE = /^[6-9]\d{9}$/;
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const GOVID_CITIZEN_RE = /^\d{12}$/; // Aadhaar-style, matches the Android app's citizen ID rule
const GOVID_STAFF_RE = /^[A-Z]{3}\d{4}[A-Z]{3}$/; // employee ID rule, matches rescue/authority accounts
const VALID_AUTHORITY_TYPES = ['VAO', 'TAHSILDAR', 'DISTRICT_COLLECTOR', 'STATE_DISASTER_MANAGEMENT_AUTHORITY', 'CM'];

function findUserByGovId(govId) {
  return db.prepare('SELECT * FROM users WHERE government_id = ?').get(govId);
}

router.post('/register', (req, res) => {
  const { name, phone, email, governmentId, address, pincode, password, role, authorityType, district, inviteCode } = req.body || {};
  const normalizedEmail = String(email || '').trim().toLowerCase();
  const normalizedGovId = String(governmentId || '').trim().toUpperCase();
  const normalizedPhone = String(phone || '').trim();

  if (!name || String(name).trim().length < 2) return res.status(400).json({ error: 'Full name is required' });
  if (!PHONE_RE.test(normalizedPhone)) return res.status(400).json({ error: 'Phone must be 10 digits starting with 6-9' });
  if (!EMAIL_RE.test(normalizedEmail)) return res.status(400).json({ error: 'Enter a valid email address' });
  if (!['CITIZEN', 'RESCUE', 'AUTHORITY'].includes(role)) return res.status(400).json({ error: 'Invalid role' });
  if (!address || String(address).trim().length < 10) return res.status(400).json({ error: 'Enter the full address' });
  if (!/^\d{6}$/.test(pincode || '')) return res.status(400).json({ error: 'PIN code must be 6 digits' });
  if (!password || password.length < 8 || password.length > 30 || !/[A-Z]/.test(password) || !/\d/.test(password) || !/[^A-Za-z0-9]/.test(password)) {
    return res.status(400).json({ error: 'Password must be 8-30 chars with an uppercase letter, a number and a special character' });
  }

  const govIdRule = role === 'CITIZEN' ? GOVID_CITIZEN_RE : GOVID_STAFF_RE;
  if (!govIdRule.test(normalizedGovId)) {
    return res.status(400).json({ error: role === 'CITIZEN' ? 'Aadhaar number must be 12 digits' : 'ID must be 3 letters + 4 digits + 3 letters (e.g. ABC1234XYZ)' });
  }

  // --- This is the security-critical part: AUTHORITY registration requires
  // a valid, unused, unexpired invite code minted by an existing senior
  // authority (or the bootstrap admin key, see routes/invites.js). ---
  let grantedAuthorityType = null;
  let inviteRow = null;
  if (role === 'AUTHORITY') {
    if (!VALID_AUTHORITY_TYPES.includes(authorityType)) return res.status(400).json({ error: 'Invalid authority type' });
    if (!inviteCode) return res.status(403).json({ error: 'Authority registration requires an invite code issued by a District Collector or above.' });

    inviteRow = db.prepare('SELECT * FROM invite_codes WHERE code = ?').get(String(inviteCode).trim().toUpperCase());
    if (!inviteRow) return res.status(403).json({ error: 'Invite code not found' });
    if (inviteRow.used_by) return res.status(403).json({ error: 'This invite code has already been used' });
    if (inviteRow.expires_at < Date.now()) return res.status(403).json({ error: 'This invite code has expired' });
    if (inviteRow.authority_type !== authorityType) {
      return res.status(403).json({ error: `This invite code is only valid for ${inviteRow.authority_type} registration` });
    }
    if (inviteRow.district && String(inviteRow.district).trim().toLowerCase() !== String(district || '').trim().toLowerCase()) {
      return res.status(403).json({ error: 'The invite code is restricted to its issued district' });
    }
    grantedAuthorityType = inviteRow.authority_type;
  }

  if (findUserByGovId(normalizedGovId)) return res.status(409).json({ error: 'ID is already registered' });
  if (db.prepare('SELECT 1 FROM users WHERE phone = ?').get(normalizedPhone)) return res.status(409).json({ error: 'Phone number is already in use' });
  if (db.prepare('SELECT 1 FROM users WHERE email = ?').get(normalizedEmail)) return res.status(409).json({ error: 'Email is already in use' });

  const id = crypto.randomUUID();
  const passwordHash = bcrypt.hashSync(password, 10);

  try {
    db.exec('BEGIN IMMEDIATE');
    db.prepare(`
      INSERT INTO users (id, name, phone, email, government_id, address, pincode, password_hash, role, authority_type, district)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).run(id, name.trim(), normalizedPhone, normalizedEmail, normalizedGovId, address.trim(), pincode, passwordHash, role, grantedAuthorityType, district || '');

    if (inviteRow) {
      const consumed = db.prepare('UPDATE invite_codes SET used_by = ?, used_at = ? WHERE code = ? AND used_by IS NULL').run(id, Date.now(), inviteRow.code);
      if (consumed.changes !== 1) throw new Error('Invite code was already claimed');
    }
    db.exec('COMMIT');
  } catch (error) {
    try { db.exec('ROLLBACK'); } catch (_) {}
    console.error('Registration failed:', error);
    return res.status(409).json({ error: 'Account could not be created. Phone, email, government ID or invite code may already be in use.' });
  }

  const user = findUserByGovId(normalizedGovId);
  const token = signToken(user);
  const refreshToken = createRefreshSession(user);
  res.status(201).json({ token, refreshToken, user: toPublicUser(user) });
});

router.post('/login', (_req, res) => {
  return res.status(410).json({
    error: 'Password/ID login has been retired. Sign in with your registered email and one-time verification code.'
  });
});



// Module 4: rotate a long-lived opaque refresh session into a fresh access JWT.
// The Android app can call this when online; offline operation never depends on
// this endpoint because the local session is kept in SharedPreferences.
router.post('/refresh', (req, res) => {
  const result = rotateRefreshSession(req.body?.refreshToken);
  if (!result) return res.status(401).json({ error: 'Refresh session is invalid, expired or revoked. Please log in again.' });
  res.json({ token: result.accessToken, user: toPublicUser(result.user) });
});

router.post('/logout', requireAuth, (req, res) => {
  const refreshToken = req.body?.refreshToken;
  if (refreshToken) revokeRefreshToken(refreshToken);
  res.json({ message: 'Logged out' });
});

function toPublicUser(user) {
  return {
    id: user.id, name: user.name, phone: user.phone, email: user.email,
    governmentId: user.government_id, address: user.address, pincode: user.pincode,
    role: user.role, authorityType: user.authority_type, district: user.district,
  };
}

module.exports = router;
