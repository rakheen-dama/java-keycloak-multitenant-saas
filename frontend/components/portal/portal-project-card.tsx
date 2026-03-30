import Link from "next/link";
import { FolderOpen } from "lucide-react";
import { Badge } from "@/components/ui/badge";

interface PortalProjectCardProps {
  id: string;
  title: string;
  description: string | null;
  status: string;
}

const statusBadgeVariant: Record<string, "success" | "warning" | "neutral"> = {
  ACTIVE: "success",
  COMPLETED: "warning",
  ARCHIVED: "neutral",
};

export function PortalProjectCard({
  id,
  title,
  description,
  status,
}: PortalProjectCardProps) {
  return (
    <Link
      href={`/portal/projects/${id}`}
      className="group rounded-lg border border-slate-200 bg-white p-5 transition-all hover:border-slate-300 hover:shadow-sm dark:border-slate-800 dark:bg-slate-900 dark:hover:border-slate-700"
    >
      <div className="flex items-start gap-3">
        <FolderOpen className="mt-0.5 size-5 text-slate-400" />
        <div className="min-w-0 flex-1">
          <h3 className="font-semibold text-slate-900 group-hover:text-slate-950 dark:text-slate-100">
            {title}
          </h3>
          {description ? (
            <p className="mt-1 line-clamp-2 text-sm text-slate-600 dark:text-slate-400">
              {description}
            </p>
          ) : (
            <p className="mt-1 text-sm italic text-slate-400">
              No description
            </p>
          )}
          <div className="mt-3">
            <Badge variant={statusBadgeVariant[status] ?? "neutral"}>
              {status}
            </Badge>
          </div>
        </div>
      </div>
    </Link>
  );
}
