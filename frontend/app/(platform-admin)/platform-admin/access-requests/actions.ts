"use server";

import { cookies } from "next/headers";
import { revalidatePath } from "next/cache";

const GATEWAY_URL = process.env.GATEWAY_URL ?? "http://localhost:8443";

export interface AccessRequest {
  id: string;
  email: string;
  fullName: string;
  organizationName: string;
  country: string;
  industry: string;
  status: string;
  otpVerifiedAt: string | null;
  createdAt: string;
}

interface ListResult {
  success: boolean;
  data?: AccessRequest[];
  error?: string;
}

interface ActionResult {
  success: boolean;
  error?: string;
}

async function gatewayFetch(path: string, options: RequestInit = {}) {
  const cookieStore = await cookies();
  const sessionCookie = cookieStore.get("SESSION");
  if (!sessionCookie) {
    throw new Error("SESSION_EXPIRED");
  }

  const res = await fetch(`${GATEWAY_URL}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      Cookie: `SESSION=${sessionCookie.value}`,
      ...options.headers,
    },
  });
  return res;
}

export async function listAccessRequests(): Promise<ListResult> {
  try {
    const res = await gatewayFetch("/api/platform-admin/access-requests");
    if (res.ok) {
      const data: AccessRequest[] = await res.json();
      return { success: true, data };
    }

    const errorBody = await res.json().catch(() => null);
    const errorMessage =
      errorBody?.detail ??
      errorBody?.error ??
      errorBody?.message ??
      "Failed to load access requests.";
    return { success: false, error: errorMessage };
  } catch (e) {
    const msg =
      e instanceof Error && e.message === "SESSION_EXPIRED"
        ? "Session expired. Please sign in again."
        : "Unable to reach the server. Please try again later.";
    return { success: false, error: msg };
  }
}

export async function approveAccessRequest(id: string): Promise<ActionResult> {
  try {
    const res = await gatewayFetch(
      `/api/platform-admin/access-requests/${id}/approve`,
      { method: "POST" },
    );

    if (res.ok) {
      revalidatePath("/platform-admin/access-requests");
      return { success: true };
    }

    const errorBody = await res.json().catch(() => null);
    const errorMessage =
      errorBody?.detail ??
      errorBody?.error ??
      errorBody?.message ??
      "Failed to approve request.";
    return { success: false, error: errorMessage };
  } catch (e) {
    const msg =
      e instanceof Error && e.message === "SESSION_EXPIRED"
        ? "Session expired. Please sign in again."
        : "Unable to reach the server. Please try again later.";
    return { success: false, error: msg };
  }
}

export async function rejectAccessRequest(id: string): Promise<ActionResult> {
  try {
    const res = await gatewayFetch(
      `/api/platform-admin/access-requests/${id}/reject`,
      { method: "POST" },
    );

    if (res.ok) {
      revalidatePath("/platform-admin/access-requests");
      return { success: true };
    }

    const errorBody = await res.json().catch(() => null);
    const errorMessage =
      errorBody?.detail ??
      errorBody?.error ??
      errorBody?.message ??
      "Failed to reject request.";
    return { success: false, error: errorMessage };
  } catch (e) {
    const msg =
      e instanceof Error && e.message === "SESSION_EXPIRED"
        ? "Session expired. Please sign in again."
        : "Unable to reach the server. Please try again later.";
    return { success: false, error: msg };
  }
}
