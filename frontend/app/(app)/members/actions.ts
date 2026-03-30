"use server";

import { cookies } from "next/headers";
import { revalidatePath } from "next/cache";

const GATEWAY_URL = process.env.GATEWAY_URL ?? "http://localhost:8443";

export interface MemberResponse {
  id: string;
  email: string;
  displayName: string | null;
  role: string;
  status: string;
  firstLoginAt: string | null;
  lastLoginAt: string | null;
  createdAt: string;
}

interface ListResult {
  success: boolean;
  data?: MemberResponse[];
  error?: string;
}

interface MemberResult {
  success: boolean;
  data?: MemberResponse;
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

function parseError(
  e: unknown,
  fallback: string,
): string {
  if (e instanceof Error && e.message === "SESSION_EXPIRED") {
    return "Session expired. Please sign in again.";
  }
  return fallback;
}

export async function listMembers(): Promise<ListResult> {
  try {
    const res = await gatewayFetch("/api/members");
    if (res.ok) {
      const data: MemberResponse[] = await res.json();
      return { success: true, data };
    }

    const errorBody = await res.json().catch(() => null);
    const errorMessage =
      errorBody?.detail ??
      errorBody?.error ??
      errorBody?.message ??
      "Failed to load members.";
    return { success: false, error: errorMessage };
  } catch (e) {
    return {
      success: false,
      error: parseError(e, "Unable to reach the server. Please try again later."),
    };
  }
}

export async function getCurrentMember(): Promise<MemberResult> {
  try {
    const res = await gatewayFetch("/api/members/me");
    if (res.ok) {
      const data: MemberResponse = await res.json();
      return { success: true, data };
    }

    const errorBody = await res.json().catch(() => null);
    const errorMessage =
      errorBody?.detail ??
      errorBody?.error ??
      errorBody?.message ??
      "Failed to load current member.";
    return { success: false, error: errorMessage };
  } catch (e) {
    return {
      success: false,
      error: parseError(e, "Unable to reach the server. Please try again later."),
    };
  }
}

export async function inviteMember(email: string): Promise<ActionResult> {
  try {
    const res = await gatewayFetch("/api/members/invite", {
      method: "POST",
      body: JSON.stringify({ email }),
    });

    if (res.ok) {
      revalidatePath("/members");
      return { success: true };
    }

    const errorBody = await res.json().catch(() => null);
    const errorMessage =
      errorBody?.detail ??
      errorBody?.error ??
      errorBody?.message ??
      "Failed to send invitation.";
    return { success: false, error: errorMessage };
  } catch (e) {
    return {
      success: false,
      error: parseError(e, "Unable to reach the server. Please try again later."),
    };
  }
}

export async function changeMemberRole(
  id: string,
  role: string,
): Promise<ActionResult> {
  try {
    const res = await gatewayFetch(`/api/members/${id}/role`, {
      method: "PATCH",
      body: JSON.stringify({ role }),
    });

    if (res.ok) {
      revalidatePath("/members");
      return { success: true };
    }

    const errorBody = await res.json().catch(() => null);
    const errorMessage =
      errorBody?.detail ??
      errorBody?.error ??
      errorBody?.message ??
      "Failed to update role.";
    return { success: false, error: errorMessage };
  } catch (e) {
    return {
      success: false,
      error: parseError(e, "Unable to reach the server. Please try again later."),
    };
  }
}

export async function removeMember(id: string): Promise<ActionResult> {
  try {
    const res = await gatewayFetch(`/api/members/${id}`, {
      method: "DELETE",
    });

    if (res.ok || res.status === 204) {
      revalidatePath("/members");
      return { success: true };
    }

    const errorBody = await res.json().catch(() => null);
    const errorMessage =
      errorBody?.detail ??
      errorBody?.error ??
      errorBody?.message ??
      "Failed to remove member.";
    return { success: false, error: errorMessage };
  } catch (e) {
    return {
      success: false,
      error: parseError(e, "Unable to reach the server. Please try again later."),
    };
  }
}
