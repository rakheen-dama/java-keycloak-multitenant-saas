"use client";

import { PortalAuthGuard } from "@/components/portal/portal-auth-guard";
import { PortalHeader } from "@/components/portal/portal-header";

export default function AuthenticatedPortalLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <PortalAuthGuard>
      <div className="min-h-screen bg-slate-50 dark:bg-slate-950">
        <PortalHeader />
        <main className="mx-auto max-w-5xl px-4 py-8 sm:px-6">
          {children}
        </main>
      </div>
    </PortalAuthGuard>
  );
}
