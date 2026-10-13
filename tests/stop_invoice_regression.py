#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / "app/src/main/java/com/example"
BACK = ROOT / "backend"

MAIN = (A / "ui/MainScreen.kt").read_text(errors="ignore")
VM = (A / "ui/GameNetViewModel.kt").read_text(errors="ignore")
DIALOG = (A / "ui/CustomerDialogs.kt").read_text(errors="ignore")
UI = (A / "ui/CustomersReservationsScreen.kt").read_text(errors="ignore")
REPO = (A / "data/GameNetRepository.kt").read_text(errors="ignore")
CANON = (BACK / "canonical_routes.js").read_text(errors="ignore")

checks = []
def ok(name, condition):
    checks.append((name, bool(condition)))

# Stop regression: configured billing must bypass the three-option dialog.
ok(
    "configured duration/prepayment bypasses Stop dialog",
    'if (station.prepaymentAmount > 0L || station.durationLimitMinutes > 0) {' in MAIN
    and 'onPause()' in MAIN[MAIN.find('if (station.prepaymentAmount > 0L || station.durationLimitMinutes > 0)'):
        MAIN.find('if (station.prepaymentAmount > 0L || station.durationLimitMinutes > 0)') + 300]
)
ok(
    "unconfigured Stop still exposes the three-option workflow",
    'showStationPauseDialog = true' in MAIN
    and '1 -> viewModel.pauseStation(station.id)' in MAIN
    and '2 -> viewModel.commitSegmentAndPause' in MAIN
    and '3 -> viewModel.commitSegmentAndContinue' in MAIN
)
ok(
    "Stop executes after dialog dismissal",
    'LaunchedEffect(pendingStopOption)' in MAIN
    and 'showStationPauseDialog = false' in MAIN
    and 'pendingStopOption = 2' in MAIN
    and 'pendingStopOption = 3' in MAIN
)
stop_body = VM[VM.find('private fun applyStopStateLocally'):VM.find('fun resumeStation')]
ok("Stop persists local state before cloud sync", 'repository.insertStationStateLocal(updated)' in stop_body and stop_body.find('repository.insertStationStateLocal(updated)') < stop_body.find('saveAndSyncStationState(updated)'))
ok("Stop has a fatal guard", 'catch (t: Throwable)' in stop_body and 'STOP_FATAL_GUARD' in stop_body)
ok("Stop background sync cannot block local transition", 'runCatching { saveAndSyncStationState(updated) }' in stop_body)

# Invoice regression: server result must be applied to Room immediately, without a manual reload.
finalize_body = VM[VM.find('suspend fun finalizeSettlementReview'):VM.find('suspend fun refundUnusedPrepayment')]
ok("settlement finalization calls backend first", 'SelfHostedManager.finalizeSettlementReview(sessionId, decisions)' in finalize_body)
ok("settlement result is written to Room immediately", 'repository.updateCustomerTransaction(tx.copy(status=status' in finalize_body)
ok("settlement refresh is background-only", 'viewModelScope.launch(Dispatchers.IO)' in finalize_body and 'syncCustomerTransactionsFromServer()' in finalize_body)
ok("Done closes dialog after authoritative success", 'showPrepaymentDialog = false' in UI and 'if (ok)' in UI)
ok("Done does not re-submit through legacy status updater", 'Do NOT call the legacy' in UI and 'onUpdateStatus(status)' not in UI[UI.find('if (ok)'):UI.find('if (ok)')+1500])
ok("manager transaction PATCH is server-authoritative", "app.patch('/api/v1/manager/customer-transactions/:id'" in CANON)
ok("PATCH clamps paid amount and derives final status on server", "const status=amount<=0||paid>=amount?'REVIEWED':(requested==='UNREVIEWED'?'UNREVIEWED':'DEBTOR')" in CANON)
ok("settlement finalization persists transaction status", 'customer_transactions SET status=$1,paid_amount=$2' in CANON)
ok("settlement debt reconciliation is idempotent", 'debtBeforeOutstanding' in CANON and 'debtAfterOutstanding' in CANON and 'debtDelta=debtAfterOutstanding-debtBeforeOutstanding' in CANON)

failed = [name for name, value in checks if not value]
for name, value in checks:
    print(("PASS" if value else "FAIL") + ": " + name)
if failed:
    print(f"FAILED CHECKS: {len(failed)}")
    sys.exit(1)
print(f"PASS: stop/invoice regression ({len(checks)} invariants)")
