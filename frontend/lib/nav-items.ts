import {
  LayoutDashboard,
  FolderKanban,
  Users,
  UserCog,
  Settings,
  type LucideIcon,
} from "lucide-react";

export interface NavItem {
  label: string;
  href: string;
  icon: LucideIcon;
}

export const navItems: NavItem[] = [
  { label: "Dashboard", href: "/dashboard", icon: LayoutDashboard },
  { label: "Projects", href: "/projects", icon: FolderKanban },
  { label: "Customers", href: "/customers", icon: Users },
  { label: "Members", href: "/members", icon: UserCog },
  { label: "Settings", href: "/settings", icon: Settings },
];
