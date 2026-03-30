"use server";

import { cookies } from "next/headers";
import { revalidatePath } from "next/cache";

const GATEWAY_URL = process.env.GATEWAY_URL ?? "http://localhost:8443";

// ── Response types ──────────────────────────────────────────────────

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

export interface CustomerResponse {
  id: string;
  name: string;
  email: string;
  company: string | null;
  status: string;
  createdAt: string;
}

export interface CommentResponse {
  id: string;
  projectId: string;
  content: string;
  authorType: string;
  authorId: string;
  authorName: string;
  createdAt: string;
}

export interface MemberResponse {
  id: string;
  email: string;
  displayName: string | null;
  role: string;
  status: string;
  createdAt: string;
}

// ── Result types ────────────────────────────────────────────────────

interface ProjectListResult {
  success: boolean;
  data?: ProjectResponse[];
  error?: string;
}

interface ProjectResult {
  success: boolean;
  data?: ProjectResponse;
  error?: string;
}

interface CustomerListResult {
  success: boolean;
  data?: CustomerResponse[];
  error?: string;
}

interface CommentListResult {
  success: boolean;
  data?: CommentResponse[];
  error?: string;
}

interface MemberResult {
  success: boolean;
  data?: MemberResponse;
  error?: string;
}

interface MemberListResult {
  success: boolean;
  data?: MemberResponse[];
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

export async function listProjects(): Promise<ProjectListResult> {
  try {
    const res = await gatewayFetch("/api/projects");
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

export async function getProject(id: string): Promise<ProjectResult> {
  try {
    const res = await gatewayFetch(`/api/projects/${id}`);
    if (res.ok) {
      const data: ProjectResponse = await res.json();
      return { success: true, data };
    }
    const errorBody = await res.json().catch(() => null);
    const errorMessage =
      errorBody?.detail ??
      errorBody?.error ??
      errorBody?.message ??
      "Failed to load project.";
    return { success: false, error: errorMessage };
  } catch (e) {
    return {
      success: false,
      error: parseError(e, "Unable to reach the server. Please try again later."),
    };
  }
}

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

export async function getComments(projectId: string): Promise<CommentListResult> {
  try {
    const res = await gatewayFetch(`/api/projects/${projectId}/comments`);
    if (res.ok) {
      const data: CommentResponse[] = await res.json();
      return { success: true, data };
    }
    const errorBody = await res.json().catch(() => null);
    const errorMessage =
      errorBody?.detail ??
      errorBody?.error ??
      errorBody?.message ??
      "Failed to load comments.";
    return { success: false, error: errorMessage };
  } catch (e) {
    return {
      success: false,
      error: parseError(e, "Unable to reach the server. Please try again later."),
    };
  }
}

export async function listMembers(): Promise<MemberListResult> {
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

// ── Mutations ───────────────────────────────────────────────────────

export async function createProject(
  title: string,
  customerId: string,
  description?: string,
): Promise<ActionResult> {
  try {
    const res = await gatewayFetch("/api/projects", {
      method: "POST",
      body: JSON.stringify({ title, customerId, description }),
    });

    if (res.ok) {
      revalidatePath("/projects");
      revalidatePath("/dashboard");
      return { success: true };
    }

    const errorBody = await res.json().catch(() => null);
    const errorMessage =
      errorBody?.detail ??
      errorBody?.error ??
      errorBody?.message ??
      "Failed to create project.";
    return { success: false, error: errorMessage };
  } catch (e) {
    return {
      success: false,
      error: parseError(e, "Unable to reach the server. Please try again later."),
    };
  }
}

export async function updateProjectStatus(
  id: string,
  status: string,
): Promise<ActionResult> {
  try {
    const res = await gatewayFetch(`/api/projects/${id}/status`, {
      method: "PATCH",
      body: JSON.stringify({ status }),
    });

    if (res.ok) {
      revalidatePath(`/projects/${id}`);
      revalidatePath("/projects");
      revalidatePath("/dashboard");
      return { success: true };
    }

    const errorBody = await res.json().catch(() => null);
    const errorMessage =
      errorBody?.detail ??
      errorBody?.error ??
      errorBody?.message ??
      "Failed to update project status.";
    return { success: false, error: errorMessage };
  } catch (e) {
    return {
      success: false,
      error: parseError(e, "Unable to reach the server. Please try again later."),
    };
  }
}

export async function deleteProject(id: string): Promise<ActionResult> {
  try {
    const res = await gatewayFetch(`/api/projects/${id}`, {
      method: "DELETE",
    });

    if (res.ok || res.status === 204) {
      revalidatePath("/projects");
      revalidatePath("/dashboard");
      return { success: true };
    }

    const errorBody = await res.json().catch(() => null);
    const errorMessage =
      errorBody?.detail ??
      errorBody?.error ??
      errorBody?.message ??
      "Failed to delete project.";
    return { success: false, error: errorMessage };
  } catch (e) {
    return {
      success: false,
      error: parseError(e, "Unable to reach the server. Please try again later."),
    };
  }
}

export async function addComment(
  projectId: string,
  content: string,
): Promise<ActionResult> {
  try {
    const res = await gatewayFetch(`/api/projects/${projectId}/comments`, {
      method: "POST",
      body: JSON.stringify({ content }),
    });

    if (res.ok) {
      revalidatePath(`/projects/${projectId}`);
      return { success: true };
    }

    const errorBody = await res.json().catch(() => null);
    const errorMessage =
      errorBody?.detail ??
      errorBody?.error ??
      errorBody?.message ??
      "Failed to add comment.";
    return { success: false, error: errorMessage };
  } catch (e) {
    return {
      success: false,
      error: parseError(e, "Unable to reach the server. Please try again later."),
    };
  }
}

export async function deleteComment(commentId: string, projectId: string): Promise<ActionResult> {
  try {
    const res = await gatewayFetch(`/api/comments/${commentId}`, {
      method: "DELETE",
    });

    if (res.ok || res.status === 204) {
      revalidatePath(`/projects/${projectId}`);
      return { success: true };
    }

    const errorBody = await res.json().catch(() => null);
    const errorMessage =
      errorBody?.detail ??
      errorBody?.error ??
      errorBody?.message ??
      "Failed to delete comment.";
    return { success: false, error: errorMessage };
  } catch (e) {
    return {
      success: false,
      error: parseError(e, "Unable to reach the server. Please try again later."),
    };
  }
}
