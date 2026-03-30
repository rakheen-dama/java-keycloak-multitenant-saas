import Link from "next/link";
import { FolderKanban } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import type { ProjectResponse } from "@/app/(app)/projects/actions";

interface RecentProjectsProps {
  projects: ProjectResponse[];
}

const statusBadgeVariant: Record<string, "success" | "warning" | "neutral"> = {
  ACTIVE: "success",
  COMPLETED: "warning",
  ARCHIVED: "neutral",
};

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString("en-ZA", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

export function RecentProjects({ projects }: RecentProjectsProps) {
  if (projects.length === 0) {
    return (
      <div className="flex flex-col items-center gap-2 py-12 text-muted-foreground">
        <FolderKanban className="size-8 opacity-40" />
        <p className="text-sm">No projects yet.</p>
      </div>
    );
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Title</TableHead>
          <TableHead>Customer</TableHead>
          <TableHead>Status</TableHead>
          <TableHead>Created</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {projects.map((project) => (
          <TableRow key={project.id}>
            <TableCell className="font-medium">
              <Link
                href={`/projects/${project.id}`}
                className="text-teal-600 hover:underline"
              >
                {project.title}
              </Link>
            </TableCell>
            <TableCell className="text-muted-foreground">
              {project.customerName ?? "—"}
            </TableCell>
            <TableCell>
              <Badge variant={statusBadgeVariant[project.status] ?? "neutral"}>
                {project.status}
              </Badge>
            </TableCell>
            <TableCell className="text-muted-foreground">
              {formatDate(project.createdAt)}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
