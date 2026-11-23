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
  constructor(public readonly status: number, message?: string) {
    super(message ?? (status === 403
      ? "MONIQUE rejected this request (HTTP 403). Your admin login may still be active."
      : "MONIQUE API returned HTTP " + status));
    this.name = "ApiRequestError";
  }
}

/**
 * Independently verify the cookie with Spring Security before claiming that
 * the administrator was logged out. A 401 emitted by a protected /error
 * dispatch, reverse proxy, or endpoint is not proof of cookie expiry.
 *
 * Returns "unknown" on infrastructure/network errors rather than logging
 * out an operator who might still be authenticated.
 */
async function checkBrowserSession(): Promise<"active" | "expired" | "unknown"> {
  try {
    const response = await fetch("/api/auth/session", {
      method: "GET",
      credentials: "same-origin",
      redirect: "error",
      cache: "no-store",
      headers: { Accept: "application/json" },
    });
    if (response.status === 401) return "expired";
    if (!response.ok) return "unknown";
    const session = (await response.json()) as AdminSession;
    if (session.authenticated !== true || typeof session.csrf !== "string" || !session.csrf) {
      return "unknown";
    }
    csrfToken = session.csrf;
    return "active";
  } catch {
    return "unknown";
  }
}

async function request<T>(route: string, options: RequestInit = {}): Promise<T> {
  if (!route.startsWith("/") || route.includes("?") || route.includes("#")) {
    throw new Error("Invalid MONIQUE API route");
  }
  const method = (options.method || "GET").toUpperCase();
  const unsafe = !["GET", "HEAD", "OPTIONS"].includes(method);
  const isLogin = route === "/auth/login";
  const isAuthEndpoint = route.startsWith("/auth/");

  // A missing CSRF token after HMR/reload must not be mistaken for an expired
  // admin session. First recover the current token from the authenticated API.
  if (unsafe && !isLogin && !csrfToken) {
    const state = await checkBrowserSession();
    if (state === "expired") throw new AuthenticationRequiredError();
    if (state !== "active") {
      throw new ApiRequestError(0, "Unable to verify the admin session. Please retry.");
    }
  }

  const send = () => {
    const headers = new Headers(options.headers);
    headers.set("Accept", "application/json");
    if (options.body) headers.set("Content-Type", "application/json");
    if (unsafe && !isLogin) headers.set("X-MONIQUE-CSRF", csrfToken);
    return fetch("/api" + route, {
      ...options,
      method,
      credentials: "same-origin",
      redirect: "error",
      cache: "no-store",
      headers,
    });
  };

  let response = await send();
  if (!response.ok && (response.status === 401 || response.status === 403) && !isAuthEndpoint) {
    const state = await checkBrowserSession();
    if (state === "expired") throw new AuthenticationRequiredError();
    if (state === "active") {
      // One retry after recovering the authoritative CSRF value. The backend
      // rejects unauthenticated writes before executing any business action.
      response = await send();
    }
    // If verification could not finish, preserve the UI session rather than
    // forcing a logout on ambiguous proxy/provider errors.
  }

  if (!response.ok) {
    // Only accept safe, structured provider error messages emitted by MONIQUE.
    // Never display arbitrary upstream bodies or proxy HTML responses.
    let providerMessage: string | undefined;
    if ((response.headers.get("content-type") || "").includes("application/json")) {
      try {
        const body: unknown = await response.clone().json();
        if (body && typeof body === "object" && "code" in body && "message" in body) {
          const issue = body as { code?: unknown; message?: unknown };
          if (typeof issue.code === "string"
              && /^(OPENAI|ANTHROPIC)_[A-Z_]+$/.test(issue.code)
              && typeof issue.message === "string"
              && issue.message.length <= 500) {
            providerMessage = issue.message;
          }
        }
      } catch {
        // Unstructured error: report its status without leaking content.
      }
    }
    if (response.status === 401 && isAuthEndpoint) throw new AuthenticationRequiredError();
    if (response.status === 401) {
      throw new ApiRequestError(401,
        "The chat request returned HTTP 401, but your admin session could not be confirmed as expired. Please retry.");
    }
    if (response.status === 403) {
      throw new ApiRequestError(403,
        "The chat request was blocked by a security check (HTTP 403). Your admin session is not necessarily expired.");
    }
    throw new ApiRequestError(response.status, providerMessage);
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export async function loginAdmin(adminToken: string): Promise<AdminSession> {
  try {
    const response = await request<AdminSession>("/auth/login", {
      method: "POST",
      body: JSON.stringify({ token: adminToken }),
    });
    csrfToken = response.csrf;
    return response;
  } catch (err) {
    if (err instanceof AuthenticationRequiredError) {
      throw new Error("Incorrect administrator token. Check MONIQUE_ADMIN_TOKEN in Coolify.");
    }
    throw err;
  }
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
