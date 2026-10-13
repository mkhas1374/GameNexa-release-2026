const fs = require('fs');
const assert = require('assert');

const server = fs.readFileSync(__dirname + '/server.js', 'utf8');
const canonical = fs.readFileSync(__dirname + '/canonical_routes.js', 'utf8');
const financial = fs.readFileSync(__dirname + '/financialService.js', 'utf8');
const reservation = fs.readFileSync(__dirname + '/reservationService.js', 'utf8');

assert(canonical.includes("req.user.managerId"), 'canonical routes must derive managerId from authenticated token');
assert(canonical.includes("req.user.id"), 'canonical routes must derive customerId from authenticated token');
assert(canonical.includes("cancelReservation(c,req.user.managerId,req.params.id,key,req.user.id)"), 'customer cancellation must pass authenticated customer id');
assert(server.includes("WHERE id = $1 AND manager_id = $2"), 'legacy manager reservation operations must scope by manager');
assert(canonical.includes("WHERE id=$1 AND manager_id=$2"), 'canonical manager reservation operations must scope by manager');
assert(canonical.includes("SELECT id FROM customers WHERE id=$1 AND manager_id=$2"), 'manager writes must validate customer ownership');
assert(financial.includes('WHERE id = $1 AND manager_id = $2'), 'pricing must scope customer lookup by manager');
assert(financial.includes('AND manager_id = $2 AND customer_id = $3'), 'payment ownership must scope reservation by manager and customer');
assert(financial.includes('AND customer_id = $3 FOR UPDATE'), 'customer cancellation must scope reservation by customer');
assert(reservation.includes('WHERE id = $1 AND manager_id = $2 FOR UPDATE'), 'reservation creation must verify customer tenant');
const customerLoginRoute = server.slice(server.indexOf("app.post('/api/auth/customer/login'"), server.indexOf('\n});', server.indexOf("app.post('/api/auth/customer/login'")) + 4);
assert(customerLoginRoute.includes('phone_number and password are required') && !customerLoginRoute.includes('!managerId ||'), 'customer login must require phone/password while allowing manager_id to be resolved securely when absent');
assert(server.includes('candidate.password_hash') && server.includes('bcrypt.compare(password, candidate.password_hash)'), 'customer login must verify the exact password against stored hashes');
assert(canonical.includes("/api/v1/manager/customers"), 'canonical manager customer route must exist');
assert(!server.includes("/api/manager/customers/:id/password"), 'legacy manager customer password route must be removed');

console.log('tenant-isolation static checks passed');
