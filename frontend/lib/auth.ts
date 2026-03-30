// Fetches auth state from the gateway's /bff/me endpoint.
// The gateway returns session info (populated from the server-side session)
// without ever sending the JWT to the browser.

export interface Session {
  authenticated: boolean;
  email?: string;
  name?: string;
  orgId?: string;
  isPlatformAdmin?: boolean;
}

export async function getSession(): Promise<Session> {
  try {
    const res = await fetch("/bff/me", {
      credentials: "include",
      headers: { Accept: "application/json" },
    });
    if (!res.ok) return { authenticated: false };
    return await res.json();
  } catch {
    return { authenticated: false };
  }
}
