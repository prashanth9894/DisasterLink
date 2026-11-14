const express = require('express');
const crypto = require('crypto');
const { db } = require('../db/db');
const { requireAuth, requireRole } = require('../middleware/auth');

const router = express.Router();

function toPublic(f) {
  return { id: f.id, userId: f.user_id, taskId: f.task_id, rescueTeamId: f.rescue_team_id, rating: f.rating, comment: f.comment, createdAt: f.created_at };
}

router.post('/', requireAuth, requireRole('CITIZEN'), (req, res) => {
  const { taskId, rescueTeamId, rating, comment } = req.body || {};
  if (!Number.isInteger(rating) || rating < 1 || rating > 5) return res.status(400).json({ error: 'rating must be an integer 1-5' });
  if (comment != null && (typeof comment !== 'string' || comment.length > 1000)) return res.status(400).json({ error: 'comment must be at most 1000 characters' });
  if (taskId && taskId !== 'GENERAL') {
    const task = db.prepare(`
      SELECT tasks.*, emergencies.citizen_id
      FROM tasks JOIN emergencies ON emergencies.id = tasks.emergency_id
      WHERE tasks.id = ?
    `).get(taskId);
    if (!task) return res.status(404).json({ error: 'Task not found' });
    if (task.citizen_id !== req.user.id) return res.status(403).json({ error: 'You may only rate a task for your own emergency' });
    if (task.status !== 'COMPLETED') return res.status(409).json({ error: 'Feedback is available after the task is completed' });
    if (rescueTeamId && rescueTeamId !== 'GENERAL' && rescueTeamId !== task.assigned_team_id) {
      return res.status(400).json({ error: 'rescueTeamId does not match the team assigned to this task' });
    }
    if (db.prepare('SELECT 1 FROM feedback WHERE user_id = ? AND task_id = ?').get(req.user.id, taskId)) {
      return res.status(409).json({ error: 'You have already submitted feedback for this task' });
    }
  }
  const id = crypto.randomUUID();
  db.prepare('INSERT INTO feedback (id, user_id, task_id, rescue_team_id, rating, comment) VALUES (?, ?, ?, ?, ?, ?)')
    .run(id, req.user.id, taskId || 'GENERAL', rescueTeamId || 'GENERAL', rating, comment || '');
  res.status(201).json(toPublic(db.prepare('SELECT * FROM feedback WHERE id = ?').get(id)));
});

router.get('/', requireAuth, requireRole('AUTHORITY'), (_req, res) => {
  res.json(db.prepare('SELECT * FROM feedback ORDER BY created_at DESC').all().map(toPublic));
});

module.exports = router;
