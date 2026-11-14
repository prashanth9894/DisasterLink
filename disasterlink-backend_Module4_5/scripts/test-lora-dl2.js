// Protocol-level Module 3 test. No hardware or external service is required.
// This reproduces the HMAC portion used by both ESP32 sketches.
const crypto = require('crypto');

const key = 'module3-test-key';
const unsigned = 'DL2|FIELD-001|12345|SOS|abcdef0123456789abcdef0123456789|123456789012|Test Citizen|9876543210|Chennai|13.082700|80.270700|Offline SOS via ESP32';
const tag = crypto.createHmac('sha256', key).update(unsigned).digest('hex').slice(0, 16);
const frame = `${unsigned}|${tag}`;

const fields = frame.split('|');
const receivedTag = fields.pop();
const receivedUnsigned = fields.join('|');
const expectedTag = crypto.createHmac('sha256', key).update(receivedUnsigned).digest('hex').slice(0, 16);

if (receivedTag !== expectedTag) throw new Error('HMAC verification failed');
if (fields.length !== 12) throw new Error(`Expected 12 unsigned fields, got ${fields.length}`);
if (Buffer.byteLength(frame, 'utf8') > 255) throw new Error(`Frame too large: ${Buffer.byteLength(frame, 'utf8')} bytes`);
console.log('Module 3 DL2 protocol test: PASS');
console.log(`Frame bytes: ${Buffer.byteLength(frame, 'utf8')}`);
console.log(`HMAC: ${tag}`);
