/**
 * Client-side API client for portal acceptance pages.
 * Uses plain fetch with token-based auth (no JWT needed).
 * Requests go through Next.js rewrites which proxy to the backend.
 */

export interface AcceptancePageData {
  requestId: string;
  status: "PENDING" | "SENT" | "VIEWED" | "ACCEPTED" | "EXPIRED" | "REVOKED";
  documentTitle: string | null;
  documentFileName: string | null;
  expiresAt: string | null;
  orgName: string | null;
  orgLogo: string | null;
  brandColor: string | null;
  acceptedAt: string | null;
  acceptorName: string | null;
}

export interface AcceptResponse {
  status: string;
  acceptedAt: string;
  acceptorName: string;
}

export async function getAcceptancePageData(
  token: string,
): Promise<AcceptancePageData> {
  const res = await fetch(
    `/api/portal/acceptance/${encodeURIComponent(token)}`,
  );
  if (!res.ok) throw new Error(res.status === 404 ? "not_found" : "error");
  return res.json();
}

export async function getAcceptancePdf(token: string): Promise<Blob> {
  const res = await fetch(
    `/api/portal/acceptance/${encodeURIComponent(token)}/pdf`,
  );
  if (!res.ok) throw new Error("Failed to load document");
  return res.blob();
}

export async function acceptDocument(
  token: string,
  name: string,
): Promise<AcceptResponse> {
  const res = await fetch(
    `/api/portal/acceptance/${encodeURIComponent(token)}/accept`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name }),
    },
  );
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    throw new Error(body?.detail || body?.title || "Failed to accept");
  }
  return res.json();
}
