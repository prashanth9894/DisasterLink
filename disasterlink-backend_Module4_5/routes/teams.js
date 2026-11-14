const express = require('express');
const crypto = require('crypto');
const { db, MIN_RANK_TO_MANAGE_TEAMS } = require('../db/db');
const { requireAuth, requireRole, requireMinAuthorityRank } = require('../middleware/auth');

const router = express.Router();
const GOVID_STAFF_RE = /^[A-Z]{3}\d{4}[A-Z]{3}$/;
const PHONE_RE = /^[6-9]\d{9}$/;

function toPublic(t) {
  return {
    id: t.id,
    name: t.name,
    memberIds: t.member_ids.split(',').filter(Boolean),
    phone: t.phone,
    latitude: t.latitude,
    longitude: t.longitude,
    updatedAt: t.updated_at
  };
}

function isTeamMember(team, governmentId) {
  return !!team && team.member_ids.split(',').includes(governmentId);
}

router.get('/', requireAuth, requireRole('AUTHORITY', 'RESCUE'), (_req, res) => {
  res.json(db.prepare('SELECT * FROM rescue_teams ORDER BY updated_at DESC').all().map(toPublic));
});

router.post('/', requireAuth, requireMinAuthorityRank(MIN_RANK_TO_MANAGE_TEAMS), (req, res) => {
  const { name, memberIds, phone } = req.body || {};
  const cleanName = String(name || '').trim();
  const normalized = [...new Set((Array.isArray(memberIds) ? memberIds : [])
    .map(m => String(m).trim().toUpperCase())
    .filter(Boolean))];
  const cleanPhone = String(phone || '').replace(/^\+91/, '');

  if (cleanName.length < 2 || cleanName.length > 100) return res.status(400).json({ error: 'Team name must be 2-100 characters' });
  if (normalized.length < 2 || normalized.length > 5) return res.status(400).json({ error: 'A rescue team must have 2 to 5 members' });
  if (normalized.some(m => !GOVID_STAFF_RE.test(m))) return res.status(400).json({ error: 'Member IDs must match ABC1234XYZ format' });
  if (!PHONE_RE.test(cleanPhone)) return res.status(400).json({ error: 'Team phone must be 10 digits starting with 6-9' });

  const users = normalized.map(govId => db.prepare('SELECT id, role FROM users WHERE government_id = ?').get(govId));
  const missingIndex = users.findIndex(u => !u);
  if (missingIndex >= 0) return res.status(400).json({ error: `${normalized[missingIndex]} is not a registered account` });
  const nonRescueIndex = users.findIndex(u => u.role !== 'RESCUE');
  if (nonRescueIndex >= 0) return res.status(400).json({ error: `${normalized[nonRescueIndex]} is not a rescue account` });

  // A rescue member can belong to only one active team. This prevents task
  // routing ambiguity: government ID -> team ID must remain deterministic.
  // SQLite cannot search a comma-separated member list with a simple IN.
  const existingTeams = db.prepare('SELECT id, name, member_ids FROM rescue_teams').all();
  for (const team of existingTeams) {
    const members = team.member_ids.split(',').filter(Boolean);
    const conflict = normalized.find(id => members.includes(id));
    if (conflict) return res.status(409).json({ error: `${conflict} is already assigned to team ${team.name}` });
  }

  const id = crypto.randomUUID();
  try {
    db.prepare('INSERT INTO rescue_teams (id, name, member_ids, phone) VALUES (?, ?, ?, ?)')
      .run(id, cleanName, normalized.join(','), `+91${cleanPhone}`);
  } catch (error) {
    console.error('Team creation failed:', error);
    return res.status(500).json({ error: 'Could not create rescue team' });
  }

  res.status(201).json(toPublic(db.prepare('SELECT * FROM rescue_teams WHERE id = ?').get(id)));
});

router.delete('/:id', requireAuth, requireMinAuthorityRank(MIN_RANK_TO_MANAGE_TEAMS), (req, res) => {
  const team = db.prepare('SELECT * FROM rescue_teams WHERE id = ?').get(req.params.id);
  if (!team) return res.status(404).json({ error: 'Not found' });

  const activeTask = db.prepare(`
    SELECT id FROM tasks
    WHERE assigned_team_id = ? AND status NOT IN ('COMPLETED','REJECTED')
    LIMIT 1
  `).get(req.params.id);
  if (activeTask) return res.status(409).json({ error: 'Cannot remove a team while it has an active task' });

  db.prepare('UPDATE lora_devices SET team_id = NULL WHERE team_id = ?').run(req.params.id);
  db.prepare('DELETE FROM rescue_teams WHERE id = ?').run(req.params.id);
  res.status(204).send();
});

router.patch('/:id/location', requireAuth, requireRole('RESCUE'), (req, res) => {
  const { latitude, longitude } = req.body || {};
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude) || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
    return res.status(400).json({ error: 'latitude and longitude must be valid coordinates' });
  }
  const team = db.prepare('SELECT * FROM rescue_teams WHERE id = ?').get(req.params.id);
  if (!team) return res.status(404).json({ error: 'Not found' });
  if (!isTeamMember(team, req.user.governmentId)) return res.status(403).json({ error: 'You are not a member of this rescue team' });
  db.prepare('UPDATE rescue_teams SET latitude = ?, longitude = ?, updated_at = ? WHERE id = ?')
    .run(latitude, longitude, Date.now(), req.params.id);
  res.json(toPublic(db.prepare('SELECT * FROM rescue_teams WHERE id = ?').get(req.params.id)));
});

module.exports = router;
