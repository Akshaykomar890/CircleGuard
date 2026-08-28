# CircleGuard notification Worker

This Worker receives authenticated geofence exit events, verifies the Firebase ID token, checks group membership in Firestore, suppresses duplicate event IDs, and sends FCM notifications to every registered group member except the sender.

## Configure

From this directory:

```bash
npm install
npx wrangler types
npx wrangler secret put FIREBASE_API_KEY
npx wrangler secret put FIREBASE_SERVICE_ACCOUNT_JSON
```

`FIREBASE_SERVICE_ACCOUNT_JSON` must be the Firebase service-account JSON contents. Never commit it or place it in `wrangler.jsonc`.

After deployment, put the Worker URL in `CIRCLEGUARD_WORKER_URL` in `app/build.gradle.kts`, rebuild the APK, and install it on both devices.

The Android client sends `POST /v1/geofence-exit` with a Firebase ID token. The Worker owns all privileged Firestore and FCM operations.
