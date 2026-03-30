"use client";

import { useRouter } from "next/navigation";
import { LogOut } from "lucide-react";
import { Button } from "@/components/ui/button";
import { clearPortalAuth, getPortalCustomerName } from "@/lib/portal-auth";
import Link from "next/link";

export function PortalHeader() {
  const router = useRouter();
  const customerName = getPortalCustomerName();

  const handleLogout = () => {
    clearPortalAuth();
    router.push("/portal");
  };

  return (
    <header className="border-b border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950">
      <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-4 sm:px-6">
        <Link
          href="/portal/projects"
          className="font-semibold text-slate-950 dark:text-slate-50"
        >
          Customer Portal
        </Link>
        <div className="flex items-center gap-3">
          {customerName && (
            <span className="hidden text-sm text-slate-600 sm:block dark:text-slate-400">
              {customerName}
            </span>
          )}
          <Button variant="ghost" size="sm" onClick={handleLogout}>
            <LogOut className="size-4" />
            <span className="hidden sm:inline">Sign out</span>
          </Button>
        </div>
      </div>
    </header>
  );
}
