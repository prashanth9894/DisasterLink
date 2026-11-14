'use strict';

const fs = require('fs');
const path = require('path');
const http = require('http');
const bcrypt = require('bcryptjs');

const testDb = path.join(__dirname, '..', 'data', 'qa-disasterlink.db');
const testEvidenceDir = path.join(__dirname, '..', 'data', 'qa-evidence');
for (const suffix of ['', '-wal', '-shm']) {
  try { fs.unlinkSync(testDb + suffix); } catch (_) {}
}
try { fs.rmSync(testEvidenceDir, { recursive: true, force: true }); } catch (_) {}
process.env.DISASTERLINK_DB_PATH = testDb;
process.env.JWT_SECRET = 'qa-only-jwt-secret-change-me';
process.env.GATEWAY_KEY = 'qa-gateway-key';
process.env.ADMIN_BOOTSTRAP_KEY = 'qa-admin-key';
process.env.DISASTERLINK_EVIDENCE_DIR = testEvidenceDir;

const { db } = require('../db/db');
const { signToken } = require('../middleware/auth');
const app = require('../server');

const users = {
  authority: { id: 'qa-authority', name: 'QA Authority', phone: '9000000001', email: 'qa.authority@example.test', gov: 'QAT0001QAA', role: 'AUTHORITY', authority: 'DISTRICT_COLLECTOR' },
  vao: { id: 'qa-vao', name: 'QA VAO', phone: '9000000006', email: 'qa.vao@example.test', gov: 'QAV0001QAA', role: 'AUTHORITY', authority: 'VAO' },
  rescue1: { id: 'qa-rescue-1', name: 'Rescue One', phone: '9000000002', email: 'qa.rescue1@example.test', gov: 'RES0001AAA', role: 'RESCUE' },
  rescue2: { id: 'qa-rescue-2', name: 'Rescue Two', phone: '9000000003', email: 'qa.rescue2@example.test', gov: 'RES0002AAA', role: 'RESCUE' },
  rescue3: { id: 'qa-rescue-3', name: 'Rescue Three', phone: '9000000004', email: 'qa.rescue3@example.test', gov: 'RES0003AAA', role: 'RESCUE' },
  citizen: { id: 'qa-citizen', name: 'QA Citizen', phone: '9000000005', email: 'qa.citizen@example.test', gov: '123456789012', role: 'CITIZEN' },
};

const passwordHash = bcrypt.hashSync('Qa@123456', 4);
for (const u of Object.values(users)) {
  db.prepare(`INSERT INTO users (id,name,phone,email,government_id,address,pincode,password_hash,role,authority_type,district)
              VALUES (?,?,?,?,?,?,?,?,?,?,?)`).run(
    u.id, u.name, u.phone, u.email, u.gov, 'QA Test Address, Tamil Nadu', '600001', passwordHash,
    u.role, u.authority || null, 'Chennai'
  );
}

const tokens = Object.fromEntries(Object.entries(users).map(([k, u]) => [k, signToken({
  id: u.id, role: u.role, authority_type: u.authority || null, district: 'Chennai', government_id: u.gov,
})]));

function request(server, method, url, body, token, headers = {}) {
  return new Promise((resolve, reject) => {
    const data = body === undefined ? null : JSON.stringify(body);
    const addr = server.address();
    const req = http.request({ host: addr.address === '::' ? '127.0.0.1' : addr.address, port: addr.port, method, path: url, headers: {
      ...(data ? { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data) } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers,
    }}, res => {
      let raw = '';
      res.on('data', c => raw += c);
      res.on('end', () => { let parsed = {}; try { parsed = raw ? JSON.parse(raw) : {}; } catch (_) {} resolve({ status: res.statusCode, body: parsed }); });
    });
    req.on('error', reject);
    if (data) req.write(data);
    req.end();
  });
}

function assert(condition, message) {
  if (!condition) throw new Error(message);
  console.log(`PASS  ${message}`);
}

(async () => {
  const server = app.listen(0);
  try {
    let r;
    r = await request(server, 'GET', '/health');
    assert(r.status === 200 && r.body.status === 'ok', 'health endpoint');

    r = await request(server, 'POST', '/api/invites', { authorityType: 'TAHSILDAR', district: 'Chennai', expiresInHours: 24 }, null, { 'x-admin-key': 'qa-admin-key' });
    assert(r.status === 201 && r.body.code, 'bootstrap admin can issue invite');
    const inviteCode = r.body.code;
    r = await request(server, 'POST', '/api/auth/register', { name: 'QA Tahsildar', phone: '9000000007', email: 'qa.tahsildar@example.test', governmentId: 'QAT0002QAA', address: 'QA Test Address, Chennai', pincode: '600001', password: 'Qa@123456', role: 'AUTHORITY', authorityType: 'TAHSILDAR', district: 'Chennai', inviteCode }, null);
    assert(r.status === 201 && r.body.user.authorityType === 'TAHSILDAR', 'invited authority registration succeeds');
    r = await request(server, 'POST', '/api/auth/register', { name: 'QA Wrong District', phone: '9000000008', email: 'qa.wrongdistrict@example.test', governmentId: 'QAT0003QAA', address: 'QA Test Address, Chennai', pincode: '600001', password: 'Qa@123456', role: 'AUTHORITY', authorityType: 'TAHSILDAR', district: 'Madurai', inviteCode }, null);
    assert(r.status === 403, 'district-restricted invite cannot be used in another district');
    // Team registration: backend is authoritative, not the authority phone Room DB.
    r = await request(server, 'POST', '/api/teams', { name: 'QA Alpha', memberIds: [users.rescue1.gov, users.rescue2.gov], phone: '+919000000002' }, tokens.authority);
    assert(r.status === 201 && r.body.id && r.body.memberIds.length === 2, 'authority creates team from registered rescue IDs');
    const team1 = r.body;

    r = await request(server, 'POST', '/api/teams', { name: 'QA Duplicate Member', memberIds: [users.rescue1.gov, users.rescue3.gov], phone: '+919000000003' }, tokens.authority);
    assert(r.status === 409, 'duplicate rescue membership is rejected');

    r = await request(server, 'POST', '/api/teams', { name: 'QA Invalid', memberIds: ['BAD'], phone: '+919000000004' }, tokens.authority);
    assert(r.status === 400, 'invalid team member ID is rejected');

    // Emergency creation + task assignment.
    r = await request(server, 'POST', '/api/emergencies', { clientId: 'QA:EMERGENCY:1', type: 'Fire', message: 'QA emergency', latitude: 13.08, longitude: 80.27, district: 'Chennai', citizenId: users.citizen.id }, tokens.citizen);
    assert(r.status === 201 && r.body.clientId === 'QA:EMERGENCY:1', 'citizen emergency creation');
    const emergencyId = r.body.id;

    r = await request(server, 'POST', '/api/tasks', { emergencyId, title: 'Respond to Fire', location: '13.08, 80.27', assignedTeamId: team1.id, clientId: 'QA:TASK:1' }, tokens.authority);
    assert(r.status === 201 && r.body.assignedTeamId === team1.id && r.body.status === 'ASSIGNED', 'task assignment stores Team UUID');
    const taskId = r.body.id;

    r = await request(server, 'POST', '/api/tasks', { emergencyId, title: 'Duplicate', location: 'same', assignedTeamId: team1.id, clientId: 'QA:TASK:2' }, tokens.authority);
    assert(r.status === 409, 'second active task for same emergency is rejected');

    // Rescue visibility and transition authorization.
    r = await request(server, 'GET', '/api/tasks', undefined, tokens.rescue1);
    assert(r.status === 200 && r.body.some(t => t.id === taskId), 'assigned rescue member sees the task');
    r = await request(server, 'GET', '/api/tasks', undefined, tokens.rescue3);
    assert(r.status === 200 && !r.body.some(t => t.id === taskId), 'non-member rescue cannot see another team task');

    r = await request(server, 'PATCH', `/api/tasks/${taskId}/accept`, undefined, tokens.rescue1);
    assert(r.status === 200 && r.body.status === 'IN_PROGRESS', 'assigned rescue accepts task');

    r = await request(server, 'PATCH', `/api/tasks/${taskId}/reassign`, { assignedTeamId: team1.id }, tokens.authority);
    assert(r.status === 200 && r.body.status === 'ASSIGNED', 'authority can reassign active task');

    r = await request(server, 'PATCH', `/api/tasks/${taskId}/accept`, undefined, tokens.rescue1);
    assert(r.status === 200 && r.body.status === 'IN_PROGRESS', 'reassigned task can be accepted again');

    // The old direct-completion route is retired. Rescue must submit evidence
    // and wait for authority verification.
    r = await request(server, 'PATCH', `/api/tasks/${taskId}/complete`, { imageUris: ['content://a','content://b','content://c'] }, tokens.rescue1);
    assert(r.status === 410, 'rescue direct completion route is retired');
    r = await request(server, 'PATCH', `/api/tasks/${taskId}/submit-verification`, { imageUris: ['content://a','content://b'] }, tokens.rescue1);
    assert(r.status === 400, 'verification submission requires at least 3 evidence images');
    r = await request(server, 'PATCH', `/api/emergencies/${emergencyId}/status`, { status: 'RESOLVED' }, tokens.authority);
    assert(r.status === 409, 'authority cannot directly resolve an emergency before task verification');
    const tinyJpeg = '/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////2wBDAf//////////////////////////////////////////////////////////////////////////////////////wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAX/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIQAxAAAAH/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oACAEBAAEFAqf/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oACAEDAQE/AX//xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oACAECAQE/AX//xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oACAEBAAY/Apf/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oACAEBAAE/IV//2gAMAwEAAgADAAAAEP/EABQRAQAAAAAAAAAAAAAAAAAAABD/2gAIAQMBAT8QH//EABQRAQAAAAAAAAAAAAAAAAAAABD/2gAIAQIBAT8QH//EABQQAQAAAAAAAAAAAAAAAAAAABD/2gAIAQEAAT8QH//Z';
    for (let i = 0; i < 3; i++) {
      r = await request(server, 'POST', `/api/tasks/${taskId}/evidence`, { index: i, mimeType: 'image/jpeg', dataBase64: tinyJpeg }, tokens.rescue1);
      assert(r.status === 201 && r.body.uri === `/api/tasks/${taskId}/evidence/${i}`, `rescue evidence image ${i + 1} uploads over Wi-Fi`);
    }
    r = await request(server, 'PATCH', `/api/tasks/${taskId}/submit-verification`, { imageUris: [0,1,2].map(i => `/api/tasks/${taskId}/evidence/${i}`) }, tokens.rescue1);
    assert(r.status === 200 && r.body.status === 'PENDING_VERIFICATION', 'rescue submits server-backed evidence for authority verification');
    r = await request(server, 'GET', `/api/tasks/${taskId}/evidence/0`, undefined, tokens.authority);
    assert(r.status === 200, 'authority can retrieve the actual evidence image over Wi-Fi');
    r = await request(server, 'PATCH', `/api/tasks/${taskId}/verify`, { evidenceReviewed: true, evidenceCount: 3 }, tokens.rescue1);
    assert(r.status === 403, 'rescue cannot verify or complete a task');
    r = await request(server, 'PATCH', `/api/tasks/${taskId}/verify`, { evidenceReviewed: false, evidenceCount: 3 }, tokens.authority);
    assert(r.status === 400, 'authority must explicitly confirm evidence review');
    r = await request(server, 'PATCH', `/api/tasks/${taskId}/reassign`, { assignedTeamId: team1.id }, tokens.authority);
    assert(r.status === 409, 'verification-pending task cannot be reassigned');
    r = await request(server, 'PATCH', `/api/tasks/${taskId}/reject-verification`, undefined, tokens.authority);
    assert(r.status === 200 && r.body.status === 'IN_PROGRESS', 'authority can reject verification and return task to rescue');
    r = await request(server, 'PATCH', `/api/tasks/${taskId}/submit-verification`, { imageUris: ['content://a','content://b','content://c'] }, tokens.rescue1);
    assert(r.status === 200 && r.body.status === 'PENDING_VERIFICATION', 'rescue can resubmit corrected evidence');
    r = await request(server, 'PATCH', `/api/tasks/${taskId}/verify`, { evidenceReviewed: true, evidenceCount: 3 }, tokens.authority);
    assert(r.status === 200 && r.body.status === 'COMPLETED', 'authority verifies evidence and completes task');
    r = await request(server, 'GET', `/api/tasks/${taskId}/events`, undefined, tokens.authority);
    assert(r.status === 200 && r.body.some(e => e.action === 'EVIDENCE_SUBMITTED') && r.body.some(e => e.action === 'VERIFICATION_REJECTED') && r.body.some(e => e.action === 'VERIFIED_COMPLETED'), 'task audit trail records verification lifecycle');
    r = await request(server, 'PATCH', `/api/tasks/${taskId}/verify`, { evidenceReviewed: true, evidenceCount: 3 }, tokens.authority);
    assert(r.status === 409, 'completed task cannot be verified twice');
    r = await request(server, 'PATCH', `/api/tasks/${taskId}/reassign`, { assignedTeamId: team1.id }, tokens.authority);
    assert(r.status === 409, 'completed task cannot be reassigned');
    r = await request(server, 'PATCH', `/api/emergencies/${emergencyId}/status`, { status: 'RESOLVED' }, tokens.authority);
    assert(r.status === 200 && r.body.status === 'RESOLVED', 'resolved emergency is consistent with completed task');

    r = await request(server, 'POST', '/api/feedback', { taskId, rescueTeamId: team1.id, rating: 5, comment: 'QA complete' }, tokens.citizen);
    assert(r.status === 201 && r.body.rating === 5, 'citizen can submit feedback after completion');
    r = await request(server, 'POST', '/api/feedback', { taskId, rescueTeamId: team1.id, rating: 4, comment: 'Duplicate QA feedback' }, tokens.citizen);
    assert(r.status === 409, 'duplicate task feedback is rejected');

    // Team location and device linking.
    r = await request(server, 'PATCH', `/api/teams/${team1.id}/location`, { latitude: 13.1, longitude: 80.3 }, tokens.rescue1);
    assert(r.status === 200 && r.body.latitude === 13.1, 'rescue member can update team location');
    r = await request(server, 'PATCH', `/api/teams/${team1.id}/location`, { latitude: 999, longitude: 80 }, tokens.rescue1);
    assert(r.status === 400, 'invalid team location rejected');

    r = await request(server, 'POST', '/api/gateway/devices', { deviceId: 'FIELD-QA', label: 'QA Field Node', teamId: team1.id }, tokens.authority);
    assert(r.status === 201 && r.body.team_id === team1.id, 'authority links LoRa device to team');

    // Gateway authentication + idempotent SOS delivery.
    const gatewayBody = { deviceId: 'FIELD-QA', sequence: 42, type: 'SOS', latitude: 13.08, longitude: 80.27, message: 'LoRa QA SOS', citizenId: users.citizen.id, reporterName: users.citizen.name, reporterPhone: users.citizen.phone, reporterGovernmentId: users.citizen.gov, district: 'Chennai' };
    r = await request(server, 'POST', '/api/gateway/ingest', gatewayBody, null, { 'x-gateway-key': 'wrong' });
    assert(r.status === 401, 'invalid gateway key rejected');
    r = await request(server, 'POST', '/api/gateway/ingest', gatewayBody, null, { 'x-gateway-key': 'qa-gateway-key' });
    assert(r.status === 201 && r.body.relayed === 'SOS', 'valid gateway ingests LoRa SOS');
    r = await request(server, 'POST', '/api/gateway/ingest', gatewayBody, null, { 'x-gateway-key': 'qa-gateway-key' });
    assert(r.status === 200 && r.body.duplicate === true, 'duplicate LoRa SOS is ignored');

    const dupCount = db.prepare('SELECT COUNT(*) AS n FROM emergencies WHERE client_id = ?').get('LORA:FIELD-QA:42').n;
    assert(dupCount === 1, 'LoRa duplicate does not create duplicate emergency');

    // Active task must block team deletion, protecting live operations.
    r = await request(server, 'POST', '/api/emergencies', { clientId: 'QA:EMERGENCY:ACTIVE', type: 'Flood', message: 'Active task deletion test', latitude: 13.09, longitude: 80.28, district: 'Chennai', citizenId: users.citizen.id }, tokens.citizen);
    const activeEmergency = r.body.id;
    r = await request(server, 'POST', '/api/tasks', { emergencyId: activeEmergency, title: 'Active response', location: '13.09, 80.28', assignedTeamId: team1.id, clientId: 'QA:TASK:ACTIVE' }, tokens.authority);
    assert(r.status === 201, 'active task created for deletion safety test');
    r = await request(server, 'DELETE', `/api/teams/${team1.id}`, undefined, tokens.authority);
    assert(r.status === 409, 'team with active task cannot be removed');
    // Fetch the active task id and reject it before deletion.
    const activeTask = db.prepare('SELECT id FROM tasks WHERE client_id = ?').get('QA:TASK:ACTIVE');
    r = await request(server, 'PATCH', `/api/tasks/${activeTask.id}/reject`, undefined, tokens.rescue1);
    assert(r.status === 200 && r.body.status === 'REJECTED', 'active task can be rejected');

    // Safe team deletion: no active task remains for team1, so deletion succeeds.
    r = await request(server, 'DELETE', `/api/teams/${team1.id}`, undefined, tokens.authority);
    assert(r.status === 204, 'completed team can be removed safely');

    console.log('\nDISASTERLINK QA: ALL PASS');
  } finally {
    server.close();
    try { db.close(); } catch (_) {}
    for (const suffix of ['', '-wal', '-shm']) try { fs.unlinkSync(testDb + suffix); } catch (_) {}
    try { fs.rmSync(testEvidenceDir, { recursive: true, force: true }); } catch (_) {}
  }
})().catch(error => {
  console.error(`\nFAIL  ${error.message}`);
  process.exitCode = 1;
});
