"use client";

import { Suspense, useEffect } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { setPortalToken, setPortalCustomerName } from "@/lib/portal-api";

function CallbackContent() {
  const router = useRouter();
  const searchParams = useSearchParams();

  useEffect(() => {
    const token = searchParams.get("t");
    const name = searchParams.get("n");

    if (token) {
      setPortalToken(token);
      if (name) setPortalCustomerName(name);
      router.replace("/projects");
    } else {
      router.replace("/");
    }
  }, [searchParams, router]);

  return null;
}

export default function CallbackPage() {
  return (
    <Suspense
      fallback={
        <div className="flex min-h-screen items-center justify-center">
          <div className="size-8 animate-spin rounded-full border-4 border-slate-300 border-t-teal-600" />
        </div>
      }
    >
      <CallbackContent />
    </Suspense>
  );
}
