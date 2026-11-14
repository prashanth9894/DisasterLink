const express = require('express');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const { db } = require('../db/db');
const { requireAuth, requireRole } = require('../middleware/auth');

const router = express.Router();

const EVIDENCE_ROOT = path.resolve(process.env.DISASTERLINK_EVIDENCE_DIR || path.join(__dirname, '..', 'data', 'evidence'));
const MAX_EVIDENCE_BYTES = 180 * 1024;
fs.mkdirSync(EVIDENCE_ROOT, { recursive: true });

function evidenceTaskDir(taskId) {
  const safeId = String(taskId).replace(/[^A-Za-z0-9_-]/g, '_').slice(0, 80);
  return path.join(EVIDENCE_ROOT, safeId);
}

function evidencePath(taskId, index) {
  return path.join(evidenceTaskDir(taskId), `evidence_${index}.jpg`);
}

function evidenceRef(taskId, index) {
  return `/api/tasks/${encodeURIComponent(taskId)}/evidence/${index}`;
}

function audit(taskId, action, actor, details = '') {
  db.prepare('INSERT INTO task_events (id, task_id, action, actor_id, actor_role, details) VALUES (?, ?, ?, ?, ?, ?)')
    .run(crypto.randomUUID(), taskId, action, actor?.id || '', actor?.role || '', details);
}

function toPublic(t) {
  return {
    id: t.id,
    clientId: t.client_id || null,
    emergencyId: t.emergency_id,
    title: t.title,
    location: t.location,
    assignedTeamId: t.assigned_team_id,
    status: t.status,
    createdAt: t.created_at,
    startedAt: t.started_at,
    completedAt: t.completed_at,
    imageUris: t.image_uris ? t.image_uris.split('|').filter(Boolean) : [],
  };
}

function assignedTeamForRescueUser(task, governmentId) {
  if (!task?.assigned_team_id) return null;
  const team = db.prepare('SELECT * FROM rescue_teams WHERE id = ?').get(task.assigned_team_id);
  return team?.member_ids.split(',').includes(governmentId) ? team : null;
}

function getTask(id) {
  return db.prepare('SELECT * FROM tasks WHERE id = ?').get(id);
}

router.get('/', requireAuth, requireRole('AUTHORITY', 'RESCUE'), (req, res) => {
  const tasks = db.prepare('SELECT * FROM tasks ORDER BY created_at DESC').all();
  if (req.user.role !== 'RESCUE') return res.json(tasks.map(toPublic));
  res.json(tasks.filter(task => assignedTeamForRescueUser(task, req.user.governmentId)).map(toPublic));
});

router.post('/', requireAuth, requireRole('AUTHORITY'), (req, res) => {
  const { emergencyId, title, location, assignedTeamId, clientId } = req.body || {};
  const emergency = db.prepare('SELECT * FROM emergencies WHERE id = ?').get(String(emergencyId || ''));
  if (!emergency) return res.status(404).json({ error: 'Emergency not found' });

  if (assignedTeamId && !db.prepare('SELECT 1 FROM rescue_teams WHERE id = ?').get(assignedTeamId)) {
    return res.status(404).json({ error: 'Assigned rescue team not found' });
  }
  if (title != null && (typeof title !== 'string' || title.trim().length > 200)) {
    return res.status(400).json({ error: 'title must be at most 200 characters' });
  }
  if (location != null && (typeof location !== 'string' || location.length > 500)) {
    return res.status(400).json({ error: 'location must be at most 500 characters' });
  }
  if (clientId != null && (typeof clientId !== 'string' || clientId.length > 100 || !/^[A-Za-z0-9:_-]+$/.test(clientId))) {
    return res.status(400).json({ error: 'Invalid clientId' });
  }

  // Offline sync retries use the same clientId. Never create a second task.
  if (clientId) {
    const existing = db.prepare('SELECT * FROM tasks WHERE client_id = ?').get(clientId);
    if (existing) return res.json(toPublic(existing));
  }

  // There must be only one non-terminal response task per emergency. Reassignment
  // changes the team on that task rather than creating another competing task.
  const active = db.prepare(`
    SELECT * FROM tasks
    WHERE emergency_id = ? AND status NOT IN ('COMPLETED','REJECTED')
    ORDER BY created_at DESC LIMIT 1
  `).get(emergencyId);
  if (active) {
    if (active.status === 'PENDING_VERIFICATION') {
      return res.status(409).json({ error: 'This emergency has a task pending authority verification', task: toPublic(active) });
    }
    if (assignedTeamId && active.assigned_team_id !== assignedTeamId) {
      db.prepare("UPDATE tasks SET assigned_team_id = ?, status = 'ASSIGNED' WHERE id = ?")
        .run(assignedTeamId, active.id);
      db.prepare("UPDATE emergencies SET status = 'ASSIGNED' WHERE id = ?").run(emergencyId);
      audit(active.id, 'REASSIGNED', req.user, `team=${assignedTeamId}`);
      return res.json(toPublic(getTask(active.id)));
    }
    return res.status(409).json({ error: 'This emergency already has an active task', task: toPublic(active) });
  }

  const id = crypto.randomUUID();
  const status = assignedTeamId ? 'ASSIGNED' : 'PENDING';
  try {
    db.exec('BEGIN IMMEDIATE');
    db.prepare(`
      INSERT INTO tasks (id, emergency_id, title, location, assigned_team_id, status, client_id)
      VALUES (?, ?, ?, ?, ?, ?, ?)
    `).run(id, emergencyId, String(title || `Respond to ${emergency.type}`).trim(), String(location || ''), assignedTeamId || null, status, clientId || null);
    db.prepare('UPDATE emergencies SET status = ? WHERE id = ?').run(assignedTeamId ? 'ASSIGNED' : 'ACKNOWLEDGED', emergencyId);
    audit(id, assignedTeamId ? 'ASSIGNED' : 'CREATED', req.user, assignedTeamId ? `team=${assignedTeamId}` : 'unassigned');
    db.exec('COMMIT');
  } catch (error) {
    try { db.exec('ROLLBACK'); } catch (_) {}
    console.error('Task creation failed:', error);
    return res.status(500).json({ error: 'Could not create task' });
  }

  res.status(201).json(toPublic(getTask(id)));
});

router.patch('/:id/reassign', requireAuth, requireRole('AUTHORITY'), (req, res) => {
  const { assignedTeamId } = req.body || {};
  if (!assignedTeamId || typeof assignedTeamId !== 'string') return res.status(400).json({ error: 'assignedTeamId is required' });
  if (!db.prepare('SELECT 1 FROM rescue_teams WHERE id = ?').get(assignedTeamId)) return res.status(404).json({ error: 'Assigned rescue team not found' });
  const task = getTask(req.params.id);
  if (!task) return res.status(404).json({ error: 'Not found' });
  if (task.status === 'COMPLETED') return res.status(409).json({ error: 'Completed task cannot be reassigned' });
  if (task.status === 'PENDING_VERIFICATION') return res.status(409).json({ error: 'Verification-pending task cannot be reassigned' });
  if (task.status === 'REJECTED') return res.status(409).json({ error: 'Rejected task cannot be reassigned; create a new assignment' });
  if (task.assigned_team_id === assignedTeamId && task.status === 'ASSIGNED') return res.json(toPublic(task));

  db.prepare("UPDATE tasks SET assigned_team_id = ?, status = 'ASSIGNED' WHERE id = ?")
    .run(assignedTeamId, req.params.id);
  db.prepare("UPDATE emergencies SET status = 'ASSIGNED' WHERE id = ?").run(task.emergency_id);
  audit(req.params.id, 'REASSIGNED', req.user, `team=${assignedTeamId}`);
  res.json(toPublic(getTask(req.params.id)));
});

router.patch('/:id/accept', requireAuth, requireRole('RESCUE'), (req, res) => {
  const task = getTask(req.params.id);
  if (!task) return res.status(404).json({ error: 'Not found' });
  if (!assignedTeamForRescueUser(task, req.user.governmentId)) return res.status(403).json({ error: 'This task is not assigned to your rescue team' });
  if (task.status !== 'ASSIGNED') return res.status(409).json({ error: 'Only an assigned task can be accepted' });

  db.prepare("UPDATE tasks SET status = 'IN_PROGRESS', started_at = COALESCE(started_at, ?) WHERE id = ?")
    .run(Date.now(), req.params.id);
  db.prepare("UPDATE emergencies SET status = 'ASSIGNED' WHERE id = ? AND status != 'RESOLVED'").run(task.emergency_id);
  audit(req.params.id, 'ACCEPTED', req.user);
  res.json(toPublic(getTask(req.params.id)));
});

router.patch('/:id/reject', requireAuth, requireRole('RESCUE'), (req, res) => {
  const task = getTask(req.params.id);
  if (!task) return res.status(404).json({ error: 'Not found' });
  if (!assignedTeamForRescueUser(task, req.user.governmentId)) return res.status(403).json({ error: 'This task is not assigned to your rescue team' });
  if (!['ASSIGNED', 'IN_PROGRESS'].includes(task.status)) return res.status(409).json({ error: 'This task cannot be rejected in its current state' });

  db.prepare("UPDATE tasks SET status = 'REJECTED', completed_at = ? WHERE id = ?")
    .run(Date.now(), req.params.id);
  db.prepare("UPDATE emergencies SET status = 'REJECTED' WHERE id = ?").run(task.emergency_id);
  audit(req.params.id, 'REJECTED', req.user);
  res.json(toPublic(getTask(req.params.id)));
});

// Rescue uploads one compressed evidence image while the phone is connected
// to the operational backend over Wi-Fi/LAN. The actual bytes are stored on
// the backend; the task only stores a protected API reference.
router.post('/:id/evidence', requireAuth, requireRole('RESCUE'), (req, res) => {
  const { index, dataBase64, mimeType } = req.body || {};
  if (!Number.isInteger(index) || index < 0 || index > 9) {
    return res.status(400).json({ error: 'Evidence index must be an integer from 0 to 9' });
  }
  if (mimeType !== 'image/jpeg') {
    return res.status(400).json({ error: 'Evidence must be uploaded as JPEG' });
  }
  if (typeof dataBase64 !== 'string' || dataBase64.length < 16 || dataBase64.length > 260000 || !/^[A-Za-z0-9+/]*={0,2}$/.test(dataBase64)) {
    return res.status(400).json({ error: 'Invalid evidence image payload' });
  }

  const task = getTask(req.params.id);
  if (!task) return res.status(404).json({ error: 'Not found' });
  if (!assignedTeamForRescueUser(task, req.user.governmentId)) {
    return res.status(403).json({ error: 'This task is not assigned to your rescue team' });
  }
  if (task.status !== 'IN_PROGRESS') {
    return res.status(409).json({ error: 'Evidence can only be uploaded for an in-progress task' });
  }

  let bytes;
  try {
    bytes = Buffer.from(dataBase64, 'base64');
  } catch (_) {
    return res.status(400).json({ error: 'Invalid Base64 evidence payload' });
  }
  if (bytes.length < 100 || bytes.length > MAX_EVIDENCE_BYTES) {
    return res.status(400).json({ error: `Evidence image must be between 100 bytes and ${MAX_EVIDENCE_BYTES} bytes` });
  }
  // Android compresses evidence as JPEG. Verify the file signature so an
  // arbitrary non-image payload cannot be stored as evidence.
  if (!(bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff)) {
    return res.status(400).json({ error: 'Evidence payload is not a valid JPEG image' });
  }

  const dir = evidenceTaskDir(task.id);
  const finalPath = evidencePath(task.id, index);
  const tempPath = `${finalPath}.tmp-${crypto.randomUUID()}`;
  try {
    fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(tempPath, bytes, { flag: 'wx' });
    fs.renameSync(tempPath, finalPath);
    res.status(201).json({ index, uri: evidenceRef(task.id, index), mimeType: 'image/jpeg', size: bytes.length });
  } catch (error) {
    try { fs.unlinkSync(tempPath); } catch (_) {}
    console.error('Evidence upload failed:', error);
    res.status(500).json({ error: 'Could not store evidence image' });
  }
});

// Authority (and the assigned rescue team) can retrieve the actual stored
// image over Wi-Fi/LAN. This endpoint is authenticated; images are never
// exposed as an unauthenticated static directory.
router.get('/:id/evidence/:index', requireAuth, requireRole('AUTHORITY', 'RESCUE'), (req, res) => {
  const index = Number(req.params.index);
  if (!Number.isInteger(index) || index < 0 || index > 9) return res.status(400).json({ error: 'Invalid evidence index' });
  const task = getTask(req.params.id);
  if (!task) return res.status(404).json({ error: 'Not found' });
  if (req.user.role === 'RESCUE' && !assignedTeamForRescueUser(task, req.user.governmentId)) {
    return res.status(403).json({ error: 'This task is not assigned to your rescue team' });
  }
  const file = evidencePath(task.id, index);
  if (!fs.existsSync(file)) return res.status(404).json({ error: 'Evidence image not found' });
  res.setHeader('Content-Type', 'image/jpeg');
  res.setHeader('Cache-Control', 'no-store');
  res.sendFile(file);
});

// Rescue submits evidence for authority verification. This endpoint never
// completes the task and never resolves the emergency.
router.patch('/:id/submit-verification', requireAuth, requireRole('RESCUE'), (req, res) => {
  const { imageUris } = req.body || {};
  if (!Array.isArray(imageUris) || imageUris.length < 3 || imageUris.length > 10) {
    return res.status(400).json({ error: 'Between 3 and 10 evidence references are required' });
  }
  if (imageUris.some(uri => typeof uri !== 'string' || uri.length === 0 || uri.length > 2048 || /[|\r\n]/.test(uri))) {
    return res.status(400).json({ error: 'Each evidence reference must be a valid URI without separators or line breaks' });
  }

  const task = getTask(req.params.id);
  if (!task) return res.status(404).json({ error: 'Not found' });
  if (!assignedTeamForRescueUser(task, req.user.governmentId)) {
    return res.status(403).json({ error: 'This task is not assigned to your rescue team' });
  }
  if (task.status !== 'IN_PROGRESS') {
    return res.status(409).json({ error: 'Only an in-progress task can be submitted for verification' });
  }

  db.prepare(`
    UPDATE tasks
    SET status = 'PENDING_VERIFICATION', completed_at = NULL, image_uris = ?
    WHERE id = ? AND status = 'IN_PROGRESS'
  `).run(imageUris.join('|'), req.params.id);
  audit(req.params.id, 'EVIDENCE_SUBMITTED', req.user, `evidence=${imageUris.length}`);
  res.json(toPublic(getTask(req.params.id)));
});

// Only an authenticated authority can approve evidence and complete a task.
// Task + emergency updates are atomic so the two states cannot diverge.
router.patch('/:id/verify', requireAuth, requireRole('AUTHORITY'), (req, res) => {
  const { evidenceReviewed, evidenceCount } = req.body || {};
  if (evidenceReviewed !== true || !Number.isInteger(evidenceCount) || evidenceCount < 3) {
    return res.status(400).json({ error: 'Authority must confirm that at least 3 evidence images were reviewed' });
  }
  const task = getTask(req.params.id);
  if (!task) return res.status(404).json({ error: 'Not found' });
  if (task.status !== 'PENDING_VERIFICATION') {
    return res.status(409).json({ error: 'Only a task pending verification can be completed' });
  }
  const evidence = task.image_uris ? task.image_uris.split('|').filter(Boolean) : [];
  if (evidence.length < 3 || evidenceCount !== evidence.length) {
    return res.status(409).json({ error: 'The reviewed evidence count must match the submitted evidence' });
  }

  try {
    db.exec('BEGIN IMMEDIATE');
    const updated = db.prepare(`
      UPDATE tasks
      SET status = 'COMPLETED', completed_at = ?
      WHERE id = ? AND status = 'PENDING_VERIFICATION'
    `).run(Date.now(), req.params.id);
    if (updated.changes !== 1) {
      db.exec('ROLLBACK');
      return res.status(409).json({ error: 'Task changed state before verification could be completed' });
    }
    db.prepare("UPDATE emergencies SET status = 'RESOLVED' WHERE id = ?").run(task.emergency_id);
    audit(req.params.id, 'VERIFIED_COMPLETED', req.user, `evidence=${evidence.length}`);
    db.exec('COMMIT');
  } catch (error) {
    try { db.exec('ROLLBACK'); } catch (_) {}
    console.error('Task verification failed:', error);
    return res.status(500).json({ error: 'Could not complete task verification' });
  }

  res.json(toPublic(getTask(req.params.id)));
});

router.patch('/:id/reject-verification', requireAuth, requireRole('AUTHORITY'), (req, res) => {
  const task = getTask(req.params.id);
  if (!task) return res.status(404).json({ error: 'Not found' });
  if (task.status !== 'PENDING_VERIFICATION') {
    return res.status(409).json({ error: 'Only a task pending verification can be rejected' });
  }
  db.prepare(`
    UPDATE tasks SET status = 'IN_PROGRESS', completed_at = NULL
    WHERE id = ? AND status = 'PENDING_VERIFICATION'
  `).run(req.params.id);
  db.prepare("UPDATE emergencies SET status = 'ASSIGNED' WHERE id = ? AND status != 'RESOLVED'").run(task.emergency_id);
  audit(req.params.id, 'VERIFICATION_REJECTED', req.user);
  res.json(toPublic(getTask(req.params.id)));
});

// The old rescue-complete route is intentionally retired. Keeping an explicit
// response makes stale clients fail safely instead of silently changing state.
router.patch('/:id/complete', requireAuth, (_req, res) => {
  res.status(410).json({ error: 'Direct task completion is retired. Submit evidence for authority verification.' });
});

router.get('/:id/events', requireAuth, requireRole('AUTHORITY', 'RESCUE'), (req, res) => {
  const task = getTask(req.params.id);
  if (!task) return res.status(404).json({ error: 'Not found' });
  if (req.user.role === 'RESCUE' && !assignedTeamForRescueUser(task, req.user.governmentId)) {
    return res.status(403).json({ error: 'This task is not assigned to your rescue team' });
  }
  res.json(db.prepare(`
    SELECT id, task_id AS taskId, action, actor_id AS actorId, actor_role AS actorRole,
           details, created_at AS createdAt
    FROM task_events WHERE task_id = ? ORDER BY created_at ASC
  `).all(req.params.id));
});

module.exports = router;
