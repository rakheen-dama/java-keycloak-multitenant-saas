"use client";

import { Suspense, useEffect, useState, useTransition } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Loader2, AlertCircle } from "lucide-react";
import {
  portalApi,
  PortalApiError,
} from "@/lib/portal-api";
import { setPortalToken, setPortalCustomerName } from "@/lib/portal-auth";
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

  useEffect(() => {
    const token = searchParams.get("token");
    if (!token) {
      setError("No token provided.");
      return;
    }

    startTransition(async () => {
      try {
        const result = await portalApi.post<PortalAuthResponse>(
          "/api/portal/auth/exchange",
          { token },
        );
        setPortalToken(result.token);
        setPortalCustomerName(result.customerName);
        router.replace("/portal/projects");
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
            <Link href="/portal">Request a new link</Link>
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
