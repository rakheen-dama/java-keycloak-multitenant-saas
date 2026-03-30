"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { LogOut } from "lucide-react";
import { cn } from "@/lib/utils";
import { navItems } from "@/lib/nav-items";
import { Separator } from "@/components/ui/separator";

interface AppSidebarProps {
  orgSlug: string;
  userName: string;
  userEmail: string;
}

export function AppSidebar({ orgSlug, userName, userEmail }: AppSidebarProps) {
  const pathname = usePathname();

  return (
    <aside className="flex h-screen w-60 flex-col bg-slate-950 text-slate-300">
      {/* Logo / App Name */}
      <div className="px-5 pt-6 pb-4">
        <h1 className="font-display text-lg font-bold tracking-tight text-white">
          Starter
        </h1>
        <p className="mt-1 font-mono text-xs tracking-wider text-teal-400 uppercase">
          {orgSlug}
        </p>
      </div>

      <Separator className="bg-slate-800" />

      {/* Navigation */}
      <nav className="flex-1 space-y-1 px-3 py-4">
        {navItems.map((item) => {
          const isActive =
            pathname === item.href || pathname.startsWith(`${item.href}/`);
          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "relative flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors",
                isActive
                  ? "bg-slate-800/70 text-white"
                  : "text-slate-400 hover:bg-slate-800/50 hover:text-slate-200",
              )}
            >
              {isActive && (
                <span className="absolute left-0 top-1/2 h-5 w-0.5 -translate-y-1/2 rounded-r bg-teal-500" />
              )}
              <item.icon className="size-4 shrink-0" />
              {item.label}
            </Link>
          );
        })}
      </nav>

      <Separator className="bg-slate-800" />

      {/* User footer */}
      <div className="px-4 py-4">
        <div className="mb-3">
          <p className="truncate text-sm font-medium text-slate-200">
            {userName}
          </p>
          <p className="truncate text-xs text-slate-500">{userEmail}</p>
        </div>
        <a
          href="/logout"
          className="flex items-center gap-2 rounded-md px-2 py-1.5 text-xs text-slate-500 transition-colors hover:bg-slate-800/50 hover:text-slate-300"
        >
          <LogOut className="size-3.5" />
          Sign out
        </a>
      </div>
    </aside>
  );
}
