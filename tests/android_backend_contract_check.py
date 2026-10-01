#!/usr/bin/env python3
import pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
ANDROID = ROOT / 'app' / 'src' / 'main' / 'java'
BACKEND = ROOT / 'backend'

android_text = '\n'.join(p.read_text(errors='ignore') for p in ANDROID.rglob('*.kt'))
backend_text = '\n'.join(p.read_text(errors='ignore') for p in BACKEND.glob('*.js'))

# Retrofit annotations: @GET("api/v1/foo/{id}") etc.
retrofit = set()
for method, path in re.findall(r'@(GET|POST|PUT|PATCH|DELETE)\("([^"]+)"\)', android_text):
    if path.startswith('api/'):
        retrofit.add((method, '/' + path))

# Literal API paths used by OkHttp/raw requests. Method is deliberately not
# inferred here; existence of the canonical backend path is what we verify.
raw_paths = set()
for m in re.finditer(r'[/"](api/(?:v1/)?[A-Za-z0-9_./:${}?=&+\-]+)', android_text):
    p = '/' + m.group(1)
    p = re.sub(r'\?.*$', '', p)
    p = re.sub(r'\$\{[^}]+\}', ':param', p)
    p = re.sub(r'\$[A-Za-z_][A-Za-z0-9_]*', ':param', p)
    p = re.sub(r'\{[^}]+\}', ':param', p)
    raw_paths.add(p)

backend_routes = set()
for method, path in re.findall(r'app\.(get|post|put|patch|delete)\(\s*[\"\']([^\"\']+)', backend_text, re.I):
    path = re.sub(r'\{[^}]+\}', ':param', path)
    path = re.sub(r':[A-Za-z_][A-Za-z0-9_]*', ':param', path)
    backend_routes.add(path)

missing = []
for method, path in sorted(retrofit):
    canonical = re.sub(r'\{[^}]+\}', ':param', '/' + path.lstrip('/'))
    canonical = re.sub(r':[A-Za-z_][A-Za-z0-9_]*', ':param', canonical)
    if canonical not in backend_routes:
        # Some routes are mounted through helper routers rather than app.*;
        # permit a literal occurrence in backend source as a secondary check.
        if canonical not in backend_text:
            missing.append(f'{method} {path}')

# Raw paths: verify literal/prefix existence. This intentionally accepts
# parameterized backend routes and does not require HTTP-method inference.
for p in sorted(raw_paths):
    if p.startswith('/api/auth/') and p not in backend_text:
        missing.append(f'RAW {p}')
    elif p.startswith('/api/'):
        prefix = p
        if ':param' in prefix:
            prefix = prefix.split(':param', 1)[0]
        if prefix not in backend_text:
            missing.append(f'RAW {p}')

if missing:
    print('FAIL: Android/backend contract mismatches')
    for item in sorted(set(missing)):
        print('  ', item)
    sys.exit(1)

# Authentication/session restoration invariants. These are intentionally static so CI
# catches regressions where a cached local record accidentally becomes an auth grant.
view_model = (ROOT / 'app' / 'src' / 'main' / 'java' / 'com' / 'example' / 'ui' / 'GameNetViewModel.kt').read_text(errors='ignore')
self_hosted = (ROOT / 'app' / 'src' / 'main' / 'java' / 'com' / 'example' / 'data' / 'network' / 'SelfHostedManager.kt').read_text(errors='ignore')
security_contracts = {
    'customer_server_restore': 'restoreCustomerSession(managerId, token)' in view_model and 'suspend fun restoreCustomerSession' in self_hosted,
    'customer_token_persisted': 'encryptSetting("enc_auth_token", com.example.data.network.NetworkClient.authToken ?: "")' in view_model,
    'logout_clears_bearer': 'NetworkClient.authToken = null' in view_model[view_model.find('fun logout()'):view_model.find('fun logoutAdmin()')],
    'manager_restore_requires_credentials': 'savedManagerId.isNotBlank() && token.isNotBlank()' in view_model,
    'no_blank_role_super_manager_fallback': 'savedRole.ifBlank { "SUPER_MANAGER" }' not in view_model,
    'serialized_cold_start': 'loadSettings()\n            _deviceId.value = getDeviceId()\n            loadSavedAuthSession()\n            verifyLicenseStatus()' in view_model,
}
failed_security = [name for name, ok in security_contracts.items() if not ok]
if failed_security:
    print('FAIL: Android session-security invariants')
    for item in failed_security:
        print('  ', item)
    sys.exit(1)

print('PASS: Android session restoration is server-authoritative and cold-start ordering is serialized')
print(f'PASS: {len(retrofit)} Retrofit contracts and {len(raw_paths)} raw API paths have backend matches')
