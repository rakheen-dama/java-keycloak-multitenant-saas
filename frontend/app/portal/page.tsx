"use client";

import { Suspense, useState, useTransition } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Mail, Loader2, CheckCircle2, AlertCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  portalApi,
  PortalApiError,
} from "@/lib/portal-api";
import { setPortalToken, setPortalCustomerName } from "@/lib/portal-auth";

type LoginStep = "email" | "sent" | "token";

interface MagicLinkResponse {
  message: string;
}

interface PortalAuthResponse {
  token: string;
  customerId: string;
  customerName: string;
}

function PortalLoginContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [step, setStep] = useState<LoginStep>("email");
  const [email, setEmail] = useState("");
  const [orgId, setOrgId] = useState(() => searchParams.get("orgId") ?? "");
  const [token, setToken] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  function handleRequestLink(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    startTransition(async () => {
      try {
        await portalApi.post<MagicLinkResponse>(
          "/api/portal/auth/request-link",
          { email, orgId },
        );
        setStep("sent");
      } catch (err) {
        setError(
          err instanceof PortalApiError
            ? err.message
            : "An unexpected error occurred. Please try again.",
        );
      }
    });
  }

  function handleExchangeToken(e: React.FormEvent) {
    e.preventDefault();
    setError(null);

    startTransition(async () => {
      try {
        let tokenValue = token.trim();
        // Support pasting full magic link URL
        try {
          const url = new URL(tokenValue);
          const paramToken = url.searchParams.get("token");
          const paramOrg = url.searchParams.get("orgId");
          if (paramToken) tokenValue = paramToken;
          if (paramOrg) setOrgId(paramOrg);
        } catch {
          // Not a URL — use raw token value as-is
        }

        const result = await portalApi.post<PortalAuthResponse>(
          "/api/portal/auth/exchange",
          { token: tokenValue },
        );
        setPortalToken(result.token);
        setPortalCustomerName(result.customerName);
        router.push("/portal/projects");
      } catch (err) {
        setError(
          err instanceof PortalApiError
            ? err.message
            : "Invalid or expired token. Please request a new link.",
        );
      }
    });
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 dark:bg-slate-950">
      <div className="w-full max-w-md space-y-8 px-6">
        <div className="text-center">
          <h1 className="text-3xl font-semibold text-slate-950 dark:text-slate-50">
            Customer Portal
          </h1>
          <p className="mt-2 text-slate-600 dark:text-slate-400">
            Access your projects and comments
          </p>
        </div>

        <div className="overflow-hidden rounded-xl border border-slate-200 bg-white p-8 dark:border-slate-800 dark:bg-slate-900">
          {step === "email" && (
            <form onSubmit={handleRequestLink} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="email">Email address</Label>
                <Input
                  id="email"
                  type="email"
                  placeholder="you@example.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  data-testid="portal-email-input"
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="orgId">Organization</Label>
                <Input
                  id="orgId"
                  type="text"
                  placeholder="your-org-slug"
                  value={orgId}
                  onChange={(e) => setOrgId(e.target.value)}
                  required
                  data-testid="portal-org-input"
                />
              </div>
              {error && (
                <div
                  className="flex items-center gap-2 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950 dark:text-red-300"
                  role="alert"
                >
                  <AlertCircle className="size-4 shrink-0" />
                  {error}
                </div>
              )}
              <Button
                type="submit"
                className="w-full"
                disabled={isPending}
                data-testid="portal-send-link-btn"
              >
                {isPending ? (
                  <Loader2 className="size-4 animate-spin" />
                ) : (
                  <Mail className="size-4" />
                )}
                Send Magic Link
              </Button>
            </form>
          )}

          {step === "sent" && (
            <form onSubmit={handleExchangeToken} className="space-y-4">
              <div className="flex flex-col items-center gap-3 text-center">
                <CheckCircle2 className="size-12 text-green-600 dark:text-green-400" />
                <h2 className="text-lg font-semibold text-slate-900 dark:text-slate-100">
                  Check your email
                </h2>
                <p className="text-sm text-slate-600 dark:text-slate-400">
                  A magic link has been sent. Once you receive it, paste the
                  token below.
                </p>
              </div>
              <div className="space-y-2">
                <Label htmlFor="token-exchange">Magic link or token</Label>
                <Input
                  id="token-exchange"
                  type="text"
                  placeholder="Paste magic link or token"
                  value={token}
                  onChange={(e) => setToken(e.target.value)}
                  data-testid="portal-token-input"
                />
              </div>
              {error && (
                <div
                  className="flex items-center gap-2 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950 dark:text-red-300"
                  role="alert"
                >
                  <AlertCircle className="size-4 shrink-0" />
                  {error}
                </div>
              )}
              <Button
                type="submit"
                className="w-full"
                disabled={isPending || !token.trim()}
                data-testid="portal-signin-btn"
              >
                {isPending ? (
                  <Loader2 className="size-4 animate-spin" />
                ) : null}
                Sign In
              </Button>
              <button
                type="button"
                className="w-full text-center text-sm text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200"
                onClick={() => {
                  setStep("email");
                  setError(null);
                  setToken("");
                }}
              >
                Request a new link
              </button>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}

export default function PortalLoginPage() {
  return (
    <Suspense fallback={null}>
      <PortalLoginContent />
    </Suspense>
  );
}
