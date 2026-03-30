"use client";

import { LogOut, ChevronDown } from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Button } from "@/components/ui/button";

interface AppHeaderProps {
  orgName: string;
  userName: string;
  userEmail: string;
}

export function AppHeader({ orgName, userName, userEmail }: AppHeaderProps) {
  return (
    <header className="flex h-14 items-center justify-between border-b border-slate-200 bg-white px-6">
      <h2 className="font-display text-sm font-semibold text-slate-800">
        {orgName}
      </h2>

      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="ghost" size="sm" className="gap-2">
            <span className="text-sm text-slate-700">{userName}</span>
            <ChevronDown className="size-3.5 text-slate-400" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="w-56">
          <div className="px-2 py-1.5">
            <p className="text-sm font-medium">{userName}</p>
            <p className="text-xs text-muted-foreground">{userEmail}</p>
          </div>
          <DropdownMenuItem asChild>
            <a href="/logout" className="flex items-center gap-2">
              <LogOut className="size-4" />
              Sign out
            </a>
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </header>
  );
}
