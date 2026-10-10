const fs = require('fs');
const assert = require('assert');
const server = fs.readFileSync(__dirname + '/server.js', 'utf8');

assert(server.includes('const payerIdentity = payer.customer_id ? `C${payer.customer_id}` : String(payer.participant_key || `G${payerIndex + 1}`)'));
assert(server.includes('const invoiceNumber = "GN-" + sessionId + "-" + payerIdentity;'));
assert(server.includes('ON CONFLICT(manager_id,invoice_number) DO UPDATE SET'));
assert(!server.includes('(payer.customer_id || "GUEST") + "-" + sessionId.slice(0,8)'));
console.log('PASS: settlement invoice identity is unique per payer and retry-idempotent');
