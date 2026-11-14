const crypto = require('crypto');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '..', '.env') });
const { db } = require('../db/db');

const VALID_AUTHORITY_TYPES = ['VAO', 'TAHSILDAR', 'DISTRICT_COLLECTOR', 'STATE_DISASTER_MANAGEMENT_AUTHORITY', 'CM'];

function generateCode() {
  const raw = crypto.randomBytes(6).toString('hex').toUpperCase();
  return `${raw.slice(0, 4)}-${raw.slice(4, 8)}-${raw.slice(8, 12)}`;
}

const args = process.argv.slice(2);
const authorityType = (args[0] || 'DISTRICT_COLLECTOR').toUpperCase();
const district = args[1] && args[1] !== 'all' ? args[1] : null;
const hours = Number(args[2]) > 0 ? Number(args[2]) : 720; // 30 days default

if (!VALID_AUTHORITY_TYPES.includes(authorityType)) {
  console.error(`Invalid authority type: "${authorityType}".`);
  console.error(`Valid options are: ${VALID_AUTHORITY_TYPES.join(', ')}`);
  process.exit(1);
}

const code = generateCode();
const expiresAt = Date.now() + hours * 60 * 60 * 1000;
const issuedBy = 'CLI_TOOL';

db.prepare('INSERT INTO invite_codes (code, authority_type, district, issued_by, expires_at) VALUES (?, ?, ?, ?, ?)')
  .run(code, authorityType, district, issuedBy, expiresAt);

console.log('\n--- DisasterLink Invite Code Created ---');
console.log(`Code:           ${code}`);
console.log(`Authority Type: ${authorityType}`);
console.log(`District:       ${district || 'ALL districts (unrestricted)'}`);
console.log(`Valid For:      ${hours} hours (~${Math.round(hours / 24)} days)`);
console.log(`Expires At:     ${new Date(expiresAt).toLocaleString()}`);
console.log('----------------------------------------\n');

