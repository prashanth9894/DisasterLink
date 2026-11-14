const jwt = require('jsonwebtoken');
const crypto = require('crypto');
const { rankIndex } = require('../db/db');

const JWT_SECRET = process.env.JWT_SECRET;
if (!JWT_SECRET) {
  throw new Error('JWT_SECRET is not set. Copy .env.example to .env and set a real secret before starting the server.');
}

function signToken(user) {
  return jwt.sign(
    { id: user.id, role: user.role, authorityType: user.authority_type, district: user.district, governmentId: user.government_id },
    JWT_SECRET,
    { expiresIn: '30d' }
  );
}

function hashRefreshToken(token) {
  return crypto.createHash('sha256').update(token).digest('hex');
}

function createRefreshSession(user) {
  const raw = crypto.randomBytes(48).toString('base64url');
  const id = crypto.randomUUID();
  const expiresAt = Date.now() + 365 * 24 * 60 * 60 * 1000;
  const { db } = require('../db/db');
  db.prepare('INSERT INTO refresh_sessions (id, user_id, token_hash, expires_at) VALUES (?, ?, ?, ?)')
    .run(id, user.id, hashRefreshToken(raw), expiresAt);
  return raw;
}

function rotateRefreshSession(rawToken) {
  if (!rawToken || typeof rawToken !== 'string') return null;
  const { db } = require('../db/db');
  const hash = hashRefreshToken(rawToken);
  const row = db.prepare(`
    SELECT rs.*, u.* FROM refresh_sessions rs
    JOIN users u ON u.id = rs.user_id
    WHERE rs.token_hash = ? AND rs.revoked_at IS NULL AND rs.expires_at > ?
  `).get(hash, Date.now());
  if (!row) return null;

  db.prepare('UPDATE refresh_sessions SET last_used_at = ? WHERE id = ?').run(Date.now(), row.id);
  const accessToken = signToken(row);
  return { accessToken, user: row };
}

function revokeRefreshToken(rawToken) {
  if (!rawToken || typeof rawToken !== 'string') return;
  const { db } = require('../db/db');
  db.prepare('UPDATE refresh_sessions SET revoked_at = ? WHERE token_hash = ? AND revoked_at IS NULL')
    .run(Date.now(), hashRefreshToken(rawToken));
}

function requireAuth(req, res, next) {
  const header = req.headers.authorization || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : null;
  if (!token) return res.status(401).json({ error: 'Missing bearer token' });
  try {
    req.user = jwt.verify(token, JWT_SECRET);
    next();
  } catch {
    return res.status(401).json({ error: 'Invalid or expired token' });
  }
}

function optionalAuth(req, res, next) {
  const header = req.headers.authorization || '';
  if (!header) return next();
  const token = header.startsWith('Bearer ') ? header.slice(7) : null;
  if (!token) return res.status(401).json({ error: 'Invalid authorization header' });
  try { req.user = jwt.verify(token, JWT_SECRET); next(); }
  catch { return res.status(401).json({ error: 'Invalid or expired token' }); }
}

function requireRole(...roles) {
  return (req, res, next) => {
    if (!req.user || !roles.includes(req.user.role)) return res.status(403).json({ error: `Requires one of roles: ${roles.join(', ')}` });
    next();
  };
}

function requireMinAuthorityRank(minRank) {
  return (req, res, next) => {
    if (!req.user || req.user.role !== 'AUTHORITY') return res.status(403).json({ error: 'Authority account required' });
    if (rankIndex(req.user.authorityType) < rankIndex(minRank)) return res.status(403).json({ error: `Requires rank ${minRank} or above. Your account is ${req.user.authorityType}.` });
    next();
  };
}

function requireAdminKeyOrMinRank(minRank) {
  return (req, res, next) => {
    const adminKey = req.headers['x-admin-key'];
    if (adminKey && process.env.ADMIN_BOOTSTRAP_KEY && adminKey === process.env.ADMIN_BOOTSTRAP_KEY) { req.isBootstrapAdmin = true; return next(); }
    requireAuth(req, res, () => requireMinAuthorityRank(minRank)(req, res, next));
  };
}

function requireGatewayKey(req, res, next) {
  const key = req.headers['x-gateway-key'];
  const expected = process.env.GATEWAY_KEY;
  const supplied = Array.isArray(key) ? key[0] : key;
  const valid = typeof supplied === 'string' && typeof expected === 'string' && supplied.length === expected.length && crypto.timingSafeEqual(Buffer.from(supplied), Buffer.from(expected));
  if (!valid) return res.status(401).json({ error: 'Missing or invalid x-gateway-key' });
  next();
}

module.exports = { signToken, createRefreshSession, rotateRefreshSession, revokeRefreshToken, requireAuth, optionalAuth, requireRole, requireMinAuthorityRank, requireAdminKeyOrMinRank, requireGatewayKey, JWT_SECRET };
