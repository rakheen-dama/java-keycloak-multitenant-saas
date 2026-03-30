"use server";

import { cookies } from "next/headers";
import { revalidatePath } from "next/cache";

const GATEWAY_URL = process.env.GATEWAY_URL ?? "http://localhost:8443";

// ── Response types ──────────────────────────────────────────────────

export interface CustomerResponse {
  id: string;
  name: string;
  email: string;
  company: string | null;
  status: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface CustomerSummary {
  id: string;
  name: string;
}

export interface MemberSummary {
  id: string;
  displayName: string;
}

export interface ProjectResponse {
  id: string;
  title: string;
  description: string | null;
  status: string;
  customer: CustomerSummary;
  createdBy: MemberSummary;
  createdAt: string;
}

// ── Result types ────────────────────────────────────────────────────

interface CustomerListResult {
  success: boolean;
  data?: CustomerResponse[];
  error?: string;
}

interface CustomerResult {
  success: boolean;
  data?: CustomerResponse;
  error?: string;
}

interface ProjectListResult {
  success: boolean;
  data?: ProjectResponse[];
  error?: string;
}

interface ActionResult {
  success: boolean;
  error?: string;
}

// ── Gateway fetch helper ────────────────────────────────────────────

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

function parseError(e: unknown, fallback: string): string {
  if (e instanceof Error && e.message === "SESSION_EXPIRED") {
    return "Session expired. Please sign in again.";
  }
  return fallback;
}

// ── Queries ─────────────────────────────────────────────────────────

export async function listCustomers(): Promise<CustomerListResult> {
  try {
    const res = await gatewayFetch("/api/customers");
    if (res.ok) {
      const data: CustomerResponse[] = await res.json();
      return { success: true, data };
    }
    const errorBody = await res.json().catch(() => null);
    const errorMessage =
      errorBody?.detail ??
      errorBody?.error ??
      errorBody?.message ??
      "Failed to load customers.";
    return { success: false, error: errorMessage };
  } catch (e) {
    return {
      success: false,
      error: parseError(e, "Unable to reach the server. Please try again later."),
    };
  }
}

export async function getCustomer(id: string): Promise<CustomerResult> {
  try {
    const res = await gatewayFetch(`/api/customers/${id}`);
    if (res.ok) {
      const data: CustomerResponse = await res.json();
      return { success: true, data };
    }
    const errorBody = await res.json().catch(() => null);
    const errorMessage =
      errorBody?.detail ??
      errorBody?.error ??
      errorBody?.message ??
      "Failed to load customer.";
    return { success: false, error: errorMessage };
  } catch (e) {
    return {
      success: false,
      error: parseError(e, "Unable to reach the server. Please try again later."),
    };
  }
}

export async function listProjectsByCustomer(
  customerId: string,
): Promise<ProjectListResult> {
  try {
    const res = await gatewayFetch(`/api/projects?customerId=${customerId}`);
    if (res.ok) {
      const data: ProjectResponse[] = await res.json();
      return { success: true, data };
    }
    const errorBody = await res.json().catch(() => null);
    const errorMessage =
      errorBody?.detail ??
      errorBody?.error ??
      errorBody?.message ??
      "Failed to load projects.";
    return { success: false, error: errorMessage };
  } catch (e) {
    return {
      success: false,
      error: parseError(e, "Unable to reach the server. Please try again later."),
    };
  }
}

// ── Mutations ───────────────────────────────────────────────────────

export async function createCustomer(
  name: string,
  email: string,
  company?: string,
): Promise<ActionResult> {
  try {
    const res = await gatewayFetch("/api/customers", {
      method: "POST",
      body: JSON.stringify({ name, email, company }),
    });

    if (res.ok) {
      revalidatePath("/customers");
      revalidatePath("/dashboard");
      return { success: true };
    }

    const errorBody = await res.json().catch(() => null);
    const errorMessage =
      errorBody?.detail ??
      errorBody?.error ??
      errorBody?.message ??
      "Failed to create customer.";
    return { success: false, error: errorMessage };
  } catch (e) {
    return {
      success: false,
      error: parseError(e, "Unable to reach the server. Please try again later."),
    };
  }
}

export async function updateCustomer(
  id: string,
  name: string,
  email: string,
  company?: string,
): Promise<ActionResult> {
  try {
    const res = await gatewayFetch(`/api/customers/${id}`, {
      method: "PUT",
      body: JSON.stringify({ name, email, company }),
    });

    if (res.ok) {
      revalidatePath(`/customers/${id}`);
      revalidatePath("/customers");
      revalidatePath("/dashboard");
      return { success: true };
    }

    const errorBody = await res.json().catch(() => null);
    const errorMessage =
      errorBody?.detail ??
      errorBody?.error ??
      errorBody?.message ??
      "Failed to update customer.";
    return { success: false, error: errorMessage };
  } catch (e) {
    return {
      success: false,
      error: parseError(e, "Unable to reach the server. Please try again later."),
    };
  }
}

export async function archiveCustomer(id: string): Promise<ActionResult> {
  try {
    const res = await gatewayFetch(`/api/customers/${id}`, {
      method: "DELETE",
    });

    if (res.ok || res.status === 204) {
      revalidatePath("/customers");
      revalidatePath("/dashboard");
      return { success: true };
    }

    const errorBody = await res.json().catch(() => null);
    const errorMessage =
      errorBody?.detail ??
      errorBody?.error ??
      errorBody?.message ??
      "Failed to archive customer.";
    return { success: false, error: errorMessage };
  } catch (e) {
    return {
      success: false,
      error: parseError(e, "Unable to reach the server. Please try again later."),
    };
  }
}
