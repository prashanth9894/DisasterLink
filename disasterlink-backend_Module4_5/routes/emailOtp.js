const express = require('express');
const crypto = require('crypto');
const net = require('net');
const tls = require('tls');
const { db } = require('../db/db');
const { signToken, createRefreshSession } = require('../middleware/auth');

const router = express.Router();

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const VALID_ROLES = ['CITIZEN', 'RESCUE', 'AUTHORITY'];
const OTP_TTL_MS = 5 * 60 * 1000;
const OTP_RESEND_COOLDOWN_MS = 45 * 1000;
const OTP_MAX_ATTEMPTS = 5;

function normalizeEmail(email) {
  return String(email || '').trim().toLowerCase();
}

function hashOtp(email, role, otp) {
  return crypto.createHash('sha256').update(`${email}:${role}:${otp}`).digest('hex');
}

function publicUser(user) {
  return {
    id: user.id,
    name: user.name,
    phone: user.phone,
    email: user.email,
    governmentId: user.government_id,
    address: user.address,
    pincode: user.pincode,
    role: user.role,
    authorityType: user.authority_type,
    district: user.district,
  };
}

function smtpResponse(socket, expectedCodes) {
  return new Promise((resolve, reject) => {
    let buffer = '';
    const onData = (chunk) => {
      buffer += chunk.toString('utf8');
      const lines = buffer.split(/\r?\n/);
      if (lines.length < 2) return;
      const complete = lines.slice(0, -1);
      buffer = lines[lines.length - 1];
      let final = null;
      for (const line of complete) {
        if (/^\d{3} /.test(line)) final = line;
      }
      if (!final) return;
      socket.off('data', onData);
      socket.off('error', onError);
      const code = Number(final.slice(0, 3));
      if (!expectedCodes.includes(code)) reject(new Error(`SMTP error ${code}: ${final.slice(4)}`));
      else resolve(final);
    };
    const onError = (err) => {
      socket.off('data', onData);
      reject(err);
    };
    socket.on('data', onData);
    socket.once('error', onError);
  });
}

function smtpWrite(socket, command, expectedCodes) {
  socket.write(`${command}\r\n`);
  return smtpResponse(socket, expectedCodes);
}

async function sendSmtpEmail(email, otp) {
  const host = process.env.SMTP_HOST;
  const port = Number(process.env.SMTP_PORT || 587);
  const secure = process.env.SMTP_SECURE === 'true' || port === 465;
  const username = process.env.SMTP_USER;
  const password = process.env.SMTP_PASS;
  const from = process.env.SMTP_FROM || username;

  if (!host || !username || !password || !from) {
    throw new Error('SMTP is not configured. Set SMTP_HOST, SMTP_USER, SMTP_PASS and SMTP_FROM in .env.');
  }

  let socket;
  if (secure) {
    socket = tls.connect({ host, port, servername: host, rejectUnauthorized: true });
  } else {
    socket = net.connect({ host, port });
  }
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('SMTP connection timed out')), 10000);
    socket.once('connect', () => { clearTimeout(timer); resolve(); });
    socket.once('error', (err) => { clearTimeout(timer); reject(err); });
  });

  try {
    socket.setTimeout(15000, () => socket.destroy(new Error('SMTP operation timed out')));
    await smtpResponse(socket, [220]);
    await smtpWrite(socket, 'EHLO disasterlink.local', [250]);

    if (!secure) {
      if (process.env.SMTP_REQUIRE_TLS !== 'false') {
        await smtpWrite(socket, 'STARTTLS', [220]);
        socket = await new Promise((resolve, reject) => {
          const old = socket;
          const upgraded = tls.connect({ socket: old, host, servername: host, rejectUnauthorized: true }, () => resolve(upgraded));
          upgraded.once('error', reject);
        });
        socket.setTimeout(15000, () => socket.destroy(new Error('SMTP TLS operation timed out')));
        await smtpWrite(socket, 'EHLO disasterlink.local', [250]);
      }
    }

    // AUTH LOGIN is widely supported by traditional SMTP providers, including Gmail.
    await smtpWrite(socket, 'AUTH LOGIN', [334]);
    await smtpWrite(socket, Buffer.from(username, 'utf8').toString('base64'), [334]);
    await smtpWrite(socket, Buffer.from(password, 'utf8').toString('base64'), [235]);

    await smtpWrite(socket, `MAIL FROM:<${from.replace(/^.*<|>.*$/g, '')}>`, [250]);
    await smtpWrite(socket, `RCPT TO:<${email}>`, [250, 251]);
    await smtpWrite(socket, 'DATA', [354]);

    const subject = 'DisasterLink verification code';
    const text = `Your DisasterLink verification code is ${otp}. It expires in 5 minutes. If you did not request this code, you can ignore this email.`;
    const html = `<div style="font-family:Arial,sans-serif;max-width:560px;margin:auto;padding:24px"><h2>DisasterLink</h2><p>Use the verification code below to sign in.</p><div style="font-size:32px;font-weight:700;letter-spacing:8px;padding:18px 0">${otp}</div><p>This code expires in <strong>5 minutes</strong> and can be used only once.</p><p style="color:#667085;font-size:12px">If you did not request this code, you can safely ignore this email.</p></div>`;
    const boundary = `=_DisasterLink_${crypto.randomBytes(12).toString('hex')}`;
    const fromHeader = from.includes('<') ? from : `DisasterLink <${from}>`;
    const message = [
      `From: ${fromHeader}`,
      `To: ${email}`,
      `Subject: ${subject}`,
      'MIME-Version: 1.0',
      `Content-Type: multipart/alternative; boundary="${boundary}"`,
      '',
      `--${boundary}`,
      'Content-Type: text/plain; charset=UTF-8',
      'Content-Transfer-Encoding: 8bit',
      '',
      text,
      '',
      `--${boundary}`,
      'Content-Type: text/html; charset=UTF-8',
      'Content-Transfer-Encoding: 8bit',
      '',
      html,
      '',
      `--${boundary}--`,
      '.',
    ].join('\r\n');
    socket.write(message + '\r\n');
    await smtpResponse(socket, [250]);
    await smtpWrite(socket, 'QUIT', [221]);
  } finally {
    socket.end();
  }
}

function generateOtp() {
  return String(crypto.randomInt(100000, 1000000));
}

router.post('/request', async (req, res) => {
  const email = normalizeEmail(req.body?.email);
  const role = String(req.body?.role || '').toUpperCase();

  if (!EMAIL_RE.test(email)) return res.status(400).json({ error: 'Enter a valid email address' });
  if (!VALID_ROLES.includes(role)) return res.status(400).json({ error: 'Invalid account type' });

  const user = db.prepare('SELECT id FROM users WHERE email = ? AND role = ?').get(email, role);

  // Do not reveal whether an email/account exists.
  if (!user) return res.json({ message: 'If the account exists, a verification code has been sent.' });

  const existing = db.prepare('SELECT * FROM email_otp_codes WHERE email = ? AND role = ?').get(email, role);
  if (existing && Date.now() - existing.last_sent_at < OTP_RESEND_COOLDOWN_MS) {
    const waitSeconds = Math.ceil((OTP_RESEND_COOLDOWN_MS - (Date.now() - existing.last_sent_at)) / 1000);
    return res.status(429).json({ error: `Please wait ${waitSeconds}s before requesting another code` });
  }

  const otp = generateOtp();
  const now = Date.now();
  const otpHash = hashOtp(email, role, otp);

  console.log(`[EMAIL OTP] Code generated for ${email} (${role}): ${otp}`);

  db.prepare(`
    INSERT INTO email_otp_codes (email, role, otp_hash, expires_at, attempts, last_sent_at)
    VALUES (?, ?, ?, ?, 0, ?)
    ON CONFLICT(email, role) DO UPDATE SET
      otp_hash = excluded.otp_hash,
      expires_at = excluded.expires_at,
      attempts = 0,
      last_sent_at = excluded.last_sent_at
  `).run(email, role, otpHash, now + OTP_TTL_MS, now);

  try {
    await sendSmtpEmail(email, otp);
    return res.json({ message: 'Verification code sent to your email.' });
  } catch (error) {
    db.prepare('DELETE FROM email_otp_codes WHERE email = ? AND role = ?').run(email, role);
    console.error('SMTP OTP delivery failed:', error.message);
    return res.status(502).json({ error: 'Unable to send the verification email right now. Check SMTP configuration and try again.' });
  }
});

// POST /api/auth/email-otp/verify { email, role, otp }
router.post('/verify', (req, res) => {
  const email = normalizeEmail(req.body?.email);
  const role = String(req.body?.role || '').toUpperCase();
  const otp = String(req.body?.otp || '').trim();

  if (!EMAIL_RE.test(email)) return res.status(400).json({ error: 'Enter a valid email address' });
  if (!VALID_ROLES.includes(role)) return res.status(400).json({ error: 'Invalid account type' });
  if (!/^\d{6}$/.test(otp)) return res.status(400).json({ error: 'Enter the 6-digit verification code' });

  const row = db.prepare('SELECT * FROM email_otp_codes WHERE email = ? AND role = ?').get(email, role);
  if (!row) return res.status(401).json({ error: 'Invalid or expired verification code. Request a new code.' });

  if (row.expires_at < Date.now()) {
    db.prepare('DELETE FROM email_otp_codes WHERE email = ? AND role = ?').run(email, role);
    return res.status(410).json({ error: 'That verification code has expired. Request a new code.' });
  }

  if (row.attempts >= OTP_MAX_ATTEMPTS) {
    db.prepare('DELETE FROM email_otp_codes WHERE email = ? AND role = ?').run(email, role);
    return res.status(429).json({ error: 'Too many incorrect attempts. Request a new code.' });
  }

  if (hashOtp(email, role, otp) !== row.otp_hash) {
    db.prepare('UPDATE email_otp_codes SET attempts = attempts + 1 WHERE email = ? AND role = ?').run(email, role);
    return res.status(401).json({ error: 'Incorrect verification code.' });
  }

  const user = db.prepare('SELECT * FROM users WHERE email = ? AND role = ?').get(email, role);
  db.prepare('DELETE FROM email_otp_codes WHERE email = ? AND role = ?').run(email, role);

  if (!user) return res.status(401).json({ error: 'Unable to complete sign-in.' });

  const token = signToken(user);
  const refreshToken = createRefreshSession(user);
  return res.json({ token, refreshToken, user: publicUser(user) });
});

module.exports = router;
