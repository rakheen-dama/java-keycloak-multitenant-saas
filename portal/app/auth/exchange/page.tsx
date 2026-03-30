"use client";

import { Suspense, useEffect, useRef, useState, useTransition } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Loader2, AlertCircle } from "lucide-react";
import {
  setPortalToken,
  setPortalCustomerName,
  PortalApiError,
} from "@/lib/portal-api";
import Link from "next/link";
import { Button } from "@/components/ui/button";

interface PortalAuthResponse {
  token: string;
  customerId: string;
  customerName: string;
}

function ExchangeContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();
  const exchangedRef = useRef(false);

  useEffect(() => {
    // Guard against React strict mode double-invocation in dev
    if (exchangedRef.current) return;
    exchangedRef.current = true;

    const token = searchParams.get("token");
    const orgId = searchParams.get("org");
    if (!token || !orgId) {
      setError("Invalid link - missing token or organization.");
      return;
    }

    startTransition(async () => {
      try {
        const res = await fetch("/api/portal/auth/exchange", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ token, orgId }),
        });
        if (!res.ok) {
          const body = await res.json().catch(() => null);
          throw new PortalApiError(
            res.status,
            body?.detail ?? body?.message ?? "Exchange failed",
          );
        }
        const result: PortalAuthResponse = await res.json();
        setPortalToken(result.token);
        setPortalCustomerName(result.customerName);
        router.replace("/projects");
      } catch (err) {
        setError(
          err instanceof PortalApiError
            ? err.message
            : "Invalid or expired link. Please request a new one.",
        );
      }
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (isPending) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <Loader2 className="size-8 animate-spin text-slate-400" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50 dark:bg-slate-950">
        <div className="w-full max-w-md space-y-4 px-6 text-center">
          <AlertCircle className="mx-auto size-12 text-red-500" />
          <h1 className="text-lg font-semibold text-slate-900 dark:text-slate-100">
            Link expired or invalid
          </h1>
          <p className="text-sm text-slate-600 dark:text-slate-400">{error}</p>
          <Button asChild variant="outline">
            <Link href="/">Request a new link</Link>
          </Button>
        </div>
      </div>
    );
  }

  return null;
}

export default function ExchangePage() {
  return (
    <Suspense
      fallback={
        <div className="flex min-h-screen items-center justify-center">
          <Loader2 className="size-8 animate-spin text-slate-400" />
        </div>
      }
    >
      <ExchangeContent />
    </Suspense>
  );
}
