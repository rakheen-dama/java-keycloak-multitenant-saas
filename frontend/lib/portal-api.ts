/**
 * Client-side API client for the customer portal.
 * Uses portal JWT (from magic link exchange), NOT org auth.
 * This module is safe to import in client components only.
 */

import { getPortalToken } from "./portal-auth";

export class PortalApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
    this.name = "PortalApiError";
  }
}

interface PortalRequestOptions {
  method?: "GET" | "POST";
  body?: unknown;
}

async function portalRequest<T>(
  endpoint: string,
  options: PortalRequestOptions = {},
): Promise<T> {
  const token = getPortalToken();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    Accept: "application/json",
  };

  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  const response = await fetch(endpoint, {
    method: options.method || "GET",
    headers,
    body: options.body ? JSON.stringify(options.body) : undefined,
  });

  if (!response.ok) {
    let message = response.statusText;
    try {
      const contentType = response.headers.get("content-type");
      if (
        contentType?.includes("application/json") ||
        contentType?.includes("application/problem+json")
      ) {
        const detail = await response.json();
        message = detail?.detail || detail?.title || message;
      }
    } catch {
      // ignore
    }
    throw new PortalApiError(response.status, message);
  }

  const contentLength = response.headers.get("content-length");
  if (response.status === 204 || contentLength === "0") {
    return undefined as T;
  }

  const text = await response.text();
  if (!text) return undefined as T;
  return JSON.parse(text) as T;
}

export const portalApi = {
  get: <T>(endpoint: string) => portalRequest<T>(endpoint, { method: "GET" }),
  post: <T>(endpoint: string, body?: unknown) =>
    portalRequest<T>(endpoint, { method: "POST", body }),
};
