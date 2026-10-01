const express = require('express');
const cors = require('cors');
const { Pool } = require('pg');
const { cancelReservation, completeVipReservation, DEFAULT_RESERVATION_CONFIGURATION, deepMerge } = require('./financialService');
const jwt = require('jsonwebtoken');
const bcrypt = require('bcrypt');
require('dotenv').config();

const app = express();
app.set('trust proxy', 1);
require("./bootstrap.js");
const allowedOrigins = new Set((process.env.CORS_ORIGINS || '').split(',').map(v => v.trim()).filter(Boolean));
app.use(cors({
    origin: (origin, callback) => {
        if (!origin || allowedOrigins.has(origin)) return callback(null, true);
        return callback(new Error('CORS origin not allowed'));
    },
    methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization', 'X-GameNet-Timestamp', 'X-GameNet-Signature', 'X-Manager-ID', 'Idempotency-Key']
}));
app.use(express.json({ limit: process.env.JSON_BODY_LIMIT || '256kb' }));
app.disable('x-powered-by');
app.use((req, res, next) => {
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('Referrer-Policy', 'no-referrer');
    next();
});

const rateBuckets = new Map();
const rateLimit = ({ windowMs = 60_000, max = 30 } = {}) => (req, res, next) => {
    const key = `${req.ip}:${req.path}`;
    const now = Date.now();
    const bucket = rateBuckets.get(key);
    if (!bucket || now - bucket.startedAt >= windowMs) {
        rateBuckets.set(key, { startedAt: now, count: 1 });
        return next();
    }
    bucket.count += 1;
    if (bucket.count > max) {
        res.setHeader('Retry-After', Math.ceil((windowMs - (now - bucket.startedAt)) / 1000));
        return res.status(429).json({ error: 'Too many requests' });
    }
    return next();
};

const pool = new Pool({
  connectionString: process.env.DATABASE_URL
});

// Monetary values exposed to customers/managers are whole Tomans.
// Internal PostgreSQL NUMERIC calculations may retain sub-Toman precision for exact
// second-based billing, but all persisted invoice/session money is canonicalized down
// to the nearest whole Toman before it becomes a billable amount.
const normalizeMoneyInteger = (value) => {
    const text = String(value ?? '0').trim();
    if (!/^\d+(?:\.\d+)?$/.test(text)) return null;
    const whole = text.split('.')[0];
    try { return BigInt(whole).toString(); } catch (_) { return null; }
};

const JWT_SECRET = process.env.JWT_SECRET;
if (!JWT_SECRET) throw new Error('JWT_SECRET is not set');

// Auth Middleware for Manager

const requireSuperManagerAuth = async (req, res, next) => {
    const authHeader = req.headers.authorization;
    if (!authHeader || !/^Bearer\s+\S+$/i.test(authHeader)) {
        return res.status(401).json({ error: 'No token provided' });
    }
    const token = authHeader.slice(7).trim();
    try {
        const decoded = jwt.verify(token, JWT_SECRET, { algorithms: ['HS256'] });
        if (decoded.role !== 'SUPER_MANAGER' || !decoded.id || !decoded.managerId || String(decoded.id) !== String(decoded.managerId)) return res.status(403).json({ error: 'Forbidden' });
        const account = await pool.query("SELECT id, role FROM managers WHERE id = $1 AND role = 'SUPER_MANAGER' LIMIT 1", [decoded.id]);
        if (!account.rows.length) return res.status(401).json({ error: 'Manager account no longer exists' });
        const entitlement = await pool.query("SELECT 1 FROM manager_entitlements WHERE manager_id=$1 AND entitlement_type='SUPER_MANAGER_LIFETIME' AND status='ACTIVE' AND starts_at<=NOW() AND expires_at>NOW() LIMIT 1", [decoded.id]);
        if (!entitlement.rows.length) return res.status(403).json({ error: 'Active Super Manager entitlement required', code: 'SUPER_MANAGER_ENTITLEMENT_REQUIRED' });
        req.user = decoded;
        next();
    } catch (e) {
        return res.status(401).json({ error: 'Invalid token' });
    }
};

const requireManagerAuth = async (req, res, next) => {
    const authHeader = req.headers.authorization;
    if (!authHeader || !/^Bearer\s+\S+$/i.test(authHeader)) {
        return res.status(401).json({ error: 'No token provided' });
    }
    const token = authHeader.slice(7).trim();
    try {
        const decoded = jwt.verify(token, JWT_SECRET, { algorithms: ['HS256'] });
        if (!['MANAGER', 'SUPER_MANAGER'].includes(decoded.role) || !decoded.id || !decoded.managerId || String(decoded.id) !== String(decoded.managerId)) {
            return res.status(403).json({ error: 'Forbidden' });
        }
        const managerHeader = String(req.headers['x-manager-id'] || '').trim();
        if (!managerHeader || managerHeader !== String(decoded.managerId)) {
            return res.status(403).json({ error: 'Manager identity header mismatch' });
        }
        const account = await pool.query("SELECT id, role FROM managers WHERE id = $1 AND role = ANY($2::text[]) LIMIT 1", [decoded.id, ['MANAGER', 'SUPER_MANAGER']]);
        if (!account.rows.length) return res.status(401).json({ error: 'Manager account no longer exists' });
        req.user = decoded;
        next();
    } catch (e) {
        return res.status(401).json({ error: 'Invalid token' });
    }
};

// Auth Middleware for Customer
const requireCustomerAuth = async (req, res, next) => {
    const authHeader = req.headers.authorization;
    if (!authHeader || !/^Bearer\s+\S+$/i.test(authHeader)) {
        return res.status(401).json({ error: 'No token provided' });
    }
    const token = authHeader.slice(7).trim();
    try {
        const decoded = jwt.verify(token, JWT_SECRET, { algorithms: ['HS256'] });
        if (decoded.role !== 'CUSTOMER' || !decoded.id || !decoded.managerId) {
            return res.status(403).json({ error: 'Forbidden' });
        }
        const account = await pool.query('SELECT id, manager_id FROM customers WHERE id = $1 AND manager_id = $2 LIMIT 1',[decoded.id, decoded.managerId]);
        if (!account.rows.length) return res.status(401).json({ error: 'Customer account no longer exists' });
        req.user = decoded;
        next();
    } catch (e) {
        return res.status(401).json({ error: 'Invalid token' });
    }
};


// --- ENTITLEMENT ACCESS GATE ---

const getManagerEntitlement = async (managerId) => {
    if (!managerId) return null;

    const result = await pool.query(`
        SELECT
            id,
            manager_id,
            entitlement_type,
            plan_id,
            status,
            starts_at,
            expires_at,
            source,
            max_stations,
            max_customers,
            allowed_console_type,
            metadata
        FROM manager_entitlements
        WHERE manager_id = $1
          AND status = 'ACTIVE'
          AND starts_at <= NOW()
          AND expires_at > NOW()
        ORDER BY expires_at DESC
        LIMIT 1
    `, [managerId]);

    return result.rows[0] || null;
};

const requireActiveEntitlement = async (req, res, next) => {
    try {
        const managerId = req.user?.managerId || req.user?.id;

        if (!managerId) {
            return res.status(401).json({
                error: 'Manager identity missing'
            });
        }

        const entitlement = await getManagerEntitlement(managerId);

        if (!entitlement) {
            return res.status(403).json({
                error: 'Active entitlement required',
                code: 'ENTITLEMENT_REQUIRED'
            });
        }

        req.entitlement = entitlement;
        next();
    } catch (e) {
        console.error('Entitlement access gate error:', e);
        return res.status(500).json({
            error: 'Entitlement verification failed',
            code: 'ENTITLEMENT_CHECK_FAILED'
        });
    }
};

// --- AUTH ENDPOINTS ---

app.post('/api/auth/manager/login', rateLimit({ windowMs: 60_000, max: 10 }), async (req, res) => {
    const { username, password } = req.body;
    try {
        const result = await pool.query('SELECT * FROM managers WHERE username = $1', [username]);
        const manager = result.rows[0];
        if (!manager) return res.status(401).json({ error: 'Invalid credentials' });
        
        // Use bcrypt to check password
        const valid = await bcrypt.compare(password, manager.password_hash);
        if (!valid) {
            return res.status(401).json({ error: 'Invalid credentials' });
        }

        {
            const deviceId = String(req.body?.deviceId || req.body?.device_id || '').trim().slice(0,255);
            if (!deviceId) return res.status(400).json({ error:'Manager device identity is required' });
            const entitlement = await pool.query(
                "SELECT max_devices, entitlement_type, plan_id FROM manager_entitlements WHERE manager_id=$1 AND status='ACTIVE' AND starts_at<=NOW() AND expires_at>NOW() ORDER BY expires_at DESC LIMIT 1",
                [manager.id]
            );
            if (!entitlement.rows[0]) return res.status(403).json({ error:'Active subscription required', code:'ENTITLEMENT_REQUIRED' });
            const maxDevices = Math.max(1, Number(entitlement.rows[0].max_devices || 1));
            const lock = await pool.connect();
            try {
                await lock.query('BEGIN');
                await lock.query('SELECT pg_advisory_xact_lock(hashtext($1))',[String(manager.id)]);
                const known = await lock.query("SELECT id FROM manager_device_bindings WHERE manager_id=$1 AND device_id=$2 AND active=TRUE LIMIT 1",[manager.id,deviceId]);
                if (!known.rows[0]) {
                    const count = await lock.query("SELECT COUNT(*)::int AS count FROM manager_device_bindings WHERE manager_id=$1 AND active=TRUE",[manager.id]);
                    if (Number(count.rows[0].count || 0) >= maxDevices) {
                        await lock.query('ROLLBACK');
                        return res.status(403).json({ error:'Maximum authorized devices reached', code:'DEVICE_LIMIT_REACHED', maxDevices });
                    }
                    await lock.query("INSERT INTO manager_device_bindings(manager_id,device_id) VALUES($1,$2) ON CONFLICT(manager_id,device_id) DO UPDATE SET last_seen_at=NOW(),active=TRUE",[manager.id,deviceId]);
                } else {
                    await lock.query("UPDATE manager_device_bindings SET last_seen_at=NOW() WHERE manager_id=$1 AND device_id=$2",[manager.id,deviceId]);
                }
                await lock.query('COMMIT');
            } catch (deviceError) {
                try { await lock.query('ROLLBACK'); } catch (_) {}
                throw deviceError;
            } finally {
                lock.release();
            }
        }

        const token = jwt.sign({ id: manager.id, managerId: manager.id, role: manager.role || 'MANAGER' }, JWT_SECRET, { expiresIn: '24h' });
        res.json({ token, managerId: manager.id, role: manager.role || 'MANAGER' });
    } catch (e) {
        console.error('Manager login error:', e);
        res.status(500).json({ error: 'Internal server error' });
    }
});


app.post('/api/auth/customer/register', rateLimit({ windowMs: 60_000, max: 5 }), async (req, res) => {
    const phoneNumber = String(req.body?.phone_number || req.body?.phoneNumber || '').trim();
    const managerId = String(req.body?.manager_id || req.body?.managerId || '').trim();
    const fullName = String(req.body?.full_name || req.body?.fullName || '').trim();
    const password = typeof req.body?.password === 'string' ? req.body.password : '';
    if (!phoneNumber || !managerId || !fullName || password.length < 4) {
        return res.status(400).json({ error: 'phone_number, manager_id, full_name and a password of at least 4 characters are required' });
    }
    try {
        const manager = await pool.query("SELECT id FROM managers WHERE id = $1 AND role = 'MANAGER' LIMIT 1", [managerId]);
        if (manager.rows.length === 0) return res.status(404).json({ error: 'Manager not found' });
        const existing = await pool.query('SELECT id FROM customers WHERE manager_id = $1 AND phone_number = $2 LIMIT 1', [managerId, phoneNumber]);
        if (existing.rows.length > 0) return res.status(409).json({ error: 'Customer already exists for this Manager' });
        const hash = await bcrypt.hash(password, 12);
        const created = await pool.query(`INSERT INTO customers (manager_id, phone_number, full_name, password_hash)
            VALUES ($1,$2,$3,$4)
            RETURNING id, manager_id, phone_number, full_name, club_tier, wallet_balance, gn_balance, lp_balance, created_at, updated_at`,
            [managerId, phoneNumber, fullName, hash]);
        const customer = created.rows[0];
        const token = jwt.sign({ id: customer.id, managerId, role: 'CUSTOMER' }, JWT_SECRET, { expiresIn: '24h' });
        res.status(201).json({ success: true, token, customerId: customer.id, customer });
    } catch (e) {
        console.error('Customer registration error:', e);
        res.status(500).json({ error: 'Internal server error' });
    }
});

app.post('/api/auth/customer/login', rateLimit({ windowMs: 60_000, max: 10 }), async (req, res) => {
    const { phone_number, manager_id, password } = req.body;
    if (!phone_number || !manager_id || typeof password !== 'string') {
        return res.status(400).json({ error: 'phone_number, manager_id and password are required' });
    }
    try {
        const result = await pool.query('SELECT * FROM customers WHERE phone_number = $1 AND manager_id = $2', [phone_number, manager_id]);
        const customer = result.rows[0];
        if (!customer) return res.status(401).json({ error: 'Customer not found' });
        if (!customer.password_hash) return res.status(401).json({ error: 'Customer password is not configured' });
        const valid = await bcrypt.compare(password, customer.password_hash);
        if (!valid) return res.status(401).json({ error: 'Invalid credentials' });

        const token = jwt.sign({ id: customer.id, managerId: customer.manager_id, role: 'CUSTOMER' }, JWT_SECRET, { expiresIn: '24h' });
        res.json({ token, customerId: customer.id });
    } catch (e) {
        res.status(500).json({ error: 'Internal server error' });
    }
});



// Canonical server clock. Clients use this only as a reference/display clock;
// financial lifecycle timestamps remain server-authoritative.
app.get('/api/v1/time', rateLimit({ windowMs: 60_000, max: 60 }), (req, res) => {
    res.set('Cache-Control', 'no-store, no-cache, must-revalidate');
    return res.json({
        serverTime: Date.now(),
        timezone: 'Asia/Tehran'
    });
});

// --- TRIAL 24H CANONICAL PUBLIC FLOW ---
const normalizeTrialIdentity = (value) => String(value || '').trim().slice(0, 255);

app.post('/api/v1/trial/start', rateLimit({ windowMs: 60_000, max: 5 }), async (req, res) => {
    const deviceId = normalizeTrialIdentity(req.body?.deviceId || req.body?.device_id);
    const deviceFingerprint = normalizeTrialIdentity(req.body?.deviceFingerprint || req.body?.device_fingerprint);
    const deviceName = normalizeTrialIdentity(req.body?.deviceName || req.body?.device_name || 'Android');
    if (!deviceId || !deviceFingerprint) return res.status(400).json({ success: false, trialActive: false, isExpired: true, message: 'Device identity is required.' });
    const client = await pool.connect();
    try {
        await client.query('BEGIN');
        await client.query('SELECT pg_advisory_xact_lock(hashtext($1))', ['trial:' + deviceId + ':' + deviceFingerprint]);
        const blocked = await client.query('SELECT 1 FROM trial_device_blocks WHERE (device_id = $1 AND device_fingerprint = $2) OR device_id = $1 OR device_fingerprint = $2 LIMIT 1', [deviceId, deviceFingerprint]);
        if (blocked.rows.length > 0) {
            await client.query('COMMIT');
            return res.status(403).json({ success: false, trialActive: false, isExpired: true, remainingMinutes: 0, message: 'این دستگاه قبلاً Trial خود را استفاده کرده یا توسط Super Manager حذف شده است.' });
        }
        const existing = await client.query('SELECT device_id, device_fingerprint, started_at, expires_at, status FROM trial_devices WHERE device_id = $1 OR device_fingerprint = $2 ORDER BY started_at DESC, created_at DESC LIMIT 1 FOR UPDATE', [deviceId, deviceFingerprint]);
        if (existing.rows.length > 0) {
            const trial = existing.rows[0];
            const expiresAt = new Date(trial.expires_at);
            const expired = trial.status !== 'ACTIVE' || !Number.isFinite(expiresAt.getTime()) || expiresAt.getTime() <= Date.now();
            await client.query('COMMIT');
            return res.json({ success: true, trialActive: !expired, isExpired: expired, remainingMinutes: expired ? 0 : Math.max(0, Math.ceil((expiresAt.getTime() - Date.now()) / 60000)), expiresAt: expiresAt.getTime(), serverTime: Date.now(), message: expired ? 'مهلت تست ۲۴ ساعته این دستگاه به پایان رسیده است.' : 'تست ۲۴ ساعته فعال است.' });
        }
        const startedAt = new Date();
        const expiresAt = new Date(startedAt.getTime() + 24 * 60 * 60 * 1000);
        await client.query('INSERT INTO trial_devices (device_id, device_fingerprint, device_name, started_at, expires_at, status) VALUES ($1, $2, $3, $4, $5, \'ACTIVE\')', [deviceId, deviceFingerprint, deviceName, startedAt, expiresAt]);
        await client.query('COMMIT');
        return res.status(201).json({ success: true, trialActive: true, isExpired: false, remainingMinutes: 1440, expiresAt: expiresAt.getTime(), serverTime: startedAt.getTime(), message: 'تست ۲۴ ساعته با موفقیت فعال شد.' });
    } catch (e) {
        await client.query('ROLLBACK');
        console.error('Canonical trial start error:', e);
        return res.status(500).json({ success: false, trialActive: false, isExpired: true, message: 'خطا در فعال‌سازی تست.' });
    } finally { client.release(); }
});

app.post('/api/v1/trial/status', rateLimit({ windowMs: 60_000, max: 20 }), async (req, res) => {
    const deviceId = normalizeTrialIdentity(req.body?.deviceId || req.body?.device_id);
    const deviceFingerprint = normalizeTrialIdentity(req.body?.deviceFingerprint || req.body?.device_fingerprint);
    if (!deviceId || !deviceFingerprint) return res.status(400).json({ success: false, trialActive: false, isExpired: true, message: 'Device identity is required.' });
    try {
        const result = await pool.query('SELECT expires_at, status FROM trial_devices WHERE device_id = $1 AND device_fingerprint = $2 ORDER BY created_at DESC LIMIT 1', [deviceId, deviceFingerprint]);
        if (result.rows.length === 0) return res.status(404).json({ success: false, trialActive: false, isExpired: true, remainingMinutes: 0, message: 'No trial has been activated on this device.' });
        const trial = result.rows[0];
        const expiresAt = new Date(trial.expires_at);
        const expired = trial.status !== 'ACTIVE' || !Number.isFinite(expiresAt.getTime()) || expiresAt.getTime() <= Date.now();
        return res.json({ success: true, trialActive: !expired, isExpired: expired, remainingMinutes: expired ? 0 : Math.max(0, Math.ceil((expiresAt.getTime() - Date.now()) / 60000)), expiresAt: expiresAt.getTime(), serverTime: Date.now(), message: expired ? 'مهلت تست ۲۴ ساعته این دستگاه به پایان رسیده است.' : 'تست ۲۴ ساعته فعال است.' });
    } catch (e) { return res.status(500).json({ success: false, trialActive: false, isExpired: true, message: 'خطا در بررسی اعتبار تست.' }); }
});

// Canonical API routes are registered in canonical_routes.js.

// SUPER MANAGER missing endpoints for Android App

app.get('/api/v1/super-manager/managers', requireSuperManagerAuth, async (req, res) => {
    try {
        const result = await pool.query(`
            SELECT m.id, m.username, m.role, m.created_at, m.display_name, m.gamenet_name,
                   m.phone, m.plan_type, m.subscription_status, m.payment_status,
                   e.plan_id AS entitlement_plan_id, e.starts_at, e.expires_at, e.max_devices
            FROM managers m
            LEFT JOIN LATERAL (
                SELECT plan_id, starts_at, expires_at, max_devices
                FROM manager_entitlements
                WHERE manager_id = m.id
                  AND status = 'ACTIVE'
                ORDER BY expires_at DESC
                LIMIT 1
            ) e ON TRUE
            ORDER BY m.created_at DESC
        `);
        const mapped = result.rows.map(r => {
            const hasActiveEntitlement = !!r.expires_at && new Date(r.expires_at).getTime() > Date.now();
            return {
                id: r.id,
                managerId: r.id,
                username: r.username,
                phone: r.phone || r.username,
                full_name: r.display_name || r.username,
                gameneName: r.gamenet_name || '',
                planType: r.plan_type || r.entitlement_plan_id || '',
                subscriptionStatus: hasActiveEntitlement ? 'ACTIVE' : 'EXPIRED',
                paymentStatus: r.payment_status || (hasActiveEntitlement ? 'PAID' : 'UNPAID'),
                plan_name: r.role || 'MANAGER',
                role: r.role || 'MANAGER',
                activation_date: r.starts_at || r.created_at,
                expiryDate: r.expires_at || null,
                expires_at: r.expires_at || null,
                maxDevices: Number(r.max_devices || 1),
                status: hasActiveEntitlement ? 'ACTIVE' : 'EXPIRED'
            };
        });
        res.json(mapped);
    } catch (e) {
        console.error('API internal error:', e); res.status(500).json({ error: 'Internal server error' });
    }
});

app.post('/api/v1/super-manager/add-manager', requireSuperManagerAuth, async (req, res) => {
    const username = String(req.body?.username || req.body?.phone || '').trim();
    const password = String(req.body?.password || '');
    const displayName = String(req.body?.fullName || req.body?.full_name || '').trim();
    const gamenetName = String(req.body?.gameneName || req.body?.gamenet_name || '').trim();
    const phone = String(req.body?.phone || username).trim();
    const rawPlan = String(req.body?.planType || req.body?.plan_name || '').trim();
    const maxDevices = Number(req.body?.maxDevices);
    const plans = {'۱ ماهه':{id:'1_MONTH',days:30},'1 ماهه':{id:'1_MONTH',days:30},'۳ ماهه':{id:'THREE_MONTHS',days:90},'3 ماهه':{id:'THREE_MONTHS',days:90},'۱۲ ماهه':{id:'YEARLY',days:365},'12 ماهه':{id:'YEARLY',days:365},'1_MONTH':{id:'1_MONTH',days:30},'THREE_MONTHS':{id:'THREE_MONTHS',days:90},'YEARLY':{id:'YEARLY',days:365}};
    const plan = plans[rawPlan];
    if (!username || !password || !displayName || !gamenetName || !plan || !Number.isInteger(maxDevices) || maxDevices < 1 || maxDevices > 100) return res.status(400).json({success:false,error:'اطلاعات کامل و معتبر ساخت پنل مدیر لازم است.'});
    if (password.length < 8) return res.status(400).json({success:false,error:'رمز عبور باید حداقل ۸ کاراکتر باشد.'});
    const client = await pool.connect();
    try {
        await client.query('BEGIN');
        const existing = await client.query('SELECT id FROM managers WHERE username=$1 FOR UPDATE',[username]);
        if (existing.rows[0]) { await client.query('ROLLBACK'); return res.status(409).json({success:false,error:'این نام کاربری قبلاً ثبت شده است.'}); }
        const managerId = 'mgr_' + require('crypto').randomUUID();
        const hash = await bcrypt.hash(password,12);
        const created = await client.query(`INSERT INTO managers(id,username,password_hash,role,display_name,gamenet_name,phone,plan_type,subscription_status,payment_status,created_at,updated_at)
            VALUES($1,$2,$3,'MANAGER',$4,$5,$6,$7,'ACTIVE','PAID',NOW(),NOW())
            RETURNING id,username,role,display_name,gamenet_name,phone,plan_type,subscription_status,payment_status,created_at`,[managerId,username,hash,displayName,gamenetName,phone,rawPlan]);
        const startsAt = new Date();
        const expiresAt = new Date(startsAt.getTime() + plan.days*86400000);
        await client.query(`INSERT INTO manager_entitlements(manager_id,entitlement_type,plan_id,status,starts_at,expires_at,source,max_devices,metadata)
            VALUES($1,'SUBSCRIPTION',$2,'ACTIVE',$3,$4,'SUPER_MANAGER',$5,$6::jsonb)`,[managerId,plan.id,startsAt,expiresAt,maxDevices,JSON.stringify({createdBy:req.user.id,salesChannel:'SUPER_MANAGER_MANAGER_SALES'})]);
        const pending = await client.query(`SELECT id FROM subscription_payment_requests WHERE status='PENDING' AND buyer_phone=$1 AND plan_id=$2 ORDER BY created_at DESC LIMIT 1 FOR UPDATE`,[username,plan.id]);
        if (pending.rows[0]) await client.query(`UPDATE subscription_payment_requests SET manager_id=$1,status='CONFIRMED',provisioned_account=TRUE,reviewed_at=NOW(),reviewed_by=$2,updated_at=NOW() WHERE id=$3`,[managerId,req.user.id,pending.rows[0].id]);
        await client.query('COMMIT');
        return res.status(201).json({success:true,id:managerId,managerId,username,phone,fullName:displayName,gameNetName:gamenetName,status:'ACTIVE',paymentStatus:'PAID',licenseCode:'ACTIVE_'+managerId,subscriptionStart:startsAt,subscriptionEnd:expiresAt,planId:plan.id,maxDevices,manager:created.rows[0]});
    } catch(e) {
        try { await client.query('ROLLBACK'); } catch(_) {}
        console.error('Manager panel creation error:',e);
        return res.status(400).json({success:false,error:'ثبت پنل مدیر انجام نشد.'});
    } finally { client.release(); }
});

app.post('/api/v1/super-manager/managers', requireSuperManagerAuth, async (req, res) => {
    return res.status(410).json({success:false,code:'USE_MANAGER_SALES_PANEL',message:'ساخت پنل مدیر فقط از مسیر فروش مدیر انجام می‌شود.'});
    // Alias to add-manager
    const { username, password, role } = req.body;
    if (!username || !password) return res.status(400).json({ error: 'Missing username or password' });
    try {
        const managerId = 'mgr_' + Date.now();
        const bcrypt = require('bcrypt');
        const hash = await bcrypt.hash(password, 10);
        try {
            await pool.query('INSERT INTO managers (id, username, password_hash, role) VALUES ($1, $2, $3, $4)', [managerId, username, hash, role || 'MANAGER']);
        } catch (e) {
            await pool.query('INSERT INTO managers (id, username, password_hash) VALUES ($1, $2, $3)', [managerId, username, hash]);
        }
        res.json({ success: true, managerId });
    } catch (e) {
        console.error('API internal error:', e); res.status(500).json({ error: 'Internal server error' });
    }
});

app.post('/api/v1/super-manager/create-manager', requireSuperManagerAuth, async (req, res) => {
    return res.status(410).json({success:false,code:'USE_MANAGER_SALES_PANEL',message:'ساخت پنل مدیر فقط از مسیر فروش مدیر انجام می‌شود.'});
    // Alias to add-manager
    const { username, password, role } = req.body;
    if (!username || !password) return res.status(400).json({ error: 'Missing username or password' });
    try {
        const managerId = 'mgr_' + Date.now();
        const bcrypt = require('bcrypt');
        const hash = await bcrypt.hash(password, 10);
        try {
            await pool.query('INSERT INTO managers (id, username, password_hash, role) VALUES ($1, $2, $3, $4)', [managerId, username, hash, role || 'MANAGER']);
        } catch (e) {
            await pool.query('INSERT INTO managers (id, username, password_hash) VALUES ($1, $2, $3)', [managerId, username, hash]);
        }
        res.json({ success: true, managerId });
    } catch (e) {
        console.error('API internal error:', e); res.status(500).json({ error: 'Internal server error' });
    }
});

app.post('/api/v1/super-manager/recover', requireSuperManagerAuth, async (req, res) => {
    const { phone } = req.body;
    if (!phone) return res.status(400).json({ error: 'Missing phone' });
    try {
        // Find a manager matching this phone number as username
        const result = await pool.query('SELECT id, username, role FROM managers WHERE username = $1', [phone]);
        if (result.rows.length === 0) return res.status(404).json({ error: 'Manager not found' });
        // Don't return secrets.
        res.json({ user: { manager_id: result.rows[0].id, full_name: result.rows[0].username, plan_name: result.rows[0].role || 'MANAGER', expires_at: Date.now() + 30*86400*1000 } });
    } catch (e) {
        console.error('API internal error:', e); res.status(500).json({ error: 'Internal server error' });
    }
});

app.get('/api/v1/super-manager/trial-devices', requireSuperManagerAuth, async (req, res) => {
    try {
        const result = await pool.query(`SELECT device_id, device_fingerprint, device_name, started_at, expires_at, status, created_at
                                         FROM trial_devices
                                         ORDER BY started_at DESC NULLS LAST, created_at DESC`);
        res.json(result.rows.map(row => ({
            ...row,
            startTime: row.started_at ? new Date(row.started_at).getTime() : null,
            expiryDate: row.expires_at ? new Date(row.expires_at).getTime() : null,
            expireTime: row.expires_at ? new Date(row.expires_at).getTime() : null
        })));
    } catch (e) {
        res.status(500).json({ error: 'Internal server error' });
    }
});

app.delete('/api/v1/super-manager/trial-devices/:id', requireSuperManagerAuth, async (req, res) => {
    const { id } = req.params;
    const client = await pool.connect();
    try {
        await client.query('BEGIN');
        const result = await client.query(`SELECT device_id, device_fingerprint, started_at, expires_at
                                           FROM trial_devices WHERE device_id = $1 FOR UPDATE`, [id]);
        if (result.rows.length === 0) {
            await client.query('ROLLBACK');
            return res.status(404).json({ success: false, message: 'Trial not found' });
        }
        const trial = result.rows[0];
        await client.query(`INSERT INTO trial_device_blocks (device_id, device_fingerprint, reason)
                            VALUES ($1, $2, 'DELETED_BY_SUPER_MANAGER')
                            ON CONFLICT DO NOTHING`, [trial.device_id, trial.device_fingerprint]);
        await client.query(`INSERT INTO trial_device_audit_log
                            (device_id, device_fingerprint, action, actor_manager_id, previous_started_at, previous_expires_at)
                            VALUES ($1, $2, 'DELETED', $3, $4, $5)`,
                            [trial.device_id, trial.device_fingerprint, req.user?.manager_id || req.user?.id || null, trial.started_at, trial.expires_at]);
        await client.query('DELETE FROM trial_devices WHERE device_id = $1', [id]);
        await client.query('COMMIT');
        res.json({ success: true, message: 'Deleted' });
    } catch (e) {
        await client.query('ROLLBACK');
        res.status(500).json({ success: false, error: 'Internal server error' });
    } finally { client.release(); }
});


app.post('/api/v1/super-manager/managers/:id/subscription/extend', requireSuperManagerAuth, async (req, res) => {
    const managerId = String(req.params.id || '').trim();
    const planId = String(req.body?.planId || req.body?.plan_id || '').trim();
    if (!managerId || !planId) return res.status(400).json({success:false,error:'managerId and planId are required'});
    try {
        const storeRaw = (await pool.query('SELECT settings FROM subscription_store_config WHERE id=1')).rows[0]?.settings || {};
        const plan = Array.isArray(storeRaw.plans) ? storeRaw.plans.find(p => String(p.id || '') === planId && p.active !== false) : null;
        if (!plan) return res.status(400).json({success:false,error:'Subscription plan is unavailable'});
        const days = Number(plan.durationDays || 0);
        if (!Number.isInteger(days) || days <= 0) return res.status(400).json({success:false,error:'Invalid subscription duration'});
        const c = await pool.connect();
        try {
            await c.query('BEGIN');
            await c.query('SELECT pg_advisory_xact_lock(hashtext($1))',[managerId]);
            const mgr = (await c.query("SELECT id,role FROM managers WHERE id=$1 AND role='MANAGER' FOR UPDATE",[managerId])).rows[0];
            if (!mgr) { await c.query('ROLLBACK'); return res.status(404).json({success:false,error:'Manager not found'}); }
            const current = (await c.query("SELECT id,plan_id,starts_at,expires_at,max_devices,status FROM manager_entitlements WHERE manager_id=$1 ORDER BY expires_at DESC LIMIT 1 FOR UPDATE",[managerId])).rows[0];
            const now = new Date();
            let startsAt = now;
            let expiresAt = new Date(now.getTime() + days*86400000);
            if (current && current.status === 'ACTIVE' && new Date(current.expires_at) > now) {
                startsAt = new Date(current.expires_at);
                expiresAt = new Date(startsAt.getTime() + days*86400000);
                await c.query("UPDATE manager_entitlements SET status='EXPIRED',updated_at=NOW() WHERE id=$1",[current.id]);
            } else if (current && current.status === 'ACTIVE') {
                await c.query("UPDATE manager_entitlements SET status='EXPIRED',updated_at=NOW() WHERE id=$1",[current.id]);
            }
            const maxDevices = Math.max(1,Number(req.body?.maxDevices || current?.max_devices || 1));
            const created = await c.query(`INSERT INTO manager_entitlements(manager_id,entitlement_type,plan_id,status,starts_at,expires_at,source,max_devices,metadata)
                VALUES($1,'SUBSCRIPTION',$2,'ACTIVE',$3,$4,'SUPER_MANAGER',$5,$6::jsonb) RETURNING id,plan_id,status,starts_at,expires_at,max_devices`,
                [managerId,planId,startsAt,expiresAt,maxDevices,JSON.stringify({extendedBy:req.user.id,previousEntitlementId:current?.id||null})]);
            await c.query("UPDATE managers SET plan_type=$1,subscription_status='ACTIVE',payment_status='PAID',updated_at=NOW() WHERE id=$2",[planId,managerId]);
            await c.query('COMMIT');
            return res.json({success:true,entitlement:created.rows[0]});
        } catch(e){ try{await c.query('ROLLBACK')}catch(_){} throw e; } finally { c.release(); }
    } catch(e){ return res.status(400).json({success:false,error:'Subscription extension failed'}); }
});

app.delete('/api/v1/super-manager/managers/:id/devices/:deviceId', requireSuperManagerAuth, async (req,res) => {
    const managerId=String(req.params.id||'').trim(), deviceId=String(req.params.deviceId||'').trim();
    if(!managerId||!deviceId) return res.status(400).json({success:false,error:'Manager and device are required'});
    try {
        const q=await pool.query("UPDATE manager_device_bindings SET active=FALSE WHERE manager_id=$1 AND device_id=$2 AND active=TRUE RETURNING manager_id,device_id",[managerId,deviceId]);
        if(!q.rows[0]) return res.status(404).json({success:false,error:'Active device binding not found'});
        return res.json({success:true,managerId,deviceId});
    } catch(e){ return res.status(500).json({success:false,error:'Device unbinding failed'}); }
});

app.post('/api/v1/super-manager/trial-devices/:id/extend', requireSuperManagerAuth, async (req, res) => {
    const { id } = req.params;
    const client = await pool.connect();
    try {
        await client.query('BEGIN');
        const result = await client.query(`SELECT device_id, device_fingerprint, started_at, expires_at, status
                                           FROM trial_devices WHERE device_id = $1 FOR UPDATE`, [id]);
        if (result.rows.length === 0) {
            await client.query('ROLLBACK');
            return res.status(404).json({ success: false, message: 'Trial not found' });
        }
        const trial = result.rows[0];
        const oldExpiry = trial.expires_at ? new Date(trial.expires_at) : new Date();
        const base = Math.max(Date.now(), oldExpiry.getTime());
        const newExpiry = new Date(base + 24 * 60 * 60 * 1000);
        const updated = await client.query(`UPDATE trial_devices
                                            SET expires_at = $1, status = 'ACTIVE'
                                            WHERE device_id = $2
                                            RETURNING started_at, expires_at, status`, [newExpiry, id]);
        await client.query(`INSERT INTO trial_device_audit_log
                            (device_id, device_fingerprint, action, actor_manager_id, previous_started_at, previous_expires_at, new_started_at, new_expires_at)
                            VALUES ($1, $2, 'EXTENDED', $3, $4, $5, $6, $7)`,
                            [trial.device_id, trial.device_fingerprint, req.user?.manager_id || req.user?.id || null,
                             trial.started_at, trial.expires_at, updated.rows[0].started_at, updated.rows[0].expires_at]);
        await client.query('COMMIT');
        res.json({ success: true, trial: updated.rows[0], expiresAt: newExpiry.getTime(), serverTime: Date.now() });
    } catch (e) {
        await client.query('ROLLBACK');
        res.status(500).json({ success: false, error: 'Internal server error' });
    } finally { client.release(); }
});



app.put('/api/v1/super-manager/managers/:id', requireSuperManagerAuth, async (req, res) => {
    const managerId = String(req.params.id || '').trim();
    if (!managerId) return res.status(400).json({ error: 'Manager id required' });
    const name = req.body?.name ?? req.body?.fullName ?? null;
    const gameNetName = req.body?.gameNetName ?? req.body?.gamenet_name ?? null;
    const password = req.body?.password ?? null;
    const planType = req.body?.planType ?? req.body?.plan_name ?? null;
    const status = req.body?.status ?? req.body?.subscriptionStatus ?? null;
    const paymentStatus = req.body?.paymentStatus ?? null;
    try {
        const current = await pool.query("SELECT id, role FROM managers WHERE id = $1 LIMIT 1", [managerId]);
        if (current.rows.length === 0 || current.rows[0].role === 'SUPER_MANAGER') {
            return res.status(404).json({ error: 'Manager not found or protected' });
        }
        const fields = [];
        const values = [];
        let i = 1;
        if (name !== null) { fields.push(`display_name = $${i++}`); values.push(String(name).trim()); }
        if (gameNetName !== null) { fields.push(`gamenet_name = $${i++}`); values.push(String(gameNetName).trim()); }
        if (planType !== null) { fields.push(`plan_type = $${i++}`); values.push(String(planType).trim()); }
        if (status !== null) { fields.push(`subscription_status = $${i++}`); values.push(String(status).trim()); }
        if (paymentStatus !== null) { fields.push(`payment_status = $${i++}`); values.push(String(paymentStatus).trim()); }
        if (password !== null && String(password).trim()) {
            const bcrypt = require('bcrypt');
            fields.push(`password_hash = $${i++}`);
            values.push(await bcrypt.hash(String(password), 10));
        }
        if (!fields.length) return res.status(400).json({ error: 'No editable fields supplied' });
        values.push(managerId);
        const updated = await pool.query(`UPDATE managers SET ${fields.join(', ')}, updated_at = NOW() WHERE id = $${i} AND role <> 'SUPER_MANAGER' RETURNING id, username, role, display_name, gamenet_name, phone, plan_type, subscription_status, payment_status, created_at`, values);
        if (updated.rows.length === 0) return res.status(404).json({ error: 'Manager not found or protected' });
        res.json({ success: true, manager: updated.rows[0] });
    } catch (e) {
        console.error('API internal error:', e);
        res.status(500).json({ error: 'Internal server error' });
    }
});

app.delete('/api/v1/super-manager/managers/:id', requireSuperManagerAuth, async (req, res) => {
    const { id } = req.params;
    if (!id || id === 'mgr_super_admin') {
        return res.status(400).json({ error: 'Super Manager cannot be deleted' });
    }
    try {
        const result = await pool.query("DELETE FROM managers WHERE id = $1 AND role <> 'SUPER_MANAGER' RETURNING id", [id]);
        if (result.rowCount === 0) return res.status(404).json({ error: 'Manager not found or protected' });
        res.json({ success: true, message: 'Deleted' });
    } catch (e) {
        console.error('API internal error:', e); res.status(500).json({ error: 'Internal server error' });
    }
});

app.post('/api/v1/super-manager/managers/delete', requireSuperManagerAuth, async (req, res) => {
    const { id, managerId } = req.body;
    const targetId = id || managerId;
    if (!targetId || targetId === 'mgr_super_admin') {
        return res.status(400).json({ error: 'Super Manager cannot be deleted' });
    }
    try {
        const result = await pool.query("DELETE FROM managers WHERE id = $1 AND role <> 'SUPER_MANAGER' RETURNING id", [targetId]);
        if (result.rowCount === 0) return res.status(404).json({ error: 'Manager not found or protected' });
        res.json({ success: true, message: 'Deleted' });
    } catch (e) {
        console.error('API internal error:', e); res.status(500).json({ error: 'Internal server error' });
    }
});

app.post('/api/v1/super-manager/managers/purge', requireSuperManagerAuth, async (req, res) => {
    const { id, managerId } = req.body;
    const targetId = id || managerId;
    if (!targetId || targetId === 'mgr_super_admin') {
        return res.status(400).json({ error: 'Super Manager cannot be deleted' });
    }
    try {
        const result = await pool.query("DELETE FROM managers WHERE id = $1 AND role <> 'SUPER_MANAGER' RETURNING id", [targetId]);
        if (result.rowCount === 0) return res.status(404).json({ error: 'Manager not found or protected' });
        res.json({ success: true, message: 'Purged' });
    } catch (e) {
        console.error('API internal error:', e); res.status(500).json({ error: 'Internal server error' });
    }
});

// Ping endpoint
const pingHandler = (req, res) => res.json({ success: true, message: 'pong' });
app.get('/api/v1/super-manager/ping', pingHandler);
app.post('/api/v1/super-manager/ping', pingHandler);



/* Canonical Manager configuration: server-owned and versioned per Manager. */
const defaultManagerConfiguration = () => deepMerge(DEFAULT_RESERVATION_CONFIGURATION, {
    version: 1,
    currency: "IRT",
    pricing: {
        consoles: {
            PS4: { "1": 120000, "2": 140000, "3": 150000, "4": 180000 },
            PS5: { "1": 180000, "2": 220000, "3": 250000, "4": 280000 },
            SimD: { "1": 220000, "2": 220000, "3": 220000, "4": 220000 }
        },
        reservations: {
            exclusiveFullDay: 15000000
        },
        products: {}
    },
    policies: {
        reservation: {
            depositPercent: 30
        },
        loyalty: {
            gameSettlement: {
                gnPer10000: 10,
                lpPer10000: 5
            }
        }
    }
});

async function getManagerConfiguration(client, managerId) {
    const result = await client.query(
        "SELECT id,version_number,settings,created_at FROM configuration_revisions WHERE manager_id=$1 ORDER BY version_number DESC LIMIT 1",
        [managerId]
    );
    if (result.rows[0]) {
        result.rows[0].settings = deepMerge(defaultManagerConfiguration(), result.rows[0].settings || {});
        return result.rows[0];
    }
    const defaults = defaultManagerConfiguration();
    const created = await client.query(
        "INSERT INTO configuration_revisions(manager_id,version_number,settings) VALUES($1,1,$2::jsonb) RETURNING id,version_number,settings,created_at",
        [managerId, JSON.stringify(defaults)]
    );
    return created.rows[0];
}

function configuredHourlyRate(configuration, consoleType, controllerCount) {
    const consoles = configuration?.settings?.pricing?.consoles || {};
    const typeKey = String(consoleType || "").trim();
    const rates = consoles[typeKey] || consoles[typeKey.toUpperCase()] || consoles[typeKey === "SimD" ? "SIMD" : typeKey];
    const raw = rates?.[String(controllerCount)];
    const text = String(raw ?? "").trim();
    if (!/^\d+(\.\d+)?$/.test(text) || Number(text) <= 0) return null;
    return text;
}

app.get("/api/v1/manager/configuration", requireManagerAuth, requireActiveEntitlement, async (req,res) => {
    const managerId = sessionManagerId(req);
    try {
        const revision = await getManagerConfiguration(pool, managerId);
        return res.json({success:true,managerId,revisionId:revision.id,version:revision.version_number,settings:revision.settings,createdAt:revision.created_at});
    } catch(e) {
        return res.status(500).json({success:false,error:"Configuration lookup failed"});
    }
});

app.put("/api/v1/manager/configuration", requireManagerAuth, requireActiveEntitlement, async (req,res) => {
    const managerId = sessionManagerId(req);
    const settings = req.body?.settings;
    if (!settings || typeof settings !== "object" || Array.isArray(settings)) return res.status(400).json({success:false,error:"settings object is required"});
    const client = await pool.connect();
    try {
        await client.query("BEGIN");
        const latest = await client.query("SELECT version_number,settings FROM configuration_revisions WHERE manager_id=$1 ORDER BY version_number DESC LIMIT 1 FOR UPDATE", [managerId]);
        const version = Number(latest.rows[0]?.version_number || 0) + 1;
        const mergedSettings = deepMerge(defaultManagerConfiguration(), deepMerge(latest.rows[0]?.settings || {}, settings));
        const inserted = await client.query(
            "INSERT INTO configuration_revisions(manager_id,version_number,settings) VALUES($1,$2,$3::jsonb) RETURNING id,version_number,settings,created_at",
            [managerId,version,JSON.stringify(mergedSettings)]
        );
        await client.query("COMMIT");
        return res.status(201).json({success:true,managerId,revisionId:inserted.rows[0].id,version:inserted.rows[0].version_number,settings:inserted.rows[0].settings,createdAt:inserted.rows[0].created_at});
    } catch(e) {
        try { await client.query("ROLLBACK"); } catch(_) {}
        return res.status(500).json({success:false,error:"Configuration update failed"});
    } finally { client.release(); }
});

/* Canonical event-driven Game Session lifecycle: no per-second API traffic. */
const sessionManagerId = (req) => req.user?.managerId || req.user?.id || null;

function normalizeParticipant(raw, index) {
    const n = Number(raw?.customerId);
    const customerId = Number.isInteger(n) && n > 0 ? n : null;
    const isGuest = customerId === null;
    const participantKey = String(raw?.participantKey || (customerId ? "customer:" + customerId : "guest:" + (index + 1))).trim();
    const participantName = String(raw?.name || raw?.participantName || (customerId ? "مشتری " + customerId : "مهمان " + (index + 1))).trim();
    if (!participantKey || !participantName) throw new Error("Invalid participant");
    return { customerId, isGuest, participantKey, participantName };
}

function sessionEventActiveSeconds(events, startAt, endAt) {
    let activeSeconds = 0;
    let cursor = new Date(startAt);
    let running = true;
    for (const event of events) {
        const t = new Date(event.occurred_at);
        if (t < cursor) continue;
        if (running) activeSeconds += Math.max(0, Math.floor((t.getTime() - cursor.getTime()) / 1000));
        if (event.event_type === "PAUSE") running = false;
        if (event.event_type === "RESUME") running = true;
        cursor = t;
    }
    if (running) activeSeconds += Math.max(0, Math.floor((new Date(endAt).getTime() - cursor.getTime()) / 1000));
    return activeSeconds;
}

app.post("/api/station/start", requireManagerAuth, requireActiveEntitlement, rateLimit({windowMs:60000,max:20}), async (req,res) => {
    const managerId = sessionManagerId(req);
    const stationId = Number(req.body?.stationId);
    const consoleType = String(req.body?.consoleType || "").trim();
    const controllerCount = Number(req.body?.controllerCount);
    const requestedRate = Number(req.body?.hourlyRate);
    const raw = Array.isArray(req.body?.participants) ? req.body.participants : (Array.isArray(req.body?.selectedCustomers) ? req.body.selectedCustomers : []);
    if (!managerId || !Number.isInteger(stationId) || !consoleType || !Number.isInteger(controllerCount) || controllerCount < 1 || controllerCount > 4) {
        return res.status(400).json({success:false,error:"Invalid session start payload"});
    }
    const participants = raw.map(normalizeParticipant);
    const client = await pool.connect();
    try {
        await client.query("BEGIN");
        const startIdempotencyKey = String(req.headers["idempotency-key"] || "").trim();
        if (startIdempotencyKey) {
            const previous = await client.query(
                "SELECT session_id FROM session_events WHERE manager_id=$1 AND event_id=$2 LIMIT 1",
                [managerId, startIdempotencyKey]
            );
            if (previous.rows[0]) {
                const recovered = await client.query(
                    "SELECT id,started_at,pricing_snapshot FROM game_sessions WHERE id=$1 AND manager_id=$2",
                    [previous.rows[0].session_id, managerId]
                );
                await client.query("COMMIT");
                if (recovered.rows[0]) {
                    return res.status(200).json({
                        success:true,
                        duplicate:true,
                        sessionId:recovered.rows[0].id,
                        serverStartedAt:new Date(recovered.rows[0].started_at).getTime(),
                        serverTime:Date.now(),
                        pricingSnapshot:recovered.rows[0].pricing_snapshot
                    });
                }
            }
        }
        const station = await client.query("SELECT id,manager_id,console_type,active FROM stations WHERE id=$1 AND manager_id=$2 FOR UPDATE",[stationId,managerId]);
        if (!station.rows[0] || !station.rows[0].active) {
            await client.query("ROLLBACK");
            return res.status(404).json({success:false,error:"Station not found"});
        }
        const active = await client.query("SELECT id,status,started_at FROM game_sessions WHERE manager_id=$1 AND station_id=$2 AND status IN ('ACTIVE','PAUSED') ORDER BY started_at DESC LIMIT 1 FOR UPDATE",[managerId,stationId]);
        if (active.rows[0]) {
            await client.query("ROLLBACK");
            return res.status(409).json({success:false,code:"ACTIVE_SESSION_EXISTS",sessionId:active.rows[0].id,startedAt:active.rows[0].started_at});
        }
        const ids = participants.filter(p => !p.isGuest).map(p => p.customerId);
        if (ids.length) {
            const conflict = await client.query("SELECT customer_id,session_id FROM active_session_customer_claims WHERE customer_id=ANY($1::int[]) FOR UPDATE",[ids]);
            if (conflict.rows.length) {
                await client.query("ROLLBACK");
                return res.status(409).json({success:false,code:"CUSTOMER_ALREADY_IN_ACTIVE_SESSION",conflicts:conflict.rows});
            }
        }
        const canonicalConsoleType = String(station.rows[0].console_type || consoleType).trim();
        if (canonicalConsoleType && consoleType && canonicalConsoleType.toUpperCase() !== consoleType.toUpperCase()) {
            await client.query("ROLLBACK");
            return res.status(409).json({success:false,code:"STATION_CONSOLE_TYPE_MISMATCH",stationConsoleType:canonicalConsoleType});
        }
        const configuration = await getManagerConfiguration(client, managerId);
        const configuredRate = configuredHourlyRate(configuration, canonicalConsoleType, controllerCount);
        if (!configuredRate) {
            await client.query("ROLLBACK");
            return res.status(422).json({success:false,code:"SERVER_PRICING_NOT_CONFIGURED",consoleType:canonicalConsoleType,controllerCount});
        }
        const now = new Date();
        const pricingSnapshot = {
            source:"MANAGER_CONFIGURATION_REVISION",
            configurationRevisionId:configuration.id,
            configurationVersion:configuration.version_number,
            currency:configuration.settings?.currency || "IRT",
            consoleType:canonicalConsoleType,
            controllerCount,
            hourlyRate:configuredRate,
            capturedAt:now.toISOString()
        };
        const created = await client.query(
            "INSERT INTO game_sessions(manager_id,station_id,status,console_type,controller_count,started_at,pricing_snapshot) VALUES($1,$2,'ACTIVE',$3,$4,$5,$6::jsonb) RETURNING id,manager_id,station_id,status,console_type,controller_count,started_at,pricing_snapshot",
            [managerId,stationId,consoleType,controllerCount,now.toISOString(),JSON.stringify(pricingSnapshot)]
        );
        const session = created.rows[0];
        const eventId = String(req.headers["idempotency-key"] || "start:" + session.id);
        await client.query(
            "INSERT INTO session_events(manager_id,session_id,event_id,event_type,occurred_at,payload,sequence_no) VALUES($1,$2,$3,'START',$4,$5::jsonb,1)",
            [managerId,session.id,eventId,now.toISOString(),JSON.stringify({consoleType,controllerCount})]
        );
        for (const p of participants) {
            await client.query(
                "INSERT INTO session_participants(manager_id,session_id,customer_id,participant_key,participant_name,is_guest,is_payer) VALUES($1,$2,$3,$4,$5,$6,TRUE)",
                [managerId,session.id,p.customerId,p.participantKey,p.participantName,p.isGuest]
            );
            if (!p.isGuest) {
                await client.query("INSERT INTO active_session_customer_claims(customer_id,manager_id,session_id) VALUES($1,$2,$3)",[p.customerId,managerId,session.id]);
            }
        }
        await client.query("COMMIT");
        return res.status(201).json({success:true,sessionId:session.id,managerId,stationId,consoleType,controllerCount,participants,serverStartedAt:now.getTime(),serverTime:now.getTime(),pricingSnapshot});
    } catch(e) {
        try { await client.query("ROLLBACK"); } catch(_) {}
        console.error("Session start error:",e);
        return res.status(500).json({success:false,error:"Session start failed"});
    } finally { client.release(); }
});

app.post("/api/station/offline-start", requireManagerAuth, requireActiveEntitlement, rateLimit({windowMs:60000,max:30}), async (req,res) => {
    const managerId = sessionManagerId(req), sessionId = String(req.body?.sessionId || "").trim();
    const stationId = Number(req.body?.stationId), startTimeMillis = Number(req.body?.startTimeMillis);
    const isUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(sessionId);
    const consoleType = String(req.body?.consoleType || "").trim(), controllerCount = Number(req.body?.controllerCount || 1);
    const participants = Array.isArray(req.body?.participants) ? req.body.participants : [];
    const key = String(req.headers["idempotency-key"] || "").trim();
    if (!managerId || !sessionId || !isUuid || !key || !Number.isInteger(stationId) || stationId <= 0 || !Number.isFinite(startTimeMillis) || !consoleType || controllerCount < 1 || controllerCount > 4) return res.status(400).json({success:false,error:"Invalid offline session start payload"});
    const client = await pool.connect();
    try {
        await client.query("BEGIN");
        const duplicate = await client.query("SELECT id FROM game_sessions WHERE id=$1 AND manager_id=$2 FOR UPDATE",[sessionId,managerId]);
        if (duplicate.rows[0]) { await client.query("COMMIT"); return res.json({success:true,duplicate:true,sessionId}); }
        const station = await client.query("SELECT id,manager_id,console_type,active FROM stations WHERE id=$1 AND manager_id=$2 FOR UPDATE",[stationId,managerId]);
        if (!station.rows[0] || !station.rows[0].active) { await client.query("ROLLBACK"); return res.status(404).json({success:false,error:"Station not found"}); }
        const active = await client.query("SELECT id FROM game_sessions WHERE manager_id=$1 AND station_id=$2 AND status IN ('ACTIVE','PAUSED') FOR UPDATE",[managerId,stationId]);
        if (active.rows[0]) { await client.query("ROLLBACK"); return res.status(409).json({success:false,code:"ACTIVE_SESSION_EXISTS",sessionId:active.rows[0].id}); }
        const configuration = await getManagerConfiguration(client, managerId);
        const canonicalConsoleType = String(station.rows[0].console_type || consoleType);
        const configuredRate = configuredHourlyRate(configuration, canonicalConsoleType, controllerCount);
        if (!configuredRate) { await client.query("ROLLBACK"); return res.status(422).json({success:false,code:"SERVER_PRICING_NOT_CONFIGURED"}); }
        const nowMs = Date.now();
        if (startTimeMillis > nowMs + 5 * 60 * 1000 || startTimeMillis < nowMs - 24 * 60 * 60 * 1000) { await client.query("ROLLBACK"); return res.status(422).json({success:false,code:"OFFLINE_START_OUTSIDE_ALLOWED_WINDOW"}); }
        const startedAt = new Date(startTimeMillis);
        const pricingSnapshot = {source:"MANAGER_CONFIGURATION_REVISION",configurationRevisionId:configuration.id,configurationVersion:configuration.version_number,currency:configuration.settings?.currency || "IRT",consoleType:canonicalConsoleType,controllerCount,hourlyRate:configuredRate,capturedAt:new Date().toISOString(),offlineReconciled:true};
        await client.query("INSERT INTO game_sessions(id,manager_id,station_id,status,console_type,controller_count,started_at,pricing_snapshot) VALUES($1,$2,$3,'ACTIVE',$4,$5,$6,$7::jsonb)",[sessionId,managerId,stationId,canonicalConsoleType,controllerCount,startedAt.toISOString(),JSON.stringify(pricingSnapshot)]);
        await client.query("INSERT INTO session_events(manager_id,session_id,event_id,event_type,occurred_at,payload,sequence_no) VALUES($1,$2,$3,'START',$4,$5::jsonb,1)",[managerId,sessionId,key,startedAt.toISOString(),JSON.stringify({consoleType:canonicalConsoleType,controllerCount,offlineReconciled:true})]);
        for (const part of participants) {
            const cid = Number(part.customerId || 0), isGuest = !(cid > 0);
            const participantKey = String(part.participantKey || (isGuest ? "guest:"+cid : "customer:"+cid));
            const participantName = String(part.name || part.participantName || "");
            if (!isGuest) {
                const c = await client.query("SELECT id FROM customers WHERE id=$1 AND manager_id=$2",[cid,managerId]);
                if (!c.rows[0]) { await client.query("ROLLBACK"); return res.status(404).json({success:false,code:"CUSTOMER_NOT_FOUND"}); }
            }
            await client.query("INSERT INTO session_participants(manager_id,session_id,customer_id,participant_key,participant_name,is_guest,is_payer) VALUES($1,$2,$3,$4,$5,$6,TRUE) ON CONFLICT DO NOTHING",[managerId,sessionId,isGuest?null:cid,participantKey,participantName,isGuest]);
            if (!isGuest) await client.query("INSERT INTO active_session_customer_claims(customer_id,manager_id,session_id) VALUES($1,$2,$3) ON CONFLICT DO NOTHING",[cid,managerId,sessionId]);
        }
        await client.query("COMMIT");
        res.status(201).json({success:true,sessionId,serverStartedAt:startedAt.getTime(),serverTime:Date.now(),pricingSnapshot});
    } catch(e) {
        try { await client.query("ROLLBACK"); } catch(_) {}
        console.error("Offline session reconciliation error:", e);
        res.status(500).json({success:false,error:"Offline session reconciliation failed"});
    } finally { client.release(); }
});

app.post("/api/station/order", requireManagerAuth, requireActiveEntitlement, rateLimit({windowMs:60000,max:60}), async (req,res) => {
    const managerId = sessionManagerId(req), stationId = Number(req.body?.stationId);
    const productName = String(req.body?.productName || "").trim(), quantity = Number(req.body?.quantity || 0);
    const targetCustomerId = Number(req.body?.targetCustomerId || 0);
    const idempotencyKey = String(req.headers["idempotency-key"] || req.body?.idempotencyKey || "").trim();
    if (!managerId || !stationId || !productName || !Number.isInteger(quantity) || quantity <= 0 || !idempotencyKey) return res.status(400).json({success:false,error:"Invalid order payload"});
    const client = await pool.connect();
    try {
        await client.query("BEGIN");
        const session = await client.query("SELECT id FROM game_sessions WHERE manager_id=$1 AND station_id=$2 AND status IN ('ACTIVE','PAUSED') ORDER BY started_at DESC LIMIT 1 FOR UPDATE",[managerId,stationId]);
        if (!session.rows[0]) { await client.query("ROLLBACK"); return res.status(404).json({success:false,error:"Active session not found"}); }
        const sid=session.rows[0].id;
        const duplicate=await client.query("SELECT id,session_id FROM session_events WHERE manager_id=$1 AND event_id=$2 LIMIT 1",[managerId,idempotencyKey]);
        if(duplicate.rows[0]){
            if(String(duplicate.rows[0].session_id) !== String(sid)){
                await client.query("ROLLBACK");
                return res.status(409).json({success:false,code:"IDEMPOTENCY_KEY_REUSED_FOR_DIFFERENT_SESSION"});
            }
            await client.query("COMMIT");
            return res.json({success:true,duplicate:true,sessionId:sid});
        }
        const cfg=await getManagerConfiguration(client,managerId);
        const product=cfg.settings?.pricing?.products?.[productName] ?? cfg.settings?.products?.[productName];
        const unitPrice=Number(product?.price ?? product ?? 0);
        if(!Number.isFinite(unitPrice) || unitPrice < 0) { await client.query("ROLLBACK"); return res.status(422).json({success:false,error:"Product pricing not configured"}); }
        const unitPriceWhole = Math.trunc(unitPrice);
        if(targetCustomerId){
            const target=await client.query("SELECT id FROM session_participants WHERE session_id=$1 AND manager_id=$2 AND customer_id=$3",[sid,managerId,targetCustomerId]);
            if(!target.rows[0]){
                await client.query("ROLLBACK");
                return res.status(409).json({success:false,code:"ORDER_CUSTOMER_NOT_IN_SESSION"});
            }
        }
        const lineTotal=unitPriceWhole*quantity;
        await client.query("INSERT INTO session_orders(manager_id,session_id,product_name,quantity,unit_price,target_customer_id,line_total,product_snapshot) VALUES($1,$2,$3,$4,$5,$6,$7,$8::jsonb)",[managerId,sid,productName,quantity,unitPriceWhole,targetCustomerId||null,lineTotal,JSON.stringify({productName,unitPrice:unitPriceWhole,capturedAt:new Date().toISOString()})]);
        const last=await client.query("SELECT COALESCE(MAX(sequence_no),0)+1 seq FROM session_events WHERE session_id=$1 AND manager_id=$2",[sid,managerId]);
        await client.query("INSERT INTO session_events(manager_id,session_id,event_id,event_type,occurred_at,payload,sequence_no) VALUES($1,$2,$3,'ORDER',NOW(),$4::jsonb,$5)",[managerId,sid,idempotencyKey,JSON.stringify({productName,quantity,unitPrice,targetCustomerId:targetCustomerId||null}),Number(last.rows[0].seq)]);
        await client.query("COMMIT");
        res.status(201).json({success:true,sessionId:sid,unitPrice:unitPriceWhole,lineTotal});
    } catch(e){try{await client.query("ROLLBACK")}catch(_){}res.status(500).json({success:false,error:"Order creation failed"});} finally{client.release();}
});


app.post("/api/station/event", requireManagerAuth, requireActiveEntitlement, rateLimit({windowMs:60000,max:60}), async (req,res) => {
    const managerId = sessionManagerId(req);
    const sessionId = String(req.body?.sessionId || "");
    const eventId = String(req.body?.eventId || "").trim();
    const eventType = String(req.body?.eventType || "").trim().toUpperCase();
    const occurredAtMs = Number(req.body?.occurredAt);
    const allowed = new Set(["PAUSE","RESUME","ADD_PARTICIPANT","REMOVE_PARTICIPANT","ORDER"]);
    if (!managerId || !sessionId || !eventId || !allowed.has(eventType) || !Number.isFinite(occurredAtMs)) return res.status(400).json({success:false,error:"Invalid session event payload"});
    const client = await pool.connect();
    try {
        await client.query("BEGIN");
        const duplicate = await client.query("SELECT sequence_no,session_id FROM session_events WHERE manager_id=$1 AND event_id=$2 LIMIT 1",[managerId,eventId]);
        if (duplicate.rows[0]) {
            if(String(duplicate.rows[0].session_id) !== String(sessionId)){
                await client.query("ROLLBACK");
                return res.status(409).json({success:false,code:"IDEMPOTENCY_KEY_REUSED_FOR_DIFFERENT_SESSION"});
            }
            await client.query("COMMIT");
            return res.json({success:true,duplicate:true,sessionId,sequenceNo:duplicate.rows[0].sequence_no});
        }
        const session = await client.query("SELECT id,status,started_at FROM game_sessions WHERE id=$1 AND manager_id=$2 FOR UPDATE",[sessionId,managerId]);
        if (!session.rows[0] || !["ACTIVE","PAUSED"].includes(session.rows[0].status)) {
            await client.query("ROLLBACK");
            return res.status(404).json({success:false,error:"Active session not found"});
        }
        const occurredAt = new Date(occurredAtMs);
        if (occurredAtMs < new Date(session.rows[0].started_at).getTime() || occurredAtMs > Date.now()+300000) {
            await client.query("ROLLBACK");
            return res.status(422).json({success:false,error:"Event timestamp outside session window"});
        }
        const last = await client.query("SELECT occurred_at,sequence_no FROM session_events WHERE session_id=$1 AND manager_id=$2 ORDER BY sequence_no DESC LIMIT 1",[sessionId,managerId]);
        const sequenceNo = last.rows.length ? Number(last.rows[0].sequence_no)+1 : 1;
        if (last.rows.length && occurredAt < new Date(last.rows[0].occurred_at)) {
            await client.query("ROLLBACK");
            return res.status(409).json({success:false,code:"EVENT_ORDER_CONFLICT"});
        }
        if (eventType === "PAUSE" && session.rows[0].status !== "ACTIVE") { await client.query("ROLLBACK"); return res.status(409).json({success:false,code:"SESSION_NOT_RUNNING"}); }
        if (eventType === "RESUME" && session.rows[0].status !== "PAUSED") { await client.query("ROLLBACK"); return res.status(409).json({success:false,code:"SESSION_NOT_PAUSED"}); }
        const payload = req.body?.payload && typeof req.body.payload === "object" ? req.body.payload : {};
        if (eventType === "ADD_PARTICIPANT") {
            const participant = normalizeParticipant(payload, 0);
            if (participant.customerId) {
                const customer = await client.query("SELECT id FROM customers WHERE id=$1 AND manager_id=$2", [participant.customerId, managerId]);
                if (!customer.rows[0]) { await client.query("ROLLBACK"); return res.status(404).json({success:false,code:"CUSTOMER_NOT_FOUND"}); }
                const claim = await client.query("SELECT session_id FROM active_session_customer_claims WHERE customer_id=$1 AND manager_id=$2 FOR UPDATE", [participant.customerId, managerId]);
                if (claim.rows.length && claim.rows[0].session_id !== sessionId) { await client.query("ROLLBACK"); return res.status(409).json({success:false,code:"CUSTOMER_ALREADY_IN_ACTIVE_SESSION"}); }
                if (!claim.rows.length) await client.query("INSERT INTO active_session_customer_claims(customer_id,manager_id,session_id) VALUES($1,$2,$3)", [participant.customerId, managerId, sessionId]);
            }
            const exists = await client.query("SELECT id FROM session_participants WHERE session_id=$1 AND manager_id=$2 AND participant_key=$3 LIMIT 1", [sessionId, managerId, participant.participantKey]);
            if (!exists.rows.length) await client.query("INSERT INTO session_participants(manager_id,session_id,customer_id,participant_key,participant_name,is_guest,is_payer) VALUES($1,$2,$3,$4,$5,$6,TRUE)", [managerId,sessionId,participant.customerId,participant.participantKey,participant.participantName,participant.isGuest]);
        } else if (eventType === "REMOVE_PARTICIPANT") {
            const participantKey = String(payload.participantKey || "").trim();
            if (!participantKey) { await client.query("ROLLBACK"); return res.status(400).json({success:false,error:"participantKey is required"}); }
            const participant = await client.query("SELECT customer_id FROM session_participants WHERE session_id=$1 AND manager_id=$2 AND participant_key=$3 FOR UPDATE", [sessionId,managerId,participantKey]);
            if (!participant.rows[0]) { await client.query("ROLLBACK"); return res.status(404).json({success:false,code:"PARTICIPANT_NOT_FOUND"}); }
            await client.query("DELETE FROM session_participants WHERE session_id=$1 AND manager_id=$2 AND participant_key=$3", [sessionId,managerId,participantKey]);
            if (participant.rows[0].customer_id) await client.query("DELETE FROM active_session_customer_claims WHERE session_id=$1 AND manager_id=$2 AND customer_id=$3", [sessionId,managerId,participant.rows[0].customer_id]);
        } else if (eventType === "ORDER") {
            const productName = String(payload.productName || "").trim();
            const quantity = Number(payload.quantity);
            const targetCustomerId = payload.targetCustomerId == null ? null : Number(payload.targetCustomerId);
            if (!productName || !Number.isInteger(quantity) || quantity <= 0 || quantity > 1000) { await client.query("ROLLBACK"); return res.status(400).json({success:false,error:"Invalid order payload"}); }
            if (targetCustomerId != null) {
                const target = await client.query("SELECT 1 FROM session_participants WHERE session_id=$1 AND manager_id=$2 AND customer_id=$3", [sessionId,managerId,targetCustomerId]);
                if (!target.rows.length) { await client.query("ROLLBACK"); return res.status(409).json({success:false,code:"ORDER_CUSTOMER_NOT_IN_SESSION"}); }
            }
            const cfg=await getManagerConfiguration(client,managerId);
            const product=cfg.settings?.pricing?.products?.[productName] ?? cfg.settings?.products?.[productName];
            const unitPriceWhole=normalizeMoneyInteger(product?.price ?? product ?? "");
            if (unitPriceWhole === null) { await client.query("ROLLBACK"); return res.status(422).json({success:false,error:"Product pricing not configured"}); }
            await client.query("INSERT INTO session_orders(manager_id,session_id,product_name,quantity,unit_price,target_customer_id,line_total,product_snapshot) VALUES($1,$2,$3,$4,$5::numeric,$6,FLOOR($4::numeric*$5::numeric),$7::jsonb)", [managerId,sessionId,productName,quantity,unitPriceWhole,targetCustomerId,JSON.stringify({name:productName,unitPrice:unitPriceWhole,capturedAt:new Date().toISOString()})]);
        }
        await client.query(
            "INSERT INTO session_events(manager_id,session_id,event_id,event_type,occurred_at,payload,sequence_no) VALUES($1,$2,$3,$4,$5,$6::jsonb,$7)",
            [managerId,sessionId,eventId,eventType,occurredAt.toISOString(),JSON.stringify(payload),sequenceNo]
        );
        if (eventType === "PAUSE" || eventType === "RESUME") {
            await client.query("UPDATE game_sessions SET status=$1::varchar,paused_at=CASE WHEN $1::varchar='PAUSED' THEN $2 ELSE paused_at END,updated_at=NOW() WHERE id=$3 AND manager_id=$4",[eventType==="PAUSE"?"PAUSED":"ACTIVE",occurredAt.toISOString(),sessionId,managerId]);
        }
        await client.query("COMMIT");
        return res.json({success:true,sessionId,sequenceNo,serverTime:Date.now()});
    } catch(e) {
        try { await client.query("ROLLBACK"); } catch(_) {}
        console.error("Session event error:",e);
        return res.status(500).json({success:false,error:"Session event failed"});
    } finally { client.release(); }
});

app.post("/api/station/invoice/pay", requireManagerAuth, requireActiveEntitlement, rateLimit({windowMs:60000,max:30}), async (req,res) => {
    const managerId = sessionManagerId(req);
    const invoiceId = String(req.body?.invoiceId || "");
    const idempotencyKey = String(req.body?.idempotencyKey || req.get("Idempotency-Key") || "").trim();
    const requestedAmountText = String(req.body?.amount ?? "").trim();
    if (!managerId || !invoiceId || !idempotencyKey || !/^[0-9]+([.][0-9]+)?$/.test(requestedAmountText) || Number(requestedAmountText) <= 0) return res.status(400).json({success:false,error:"Invalid payment payload"});
    const client = await pool.connect();
    try {
        await client.query("BEGIN");
        const invoiceResult = await client.query("SELECT * FROM invoices WHERE id=$1 AND manager_id=$2 FOR UPDATE", [invoiceId,managerId]);
        const invoice = invoiceResult.rows[0];
        if (!invoice) { await client.query("ROLLBACK"); return res.status(404).json({success:false,error:"Invoice not found"}); }
        const duplicate = await client.query("SELECT id,amount,status,invoice_id,manager_id FROM payment_transactions WHERE idempotency_key=$1 LIMIT 1", [idempotencyKey]);
        if (duplicate.rows[0]) {
            if (String(duplicate.rows[0].manager_id) !== String(managerId) || String(duplicate.rows[0].invoice_id) !== String(invoiceId)) {
                await client.query("ROLLBACK");
                return res.status(409).json({success:false,code:"IDEMPOTENCY_KEY_REUSED_FOR_DIFFERENT_INVOICE"});
            }
            await client.query("COMMIT");
            return res.json({success:true,duplicate:true,payment:duplicate.rows[0],invoiceId});
        }
        const remainingResult = await client.query("SELECT (total_amount-paid_amount) AS remaining FROM invoices WHERE id=$1 AND manager_id=$2", [invoiceId,managerId]);
        const remainingText = String(remainingResult.rows[0]?.remaining || "0");
        const amountCheck = await client.query("SELECT ($1::numeric > 0::numeric AND $1::numeric <= $2::numeric) AS valid", [requestedAmountText,remainingText]);
        if (!amountCheck.rows[0].valid) { await client.query("ROLLBACK"); return res.status(422).json({success:false,code:"PAYMENT_EXCEEDS_REMAINING",remaining:remainingText}); }
        const payment = await client.query(
            "INSERT INTO payment_transactions(manager_id,customer_id,invoice_id,amount,status,idempotency_key,provider,currency,verified_at) VALUES($1,$2,$3,$4::numeric,'SUCCESS',$5,'MANAGER_CONFIRMATION',$6,NOW()) RETURNING id,amount,status",
            [managerId,invoice.customer_id,invoiceId,requestedAmountText,idempotencyKey,invoice.currency || "IRT"]
        );
        const updated = await client.query(
            "UPDATE invoices SET paid_amount=paid_amount+$1::numeric,status=CASE WHEN paid_amount+$1::numeric >= total_amount THEN 'PAID' ELSE 'PARTIALLY_PAID' END,updated_at=NOW() WHERE id=$2 AND manager_id=$3 RETURNING paid_amount,total_amount,status",
            [requestedAmountText,invoiceId,managerId]
        );
        await client.query("INSERT INTO financial_audit_logs(manager_id,customer_id,invoice_id,payment_id,event_type,amount,currency,actor,idempotency_key,metadata) VALUES($1,$2,$3,$4,'INVOICE_PAYMENT',$5,$6,'MANAGER',$7,$8::jsonb)", [managerId,invoice.customer_id,invoiceId,payment.rows[0].id,requestedAmountText,invoice.currency || "IRT",idempotencyKey,JSON.stringify({invoiceStatus:updated.rows[0].status,paidAmount:String(updated.rows[0].paid_amount),totalAmount:String(updated.rows[0].total_amount),provider:"MANAGER_CONFIRMATION"})]);
        const config = await getManagerConfiguration(client, managerId);
        const policy = config.settings?.policies?.loyalty?.gameSettlement || {};
        const gnPer10000 = String(policy.gnPer10000 ?? "0").trim();
        const lpPer10000 = String(policy.lpPer10000 ?? "0").trim();
        const nonNegativeRate = /^(?:0|[1-9][0-9]*)(?:[.][0-9]+)?$/;
        if (!nonNegativeRate.test(gnPer10000) || !nonNegativeRate.test(lpPer10000)) {
            await client.query("ROLLBACK");
            return res.status(422).json({success:false,code:"LOYALTY_POLICY_INVALID"});
        }
        const loyaltyConfigured = Number(gnPer10000) > 0 || Number(lpPer10000) > 0;
        if (updated.rows[0].status === 'PAID' && loyaltyConfigured) {
            const paidGameEligible = await client.query("SELECT game_cost FROM invoices WHERE id=$1 AND manager_id=$2", [invoiceId,managerId]);
            const eligibleText = String(paidGameEligible.rows[0]?.game_cost || "0");
            const loyaltyAmounts = await client.query("SELECT FLOOR(($1::numeric / 10000::numeric) * $2::numeric)::bigint AS gn_amount, FLOOR(($1::numeric / 10000::numeric) * $3::numeric)::bigint AS lp_amount", [eligibleText,gnPer10000,lpPer10000]);
            const gnAmount = Number(loyaltyAmounts.rows[0].gn_amount || 0);
            const lpAmount = Number(loyaltyAmounts.rows[0].lp_amount || 0);
            if (invoice.customer_id == null) {
                if (gnAmount > 0 || lpAmount > 0) { await client.query("ROLLBACK"); return res.status(422).json({success:false,code:"LOYALTY_REQUIRES_REGISTERED_CUSTOMER"}); }
            }
            if (gnAmount > 0) {
                const ins = await client.query("INSERT INTO gn_ledger(manager_id,customer_id,amount,type,reference_type,reference_id,idempotency_key) VALUES($1,$2,$3,'CREDIT','SESSION_PAYMENT',$4,$5) ON CONFLICT(idempotency_key) DO NOTHING RETURNING id", [managerId,invoice.customer_id,gnAmount,invoiceId,"invoice_gn_"+invoiceId]);
                if (ins.rowCount) await client.query("UPDATE customers SET gn_balance=gn_balance+$1,updated_at=NOW() WHERE id=$2 AND manager_id=$3", [gnAmount,invoice.customer_id,managerId]);
            }
            if (lpAmount > 0) {
                const ins = await client.query("INSERT INTO lp_ledger(manager_id,customer_id,amount,type,reference_type,reference_id,idempotency_key) VALUES($1,$2,$3,'CREDIT','SESSION_PAYMENT',$4,$5) ON CONFLICT(idempotency_key) DO NOTHING RETURNING id", [managerId,invoice.customer_id,lpAmount,invoiceId,"invoice_lp_"+invoiceId]);
                if (ins.rowCount) await client.query("UPDATE customers SET lp_balance=lp_balance+$1,updated_at=NOW() WHERE id=$2 AND manager_id=$3", [lpAmount,invoice.customer_id,managerId]);
            }
            if (gnAmount > 0 || lpAmount > 0) {
                await client.query(
                    "INSERT INTO financial_audit_logs(manager_id,customer_id,invoice_id,event_type,amount,currency,actor,idempotency_key,metadata) VALUES($1,$2,$3,'INVOICE_LOYALTY_SETTLEMENT',$4,$5,'SYSTEM',$6,$7::jsonb)",
                    [managerId,invoice.customer_id,invoiceId,String(gnAmount),invoice.currency || "IRT","invoice_loyalty_"+invoiceId,JSON.stringify({gnAmount,lpAmount,gameCost:eligibleText,gnRatePer10000:gnPer10000,lpRatePer10000:lpPer10000})]
                );
            }
        }
        await client.query("COMMIT");
        return res.json({success:true,payment:payment.rows[0],invoice:{id:invoiceId,paidAmount:updated.rows[0].paid_amount,totalAmount:updated.rows[0].total_amount,status:updated.rows[0].status}});
    } catch(e) {
        try { await client.query("ROLLBACK"); } catch(_) {}
        console.error("Invoice payment error:",e);
        return res.status(500).json({success:false,error:"Invoice payment failed"});
    } finally { client.release(); }
});

app.get("/api/station/active", requireManagerAuth, requireActiveEntitlement, async (req,res) => {
    const managerId = sessionManagerId(req);
    const stationId = Number(req.query.stationId);
    if (!Number.isInteger(stationId)) return res.status(400).json({success:false,error:"Invalid stationId"});
    try {
        const result = await pool.query(
            "SELECT gs.id,gs.manager_id,gs.station_id,gs.status,gs.console_type,gs.controller_count,gs.started_at,gs.paused_at,gs.pricing_snapshot,COALESCE(json_agg(json_build_object('customerId',sp.customer_id,'participantKey',sp.participant_key,'participantName',sp.participant_name,'isGuest',sp.is_guest) ORDER BY sp.id) FILTER (WHERE sp.id IS NOT NULL),'[]'::json) participants FROM game_sessions gs LEFT JOIN session_participants sp ON sp.session_id=gs.id AND sp.manager_id=gs.manager_id WHERE gs.manager_id=$1 AND gs.station_id=$2 AND gs.status IN ('ACTIVE','PAUSED') GROUP BY gs.id ORDER BY gs.started_at DESC LIMIT 1",
            [managerId,stationId]
        );
        if (!result.rows[0]) return res.status(404).json({success:false,code:"NO_ACTIVE_SESSION"});
        return res.json({success:true,session:result.rows[0],serverTime:Date.now()});
    } catch(e) { return res.status(500).json({success:false,error:"Active session lookup failed"}); }
});

app.get("/api/customer/live-session", requireCustomerAuth, async (req,res) => {
    const customerId = Number(req.user?.id);
    const managerId = req.user?.managerId;
    if (!Number.isInteger(customerId) || !managerId) return res.status(401).json({success:false,error:"Customer identity missing"});
    try {
        const result = await pool.query(
            "SELECT gs.id,gs.station_id,gs.status,gs.console_type,gs.controller_count,gs.started_at,gs.paused_at,gs.pricing_snapshot,COALESCE(json_agg(json_build_object('customerId',sp.customer_id,'participantKey',sp.participant_key,'participantName',sp.participant_name,'isGuest',sp.is_guest) ORDER BY sp.id) FILTER (WHERE sp.id IS NOT NULL),'[]'::json) participants FROM game_sessions gs JOIN session_participants mine ON mine.session_id=gs.id AND mine.manager_id=gs.manager_id AND mine.customer_id=$1 LEFT JOIN session_participants sp ON sp.session_id=gs.id AND sp.manager_id=gs.manager_id WHERE gs.manager_id=$2 AND gs.status IN ('ACTIVE','PAUSED') GROUP BY gs.id ORDER BY gs.started_at DESC LIMIT 1",
            [customerId,managerId]
        );
        if (!result.rows[0]) return res.status(404).json({success:false,code:"NO_ACTIVE_SESSION"});
        return res.json({success:true,session:result.rows[0],serverTime:Date.now(),timezone:"Asia/Tehran"});
    } catch(e) {
        return res.status(500).json({success:false,error:"Live session lookup failed"});
    }
});

app.post("/api/station/settle", requireManagerAuth, requireActiveEntitlement, rateLimit({windowMs:60000,max:20}), async (req,res) => {
    const managerId = sessionManagerId(req);
    const sessionId = String(req.body?.sessionId || "");
    const endedAtMs = Number(req.body?.endedAt || req.body?.closedAtMillis || Date.now());
    if (!managerId || !sessionId || !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(sessionId) || !Number.isFinite(endedAtMs)) return res.status(400).json({success:false,error:"Invalid settlement payload"});
    const client = await pool.connect();
    try {
        await client.query("BEGIN");
        const sessionResult = await client.query("SELECT * FROM game_sessions WHERE id=$1 AND manager_id=$2 FOR UPDATE",[sessionId,managerId]);
        const session = sessionResult.rows[0];
        if (!session) { await client.query("ROLLBACK"); return res.status(404).json({success:false,error:"Session not found"}); }
        if (["SETTLED","CLOSED","CANCELLED"].includes(session.status)) {
            const invoices = await client.query("SELECT id,invoice_number,customer_id,total_amount,paid_amount,status FROM invoices WHERE session_id=$1 AND manager_id=$2 ORDER BY id", [sessionId,managerId]);
            await client.query("COMMIT");
            return res.json({success:true,duplicate:true,sessionId,serverTime:Date.now(),durationSeconds:Number(session.duration_seconds || 0),gameCost:String(session.game_cost || "0"),buffetCost:String(session.buffet_cost || "0"),totalCost:String(session.total_cost || "0"),invoices:invoices.rows});
        }
        const endedAt = new Date(Math.min(endedAtMs,Date.now()));
        if (endedAt.getTime() < new Date(session.started_at).getTime()) { await client.query("ROLLBACK"); return res.status(422).json({success:false,error:"Invalid end time"}); }
        const events = await client.query("SELECT event_type,occurred_at,sequence_no,payload FROM session_events WHERE session_id=$1 AND manager_id=$2 ORDER BY sequence_no ASC",[sessionId,managerId]);
        const activeSeconds = sessionEventActiveSeconds(events.rows.filter(e => e.event_type !== "START"),session.started_at,endedAt.toISOString());
        const pricing = session.pricing_snapshot || {};
        const pricingRateText = String(pricing.hourlyRate ?? "");
        if (!/^\d+(\\.\d+)?$/.test(pricingRateText) || Number(pricingRateText) <= 0) {
            await client.query("ROLLBACK");
            return res.status(422).json({success:false,code:"SERVER_PRICING_SNAPSHOT_MISSING"});
        }
        // PostgreSQL NUMERIC keeps the financial calculation exact; do not use JS floating point.
        const financial = await client.query(
            "SELECT FLOOR(($1::numeric * $2::numeric) / 3600::numeric) AS game_cost, FLOOR(COALESCE((SELECT SUM(line_total) FROM session_orders WHERE session_id=$3 AND manager_id=$4),0)::numeric) AS buffet_cost",
            [pricingRateText, activeSeconds, sessionId, managerId]
        );
        const gameCost = financial.rows[0].game_cost;
        const buffetCost = financial.rows[0].buffet_cost;
        const totalResult = await client.query(
            "SELECT ($1::numeric + $2::numeric) AS total_cost",
            [gameCost, buffetCost]
        );
        const totalCost = totalResult.rows[0].total_cost;
        await client.query("UPDATE game_sessions SET status='SETTLED',ended_at=$1,duration_seconds=$2::bigint,duration_minutes=FLOOR($2::numeric/60)::integer,game_cost=$3,buffet_cost=$4,total_cost=$5,updated_at=NOW() WHERE id=$6 AND manager_id=$7",[endedAt.toISOString(),activeSeconds,gameCost,buffetCost,totalCost,sessionId,managerId]);
        const participants = await client.query("SELECT customer_id,participant_key,participant_name,is_guest,is_payer FROM session_participants WHERE session_id=$1 AND manager_id=$2 ORDER BY id",[sessionId,managerId]);
        const payers = participants.rows.filter(p => p.is_payer);
        if (payers.length) {
            const shares = await client.query(
                "SELECT FLOOR($1::numeric / $2::numeric) AS game_share_base, MOD($1::numeric, $2::numeric) AS game_remainder, FLOOR($3::numeric / $2::numeric) AS buffet_share_base, MOD($3::numeric, $2::numeric) AS buffet_remainder",
                [gameCost, payers.length, buffetCost]
            );
            const shareData = shares.rows[0];
            const baseGame = BigInt(String(shareData.game_share_base || '0'));
            const gameRemainder = BigInt(String(shareData.game_remainder || '0'));
            const baseBuffet = BigInt(String(shareData.buffet_share_base || '0'));
            const buffetRemainder = BigInt(String(shareData.buffet_remainder || '0'));
            const managerConfig = await getManagerConfiguration(client,managerId);
            let clubLevels = managerConfig.settings?.club_levels || [];
            if (typeof clubLevels === 'string') {
                try { clubLevels = JSON.parse(clubLevels); } catch (_) { clubLevels = []; }
            }
            if (!Array.isArray(clubLevels)) clubLevels = [];
            for (let payerIndex = 0; payerIndex < payers.length; payerIndex += 1) {
                const payer = payers[payerIndex];
                if (!payer.customer_id) continue;
                // Every invoice amount is an integer Toman. Any indivisible remainder
                // is assigned to the final payer so the invoice totals still reconcile.
                const shareGame = (baseGame + (payerIndex === payers.length - 1 ? gameRemainder : 0n)).toString();
                const shareBuffet = (baseBuffet + (payerIndex === payers.length - 1 ? buffetRemainder : 0n)).toString();
                const customerRow = await client.query("SELECT club_tier FROM customers WHERE id=$1 AND manager_id=$2",[payer.customer_id,managerId]);
                const tier = String(customerRow.rows[0]?.club_tier || "BRONZE").toLowerCase();
                const level = clubLevels.find(x => String(x?.id || "").toLowerCase() === tier) || {};
                const gameDiscountPercent = Math.max(0,Math.min(100,Number(level.gameDiscountPercent || 0)));
                const buffetDiscountPercent = Math.max(0,Math.min(100,Number(level.buffetDiscountPercent || 0)));
                const fixedDiscountToman = Math.max(0,Math.trunc(Number(level.fixedDiscountToman || 0)));
                const discounted = await client.query(
                    "SELECT FLOOR($1::numeric*(100-$3::numeric)/100::numeric) AS game_cost,FLOOR($2::numeric*(100-$4::numeric)/100::numeric) AS buffet_cost",
                    [shareGame,shareBuffet,gameDiscountPercent,buffetDiscountPercent]
                );
                let invoiceGameCost = BigInt(String(discounted.rows[0].game_cost || "0"));
                let invoiceBuffetCost = BigInt(String(discounted.rows[0].buffet_cost || "0"));
                const beforeFixed = invoiceGameCost + invoiceBuffetCost;
                const fixedDiscount = BigInt(String(fixedDiscountToman));
                if (fixedDiscount > 0n) {
                    let remainingFixed = fixedDiscount > beforeFixed ? beforeFixed : fixedDiscount;
                    const buffetDeduction = remainingFixed > invoiceBuffetCost ? invoiceBuffetCost : remainingFixed;
                    invoiceBuffetCost -= buffetDeduction;
                    remainingFixed -= buffetDeduction;
                    if (remainingFixed > 0n) invoiceGameCost = invoiceGameCost > remainingFixed ? invoiceGameCost - remainingFixed : 0n;
                }
                const invoiceNumber = "GN-" + new Date().toISOString().replace(/[-:TZ.]/g,"").slice(0,14) + "-" + payer.customer_id + "-" + sessionId.slice(0,8);
                const invoicePricingSnapshot = {...pricing,clubTier:tier,gameDiscountPercent,buffetDiscountPercent,fixedDiscountToman};
                await client.query(
                    "INSERT INTO invoices(invoice_number,manager_id,customer_id,session_id,station_id,status,currency,game_cost,buffet_cost,total_amount,paid_amount,settlement_idempotency_key,pricing_snapshot,customer_snapshot,manager_snapshot) VALUES($1,$2,$3,$4,$5,'UNPAID','IRT',$6,$7,($6::numeric+$7::numeric),0,$8,$9::jsonb,$10::jsonb,$11::jsonb) ON CONFLICT(session_id,customer_id) DO UPDATE SET game_cost=EXCLUDED.game_cost,buffet_cost=EXCLUDED.buffet_cost,total_amount=EXCLUDED.total_amount,settlement_idempotency_key=COALESCE(invoices.settlement_idempotency_key,EXCLUDED.settlement_idempotency_key),pricing_snapshot=EXCLUDED.pricing_snapshot,updated_at=NOW()",
                    [invoiceNumber,managerId,payer.customer_id,sessionId,session.station_id,invoiceGameCost.toString(),invoiceBuffetCost.toString(),"settle_"+sessionId+"_"+payer.customer_id,JSON.stringify(invoicePricingSnapshot),JSON.stringify({id:payer.customer_id,name:payer.participant_name}),JSON.stringify({id:managerId})]
                );
            }
        }
        await client.query("DELETE FROM active_session_customer_claims WHERE session_id=$1 AND manager_id=$2", [sessionId,managerId]);
        await client.query("COMMIT");
        const settledInvoices = await client.query("SELECT id,invoice_number,customer_id,total_amount,paid_amount,status FROM invoices WHERE session_id=$1 AND manager_id=$2 ORDER BY id", [sessionId,managerId]);
        return res.json({success:true,sessionId,serverTime:Date.now(),durationSeconds:activeSeconds,gameCost,buffetCost,totalCost,invoices:settledInvoices.rows});
    } catch(e) {
        try { await client.query("ROLLBACK"); } catch(_) {}
        console.error("Session settlement error:",e);
        return res.status(500).json({success:false,error:"Session settlement failed"});
    } finally { client.release(); }
});

// Canonical Android contract routes are registered before the server starts listening.
const registerCanonicalRoutes = require('./canonical_routes');
registerCanonicalRoutes({ app, pool, requireManagerAuth, requireActiveEntitlement, requireCustomerAuth, requireSuperManagerAuth, rateLimit });

const PORT = process.env.PORT || 3000;
const expireDueReservationPayments = async () => {
    const client = await pool.connect();
    try {
        await client.query('BEGIN');
        const expired = await client.query(`
            WITH due AS (
                SELECT id, manager_id, status AS old_status
                FROM reservations
                WHERE status IN ('PAYMENT_PENDING','VIP_PENDING_PAYMENT')
                  AND (
                    CASE
                      WHEN status='VIP_PENDING_PAYMENT'
                        THEN COALESCE((snap_vip_policy->>'vipPaymentDeadlineMinutes')::numeric,0)
                      ELSE COALESCE((snap_vip_policy->>'paymentDeadlineMinutes')::numeric,0)
                    END
                  ) > 0
                  AND created_at + (
                    CASE
                      WHEN status='VIP_PENDING_PAYMENT'
                        THEN make_interval(mins => COALESCE((snap_vip_policy->>'vipPaymentDeadlineMinutes')::int,0))
                      ELSE make_interval(mins => COALESCE((snap_vip_policy->>'paymentDeadlineMinutes')::int,0))
                    END
                  ) < NOW()
                FOR UPDATE
            )
            UPDATE reservations r
            SET status='EXPIRED', updated_at=NOW()
            FROM due
            WHERE r.id=due.id
            RETURNING r.id,r.manager_id,due.old_status
        `);
        for (const row of expired.rows) {
            await client.query(
                'INSERT INTO reservation_audit_logs(manager_id,reservation_id,actor,old_status,new_status,reason) VALUES($1,$2,$3,$4,$5,$6)',
                [row.manager_id,row.id,'SYSTEM',row.old_status,'EXPIRED','Reservation payment deadline expired']
            );
        }
        await client.query('COMMIT');
    } catch (e) {
        try { await client.query('ROLLBACK'); } catch (_) {}
        console.error('Reservation payment expiry check failed:', e.message);
    } finally {
        client.release();
    }
};

setInterval(expireDueReservationPayments, 30000);
expireDueReservationPayments()
    .then(() => app.listen(PORT, '0.0.0.0', () => console.log(`API running on port ${PORT}`)))
    .catch((e) => { console.error('Reservation expiry initialization failed:', e); process.exit(1); });

