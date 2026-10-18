# Admin bootstrap (secure first-admin procedure)

Admin rights are enforced server-side by the `admin_users` table. They can never
be granted by an email string alone or any client-side flag.

## Provisioning the first administrator

1. The intended administrator creates a normal account (email confirmed) by
   signing up in the Trading Master Admin app.
2. Someone with database access (the owner, through the Supabase dashboard SQL
   editor or management API) runs exactly one INSERT:

```sql
insert into admin_users (id, email, role)
select u.id, u.email, 'super_admin'
from auth.users u
where u.email = '<intended-admin-email>'          -- replace with the real address
  and u.email_verified = true                      -- account must be verified
  and not exists (select 1 from admin_users);
```

3. The intended administrator logs into the Admin app. The app calls `is_admin()`
   server-side; only then does the console unlock.

Rules:

- The email must already exist in `auth.users` and be verified. Do not insert an
  address that has not authenticated at least once.
- Every admin mutation writes an audit row (actor, action, target, details).
- Additional admins are added by a `super_admin` through SQL or by extending the
  admin RPCs; there is deliberately no self-service admin signup.
- Admins must never be able to read users' Binance API secrets — those exist only
  on each user's device Keystore and are never sent to the backend.

## Device policy

One approved device per user per app by default. Re-login on the same approved
device does not require a new approval (`register_device` matches the existing
approved registration). A device change requires admin approval or revocation
followed by re-approval. Devices are managed in the Admin app
(approve / revoke).
