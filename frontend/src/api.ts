export type Pod = {
  id: string;
  name: string;
  sshHost: string;
  sshPort: number;
  sshUsername: string;
  credentialType: string;
  hasCredential: boolean;
  addresses: { address: string; role: string }[];
  createdAt: string;
};

export type Status = {
  name: string;
  version: string;
  mode: string;
  executionEnabled: boolean;
};

export type ChatResponse = {
  provider: string;
  model: string;
  answer: string;
  executed: boolean;
};

export type AdminSession = {
  authenticated: boolean;
  csrf: string;
  expiresAt: string;
};

/** Not an authentication credential; only protects cookie-authenticated writes. */
let csrfToken = "";

export class AuthenticationRequiredError extends Error {
  constructor() {
    super("Your administrator session has expired. Sign in again to continue.");
    this.name = "AuthenticationRequiredError";
  }
}

export class ApiRequestError extends Error {
  constructor(public readonly status: number) {
    super(status === 403
      ? "Request rejected (HTTP 403). Refresh to renew your session before retrying."
      : "MONIQUE API returned HTTP " + status);
    this.name = "ApiRequestError";
  }
}

async function request<T>(route: string, options: RequestInit = {}): Promise<T> {
  if (!route.startsWith("/") || route.includes("?") || route.includes("#")) {
    throw new Error("Invalid MONIQUE API route");
  }
  const method = (options.method || "GET").toUpperCase();
  const headers = new Headers(options.headers);
  headers.set("Accept", "application/json");
  if (options.body) headers.set("Content-Type", "application/json");
  if (!["GET", "HEAD", "OPTIONS"].includes(method) && route !== "/auth/login") {
    if (!csrfToken) throw new AuthenticationRequiredError();
    headers.set("X-MONIQUE-CSRF", csrfToken);
  }

  const response = await fetch("/api" + route, {
    ...options,
    method,
    credentials: "same-origin",
    redirect: "error",
    cache: "no-store",
    headers,
  });
  if (!response.ok) {
    if (response.status === 401) throw new AuthenticationRequiredError();
    throw new ApiRequestError(response.status);
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export async function loginAdmin(adminToken: string): Promise<AdminSession> {
  const response = await request<AdminSession>("/auth/login", {
    method: "POST",
    body: JSON.stringify({ token: adminToken }),
  });
  csrfToken = response.csrf;
  return response;
}

export async function restoreAdmin(): Promise<AdminSession> {
  const response = await request<AdminSession>("/auth/session");
  csrfToken = response.csrf;
  return response;
}

export async function logoutAdmin(): Promise<void> {
  try {
    if (csrfToken) await request<void>("/auth/logout", { method: "POST" });
  } finally {
    csrfToken = "";
  }
}

/** Clear only client-side CSRF state after an expired or revoked session. */
export function clearAdminSession(): void {
  csrfToken = "";
}

export async function api<T>(route: string, options: RequestInit = {}): Promise<T> {
  return request<T>(route, options);
}
