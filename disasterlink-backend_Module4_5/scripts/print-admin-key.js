const crypto = require('crypto');
console.log('JWT_SECRET=' + crypto.randomBytes(32).toString('hex'));
console.log('ADMIN_BOOTSTRAP_KEY=' + crypto.randomBytes(32).toString('hex'));
console.log('GATEWAY_KEY=' + crypto.randomBytes(32).toString('hex'));
console.log('\nCopy these three lines into your .env file (replacing the placeholder values).');
