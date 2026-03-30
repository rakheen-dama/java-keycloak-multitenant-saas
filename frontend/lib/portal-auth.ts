/**
 * Client-side localStorage helpers for portal authentication.
 * Do NOT import this module in Server Components.
 */

const PORTAL_TOKEN_KEY = "portal_token";
const PORTAL_CUSTOMER_NAME_KEY = "portal_customer_name";

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
