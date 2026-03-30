/**
 * Client-side API client for the customer portal.
 * Uses portal JWT (from magic link exchange), NOT org auth.
 *
 * All requests go through Next.js rewrites (defined in next.config.ts)
 * which proxy to the backend. This avoids CORS issues and keeps the
 * backend URL server-side only.
 */

import type { ProblemDetail } from "@/lib/types";

const PORTAL_TOKEN_KEY = "portal_token";
const PORTAL_CUSTOMER_NAME_KEY = "portal_customer_name";

export class PortalApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public detail?: ProblemDetail,
  ) {
    super(message);
    this.name = "PortalApiError";
  }
}

// --- Token management (localStorage) ---

export function getPortalToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(PORTAL_TOKEN_KEY);
}

export function setPortalToken(token: string): void {
  if (typeof window === "undefined") return;
  localStorage.setItem(PORTAL_TOKEN_KEY, token);
}

export function getPortalCustomerName(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(PORTAL_CUSTOMER_NAME_KEY);
}

export function setPortalCustomerName(name: string): void {
  if (typeof window === "undefined") return;
  localStorage.setItem(PORTAL_CUSTOMER_NAME_KEY, name);
}

export function clearPortalAuth(): void {
  if (typeof window === "undefined") return;
  localStorage.removeItem(PORTAL_TOKEN_KEY);
  localStorage.removeItem(PORTAL_CUSTOMER_NAME_KEY);
}

export function isPortalAuthenticated(): boolean {
  return !!getPortalToken();
}

// --- API request ---

interface PortalRequestOptions {
  method?: "GET" | "POST";
  body?: unknown;
}

export async function portalRequest<T>(
  endpoint: string,
  options: PortalRequestOptions = {},
): Promise<T> {
  const token = getPortalToken();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };

  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  // Requests go to relative URLs — Next.js rewrites proxy to backend
  const response = await fetch(endpoint, {
    method: options.method || "GET",
    headers,
    body: options.body ? JSON.stringify(options.body) : undefined,
  });

  if (!response.ok) {
    let detail: ProblemDetail | undefined;
    let message = response.statusText;

    try {
      const contentType = response.headers.get("content-type");
      if (
        contentType?.includes("application/json") ||
        contentType?.includes("application/problem+json")
      ) {
        detail = await response.json();
        message = detail?.detail || detail?.title || message;
      } else {
        message = (await response.text()) || message;
      }
    } catch {
      // Failed to parse error body
    }

    throw new PortalApiError(response.status, message, detail);
  }

  const contentLength = response.headers.get("content-length");
  if (response.status === 204 || contentLength === "0") {
    return undefined as T;
  }

  const text = await response.text();
  if (!text) {
    return undefined as T;
  }

  return JSON.parse(text) as T;
}

export const portalApi = {
  get: <T>(endpoint: string) => portalRequest<T>(endpoint, { method: "GET" }),

  post: <T>(endpoint: string, body?: unknown) =>
    portalRequest<T>(endpoint, { method: "POST", body }),
};
