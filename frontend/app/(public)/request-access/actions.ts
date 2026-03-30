"use server";

// In the template there is only one auth mode (Keycloak BFF).
// Server actions call the gateway via absolute URL (rewrites only apply in the browser).
const GATEWAY_URL = process.env.GATEWAY_URL ?? "http://localhost:8443";

interface AccessRequestData {
  email: string;
  fullName: string;
  organizationName: string;
  country: string;
  industry: string;
}

interface AccessRequestResult {
  success: boolean;
  message?: string;
  expiresInMinutes?: number;
  error?: string;
}

export async function submitAccessRequest(
  data: AccessRequestData,
): Promise<AccessRequestResult> {
  const { email, fullName, organizationName, country, industry } = data;

  if (!email?.trim()) return { success: false, error: "Email is required." };
  if (!fullName?.trim()) return { success: false, error: "Full name is required." };
  if (!organizationName?.trim()) return { success: false, error: "Organisation name is required." };
  if (!country?.trim()) return { success: false, error: "Country is required." };
  if (!industry?.trim()) return { success: false, error: "Industry is required." };

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
  if (!email?.trim()) return { success: false, error: "Email is required." };
  if (!otp?.trim()) return { success: false, error: "Verification code is required." };

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
