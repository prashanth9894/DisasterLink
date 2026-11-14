require('dotenv').config();

const express = require('express');
const cors = require('cors');

require('./db/db'); // ensures tables exist on boot

const app = express();

app.use(cors());
// Evidence images are uploaded as compressed Base64 JSON over the operational LAN.
// A per-image request stays small, while this limit leaves room for transport overhead.
app.use(express.json({ limit: '4mb' }));

app.get('/health', (_req, res) => {
  res.json({
    status: 'ok',
    time: Date.now()
  });
});

app.use('/api/auth', require('./routes/auth'));
app.use('/api/auth/email-otp', require('./routes/emailOtp'));
app.use('/api/invites', require('./routes/invites'));
app.use('/api/emergencies', require('./routes/emergencies'));
app.use('/api/teams', require('./routes/teams'));
app.use('/api/tasks', require('./routes/tasks'));
app.use('/api/feedback', require('./routes/feedback'));

// Gateway API
app.use('/api/gateway', require('./routes/gateway'));

app.use('/api/dashboard', require('./routes/dashboard'));

app.use((err, _req, res, _next) => {
  console.error(err);

  res.status(500).json({
    error: 'Internal server error'
  });
});

const PORT = process.env.PORT || 8080;

if (require.main === module) {
  app.listen(PORT, () => {
    console.log(`DisasterLink backend listening on port ${PORT}`);
  });
}

module.exports = app;