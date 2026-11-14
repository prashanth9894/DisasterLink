/**
 * Optional helper for creating an authority account from a bootstrap invite.
 * Configure all values through environment variables; no real credentials are
 * stored in this source file.
 */
require('dotenv').config();

const API_BASE = process.env.API_BASE || 'http://localhost:8080';
const ADMIN_KEY = process.env.ADMIN_BOOTSTRAP_KEY;
const NAME = process.env.AUTHORITY_NAME;
const PHONE = process.env.AUTHORITY_PHONE;
const EMAIL = process.env.AUTHORITY_EMAIL;
const GOVERNMENT_ID = process.env.AUTHORITY_GOVERNMENT_ID;
const ADDRESS = process.env.AUTHORITY_ADDRESS || 'Tamil Nadu';
const PINCODE = process.env.AUTHORITY_PINCODE || '600001';
const PASSWORD = process.env.AUTHORITY_PASSWORD;
const AUTHORITY_TYPE = process.env.AUTHORITY_TYPE || 'DISTRICT_COLLECTOR';
const DISTRICT = process.env.AUTHORITY_DISTRICT || '';

for (const [key, value] of Object.entries({ ADMIN_BOOTSTRAP_KEY: ADMIN_KEY, AUTHORITY_NAME: NAME, AUTHORITY_PHONE: PHONE, AUTHORITY_EMAIL: EMAIL, AUTHORITY_GOVERNMENT_ID: GOVERNMENT_ID, AUTHORITY_PASSWORD: PASSWORD })) {
  if (!value) throw new Error(`${key} is required in the environment.`);
}

async function makeRequest(method, path, data, customHeaders = {}) {
  const res = await fetch(API_BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...customHeaders },
    body: data === undefined ? undefined : JSON.stringify(data),
  });
  const responseData = await res.json().catch(() => ({}));
  return { status: res.status, data: responseData };
}

(async () => {
  const invite = await makeRequest('POST', '/api/invites', {
    authorityType: AUTHORITY_TYPE,
    district: DISTRICT || null,
    expiresInHours: 72,
  }, { 'x-admin-key': ADMIN_KEY });

  if (invite.status !== 201) throw new Error(`Invite creation failed (${invite.status}): ${JSON.stringify(invite.data)}`);

  const registration = await makeRequest('POST', '/api/auth/register', {
    name: NAME,
    phone: PHONE,
    email: EMAIL,
    governmentId: GOVERNMENT_ID,
    address: ADDRESS,
    pincode: PINCODE,
    password: PASSWORD,
    role: 'AUTHORITY',
    authorityType: AUTHORITY_TYPE,
    district: DISTRICT,
    inviteCode: invite.data.code,
  });

  if (registration.status !== 201) throw new Error(`Authority registration failed (${registration.status}): ${JSON.stringify(registration.data)}`);

  console.log(`Authority created: ${registration.data.user.email}`);
  console.log(`Authority type: ${registration.data.user.authorityType}`);
  console.log(`Invite used: ${invite.data.code}`);
})().catch(error => {
  console.error('Registration error:', error.message);
  process.exitCode = 1;
});
