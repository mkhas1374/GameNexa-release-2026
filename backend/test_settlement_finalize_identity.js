const fs = require('fs');
const assert = require('assert');
const routes = fs.readFileSync(__dirname + '/canonical_routes.js', 'utf8');
const manager = fs.readFileSync(__dirname + '/../app/src/main/java/com/example/data/network/SelfHostedManager.kt', 'utf8');
const viewModel = fs.readFileSync(__dirname + '/../app/src/main/java/com/example/ui/GameNetViewModel.kt', 'utf8');

assert(routes.includes('local_id=$3 OR id=$3'), 'finalize must resolve Room local IDs to server rows');
assert(routes.includes(`customer_transactions.status IN ('REVIEWED','DEBTOR','PARTIAL') AND EXCLUDED.status='UNREVIEWED'`), 'stale sync must not downgrade finalized status');
assert(routes.includes(`customer_snapshot->>'name'=$5`), 'duplicate guest invoices must be finalized by session and participant');
assert(routes.includes('Idempotency must not mean'), 'idempotent retries must repair stale invoice/transaction status');
assert(routes.includes('Legacy sessions may have invoices/transactions but no participant rows'), 'legacy sessions without participant rows must use the actual invoice identity');
assert(routes.includes('legacyTx.forEach'), 'legacy sessions without invoice snapshots must fall back to transaction identity');
assert(routes.includes('status:retryStatus,idempotent:true'), 'idempotent response must return the invoice status, not refund-marker status');
assert(manager.includes('o.has("local_id") && !o.isNull("local_id")'), 'hydration must preserve local_id as Room identity');
assert(viewModel.includes('if (repository.syncCustomerTransactionsFromServer()) return@withContext true'), 'finalization must refresh authoritative transactions');
assert(!viewModel.includes('val exact = if (txId > 0L)'), 'server IDs must not be applied directly to unrelated Room rows');
console.log('PASS: settlement finalization preserves local identity, prevents stale status downgrade, and refreshes authoritative state');
