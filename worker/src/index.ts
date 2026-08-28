type RuntimeEnv = Env & {
  FIREBASE_API_KEY: string;
  FIREBASE_SERVICE_ACCOUNT_JSON: string;
};

type ExitPayload = {
  eventId: string;
  groupId: string;
  occurredAt: string;
};

type FirebaseValue = {
  stringValue?: string;
  integerValue?: string;
  arrayValue?: { values?: FirebaseValue[] };
  mapValue?: { fields?: Record<string, FirebaseValue> };
};

type FirebaseDocument = {
  name?: string;
  fields?: Record<string, FirebaseValue>;
};

type FirebaseServiceAccount = {
  client_email?: string;
  private_key?: string;
  token_uri?: string;
};

type FirebaseUserLookup = {
  users?: Array<{ localId?: string }>;
};

type FirebaseDocumentList = {
  documents?: FirebaseDocument[];
};

class HttpError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

const jsonHeaders = { "Content-Type": "application/json" };

const handler: ExportedHandler<RuntimeEnv> = {
  async fetch(request, env): Promise<Response> {
    if (request.method === "OPTIONS") return new Response(null, { status: 204 });
    const url = new URL(request.url);
    if (request.method !== "POST" || url.pathname !== "/v1/geofence-exit") {
      return json({ error: "Not found" }, 404);
    }

    try {
      const uid = await verifyFirebaseIdToken(request, env);
      const payload = await parseExitPayload(request);
      const accessToken = await getGoogleAccessToken(env);
      const group = await getGroup(env, accessToken, payload.groupId);
      if (!group.memberIds.includes(uid)) throw new HttpError(403, "User is not a group member");

      const created = await recordEvent(env, accessToken, payload, uid);
      if (!created) return json({ accepted: true, duplicate: true }, 200);

      const recipients = (await getDeviceTokens(env, accessToken, payload.groupId))
        .filter((recipient) => recipient.userId !== uid);
      const senderName = group.memberNames[uid] ?? "A group member";
      const sent = await sendNotifications(
        env,
        accessToken,
        recipients.map((recipient) => recipient.token),
        {
          title: "CircleGuard alert",
          body: `${senderName} left ${group.name}`,
          eventId: payload.eventId,
          groupId: payload.groupId,
        },
      );
      return json({ accepted: true, sent }, 200);
    } catch (error) {
      if (error instanceof HttpError) return json({ error: error.message }, error.status);
      console.error(JSON.stringify({ event: "geofence_exit_failed", error: String(error) }));
      return json({ error: "Internal server error" }, 500);
    }
  },
};

export default handler;

async function verifyFirebaseIdToken(request: Request, env: RuntimeEnv): Promise<string> {
  const authorization = request.headers.get("Authorization");
  const token = authorization?.match(/^Bearer\s+(.+)$/i)?.[1];
  if (!token || !env.FIREBASE_API_KEY) throw new HttpError(401, "Missing Firebase ID token");

  const response = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=${encodeURIComponent(env.FIREBASE_API_KEY)}`,
    {
      method: "POST",
      headers: jsonHeaders,
      body: JSON.stringify({ idToken: token }),
    },
  );
  const result = (await response.json()) as FirebaseUserLookup;
  const uid = result.users?.[0]?.localId;
  if (!response.ok || !uid) throw new HttpError(401, "Invalid Firebase ID token");
  return uid;
}

async function parseExitPayload(request: Request): Promise<ExitPayload> {
  let input: unknown;
  try {
    input = await request.json();
  } catch {
    throw new HttpError(400, "Request body must be valid JSON");
  }
  if (!isRecord(input)) throw new HttpError(400, "Request body must be an object");
  const eventId = input.eventId;
  const groupId = input.groupId;
  const occurredAt = input.occurredAt;
  if (
    typeof eventId !== "string" || !/^[A-Za-z0-9-]{1,100}$/.test(eventId) ||
    typeof groupId !== "string" || !/^[A-Z0-9]{6}$/.test(groupId) ||
    typeof occurredAt !== "string"
  ) {
    throw new HttpError(400, "Invalid geofence exit payload");
  }
  return { eventId, groupId, occurredAt };
}

function isRecord(input: unknown): input is Record<string, unknown> {
  return typeof input === "object" && input !== null && !Array.isArray(input);
}

async function getGroup(
  env: RuntimeEnv,
  accessToken: string,
  groupId: string,
): Promise<{ name: string; memberIds: string[]; memberNames: Record<string, string> }> {
  const document = await firestoreGet(env, accessToken, `groups/${encodeURIComponent(groupId)}`);
  const fields = document.fields ?? {};
  const memberIds = arrayStrings(fields.memberIds);
  const memberMap = fields.members?.mapValue?.fields ?? {};
  const memberNames: Record<string, string> = {};
  for (const [uid, member] of Object.entries(memberMap)) {
    const name = member.mapValue?.fields?.displayName?.stringValue;
    if (name) memberNames[uid] = name;
  }
  return {
    name: fields.name?.stringValue ?? "your group",
    memberIds,
    memberNames,
  };
}

async function getDeviceTokens(
  env: RuntimeEnv,
  accessToken: string,
  groupId: string,
): Promise<Array<{ userId: string; token: string }>> {
  const list = await firestoreRequest<FirebaseDocumentList>(
    env,
    accessToken,
    `groups/${encodeURIComponent(groupId)}/deviceTokens?pageSize=100`,
  );
  return (list.documents ?? []).flatMap((document) => {
    const userId = document.name?.split("/").pop();
    const token = document.fields?.token?.stringValue;
    return userId && token ? [{ userId, token }] : [];
  });
}

async function recordEvent(
  env: RuntimeEnv,
  accessToken: string,
  payload: ExitPayload,
  uid: string,
): Promise<boolean> {
  const path = `groups/${encodeURIComponent(payload.groupId)}/events/${encodeURIComponent(payload.eventId)}`;
  const response = await firestoreCommit(env, accessToken, {
    writes: [
      {
        update: {
          name: `projects/${env.FIREBASE_PROJECT_ID}/databases/(default)/documents/${path}`,
          fields: {
            uid: { stringValue: uid },
            occurredAt: { stringValue: payload.occurredAt },
          },
        },
        currentDocument: { exists: false },
      },
    ],
  });
  if (response.status === 409 || response.status === 400) return false;
  if (!response.ok) throw new HttpError(502, "Could not record exit event");
  return true;
}

async function sendNotifications(
  env: RuntimeEnv,
  accessToken: string,
  tokens: string[],
  notification: { title: string; body: string; eventId: string; groupId: string },
): Promise<number> {
  let sent = 0;
  for (const token of tokens) {
    const response = await fetch(
      `https://fcm.googleapis.com/v1/projects/${encodeURIComponent(env.FIREBASE_PROJECT_ID)}/messages:send`,
      {
        method: "POST",
        headers: { ...jsonHeaders, Authorization: `Bearer ${accessToken}` },
        body: JSON.stringify({
          message: {
            token,
            notification: { title: notification.title, body: notification.body },
            data: {
              eventId: notification.eventId,
              groupId: notification.groupId,
            },
          },
        }),
      },
    );
    if (!response.ok) throw new HttpError(502, "FCM delivery failed");
    sent += 1;
  }
  return sent;
}

async function getGoogleAccessToken(env: RuntimeEnv): Promise<string> {
  let account: FirebaseServiceAccount;
  try {
    account = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT_JSON) as FirebaseServiceAccount;
  } catch {
    throw new HttpError(500, "Firebase service account configuration is invalid");
  }
  if (!account.client_email || !account.private_key) {
    throw new HttpError(500, "Firebase service account configuration is incomplete");
  }

  const now = Math.floor(Date.now() / 1000);
  const assertion = await signJwt(
    {
      alg: "RS256",
      typ: "JWT",
    },
    {
      iss: account.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging https://www.googleapis.com/auth/datastore",
      aud: account.token_uri ?? "https://oauth2.googleapis.com/token",
      iat: now,
      exp: now + 3600,
    },
    account.private_key,
  );
  const response = await fetch(account.token_uri ?? "https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  const result = (await response.json()) as { access_token?: string };
  if (!response.ok || !result.access_token) throw new HttpError(502, "Could not authenticate with Google");
  return result.access_token;
}

async function signJwt(header: Record<string, string>, payload: Record<string, string | number>, privateKey: string): Promise<string> {
  const encodedHeader = base64Url(JSON.stringify(header));
  const encodedPayload = base64Url(JSON.stringify(payload));
  const unsigned = `${encodedHeader}.${encodedPayload}`;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToArrayBuffer(privateKey),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(unsigned),
  );
  return `${unsigned}.${base64Url(signature)}`;
}

function base64Url(input: string | ArrayBuffer): string {
  const bytes = typeof input === "string" ? new TextEncoder().encode(input) : new Uint8Array(input);
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function pemToArrayBuffer(pem: string): ArrayBuffer {
  const base64 = pem.replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g, "");
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
  return bytes.buffer;
}

function arrayStrings(value: FirebaseValue | undefined): string[] {
  return value?.arrayValue?.values?.flatMap((item) => item.stringValue ? [item.stringValue] : []) ?? [];
}

async function firestoreGet(env: RuntimeEnv, accessToken: string, path: string): Promise<FirebaseDocument> {
  return firestoreRequest<FirebaseDocument>(env, accessToken, path);
}

async function firestoreRequest<T>(env: RuntimeEnv, accessToken: string, path: string): Promise<T> {
  const response = await fetch(
    `https://firestore.googleapis.com/v1/projects/${encodeURIComponent(env.FIREBASE_PROJECT_ID)}/databases/(default)/documents/${path}`,
    { headers: { Authorization: `Bearer ${accessToken}` } },
  );
  if (response.status === 404) throw new HttpError(404, "Group was not found");
  if (!response.ok) throw new HttpError(502, "Firestore request failed");
  return (await response.json()) as T;
}

async function firestoreCommit(env: RuntimeEnv, accessToken: string, body: unknown): Promise<Response> {
  return fetch(
    `https://firestore.googleapis.com/v1/projects/${encodeURIComponent(env.FIREBASE_PROJECT_ID)}/databases/(default)/documents:commit`,
    {
      method: "POST",
      headers: { ...jsonHeaders, Authorization: `Bearer ${accessToken}` },
      body: JSON.stringify(body),
    },
  );
}

function json(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), { status, headers: jsonHeaders });
}
