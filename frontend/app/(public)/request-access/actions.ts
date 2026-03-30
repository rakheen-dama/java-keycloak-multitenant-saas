"use server";

import {
  accessRequestSchema,
  otpVerifySchema,
  type AccessRequestFormData,
} from "@/lib/schemas/access-request";

// In the template there is only one auth mode (Keycloak BFF).
// Server actions call the gateway via absolute URL (rewrites only apply in the browser).
const GATEWAY_URL = process.env.GATEWAY_URL ?? "http://localhost:8443";

interface AccessRequestResult {
  success: boolean;
  message?: string;
  expiresInMinutes?: number;
  error?: string;
}

export async function submitAccessRequest(
  data: AccessRequestFormData,
): Promise<AccessRequestResult> {
  const parsed = accessRequestSchema.safeParse(data);
  if (!parsed.success) {
    const firstError = parsed.error.issues[0]?.message ?? "Invalid input.";
    return { success: false, error: firstError };
  }

  const { email, fullName, organizationName, country, industry } = parsed.data;

  try {
    const response = await fetch(`${GATEWAY_URL}/api/access-requests`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        email: email.trim(),
        fullName: fullName.trim(),
        organizationName: organizationName.trim(),
        country: country.trim(),
        industry: industry.trim(),
      }),
    });

    if (response.ok) {
      const body = await response.json();
      return {
        success: true,
        message: body.message,
        expiresInMinutes: body.expiresInMinutes,
      };
    }

    const errorBody = await response.json().catch(() => null);
    const errorMessage = errorBody?.detail ?? errorBody?.error ?? errorBody?.message ?? "Something went wrong.";
    return { success: false, error: errorMessage };
  } catch {
    return { success: false, error: "Unable to reach the server. Please try again later." };
  }
}

interface VerifyOtpResult {
  success: boolean;
  message?: string;
  error?: string;
}

export async function verifyAccessRequestOtp(
  email: string,
  otp: string,
): Promise<VerifyOtpResult> {
  const parsed = otpVerifySchema.safeParse({ otp });
  if (!parsed.success) {
    const firstError = parsed.error.issues[0]?.message ?? "Invalid verification code.";
    return { success: false, error: firstError };
  }
  if (!email?.trim()) return { success: false, error: "Email is required." };

  try {
    const response = await fetch(`${GATEWAY_URL}/api/access-requests/verify`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: email.trim(), otp: otp.trim() }),
    });

    if (response.ok) {
      const body = await response.json();
      return { success: true, message: body.message };
    }

    const errorBody = await response.json().catch(() => null);
    const errorMessage = errorBody?.detail ?? errorBody?.error ?? errorBody?.message ?? "Something went wrong.";
    return { success: false, error: errorMessage };
  } catch {
    return { success: false, error: "Unable to reach the server. Please try again later." };
  }
}
