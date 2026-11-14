const express = require('express');
const { db } = require('../db/db');
const { requireAuth, requireRole } = require('../middleware/auth');

const router = express.Router();

router.get('/summary', requireAuth, requireRole('AUTHORITY', 'RESCUE'), (req, res) => {
  const emergencyTotal = Number(db.prepare('SELECT COUNT(*) AS n FROM emergencies').get().n);
  const active = Number(db.prepare("SELECT COUNT(*) AS n FROM emergencies WHERE status NOT IN ('RESOLVED','REJECTED')").get().n);
  const completed = Number(db.prepare("SELECT COUNT(*) AS n FROM tasks WHERE status = 'COMPLETED'").get().n);
  const assigned = Number(db.prepare("SELECT COUNT(*) AS n FROM tasks WHERE status = 'ASSIGNED'").get().n);
  const inProgress = Number(db.prepare("SELECT COUNT(*) AS n FROM tasks WHERE status = 'IN_PROGRESS'").get().n);
  const teams = Number(db.prepare('SELECT COUNT(*) AS n FROM rescue_teams').get().n);
  const onlineTeams = Number(db.prepare("SELECT COUNT(*) AS n FROM rescue_teams WHERE latitude IS NOT NULL AND longitude IS NOT NULL").get().n);
  res.json({ emergencyTotal, active, completed, assigned, inProgress, teams, teamsWithLocation: onlineTeams, generatedAt: Date.now() });
});

router.get('/activity', requireAuth, requireRole('AUTHORITY', 'RESCUE'), (req, res) => {
  const limit = Math.min(Math.max(Number.parseInt(req.query.limit || '25', 10) || 25, 1), 100);
  const rows = db.prepare(`
    SELECT te.id, te.task_id AS taskId, te.action, te.actor_id AS actorId,
           te.actor_role AS actorRole, te.details, te.created_at AS createdAt,
           t.emergency_id AS emergencyId, t.title, t.assigned_team_id AS assignedTeamId
    FROM task_events te JOIN tasks t ON t.id = te.task_id
    ORDER BY te.created_at DESC LIMIT ?
  `).all(limit);
  res.json(rows);
});

module.exports = router;
