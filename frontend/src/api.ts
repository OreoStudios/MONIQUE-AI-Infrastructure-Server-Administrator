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

export async function api<T>(
  token: string,
  route: string,
  options: RequestInit = {},
): Promise<T> {
  if (!route.startsWith("/") || route.includes("?") || route.includes("#")) {
    throw new Error("Invalid MONIQUE API route");
  }
  const response = await fetch("/api" + route, {
    ...options,
    redirect: "error",
    headers: {
      Accept: "application/json",
      Authorization: "Bearer " + token,
      ...(options.body ? { "Content-Type": "application/json" } : {}),
    },
  });
  if (!response.ok) {
    if (response.status === 401 || response.status === 403) {
      throw new Error("Access denied. Check your MONIQUE admin token.");
    }
    throw new Error("MONIQUE API returned HTTP " + response.status);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}
