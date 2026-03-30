"use client";

import { Suspense, useEffect, useRef } from "react";
import { useSearchParams } from "next/navigation";
import { setPortalToken, setPortalCustomerName } from "@/lib/portal-api";

function CallbackContent() {
  const searchParams = useSearchParams();
  const processed = useRef(false);

  useEffect(() => {
    if (processed.current) return;
    processed.current = true;

    const token = searchParams.get("t");
    const name = searchParams.get("n");

    if (token) {
      setPortalToken(token);
      if (name) setPortalCustomerName(name);
      // Full page navigation (not client-side router.replace) so the
      // auth guard reads the token from localStorage on a fresh mount
      window.location.href = "/projects";
    } else {
      window.location.href = "/";
    }
  }, [searchParams]);

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
