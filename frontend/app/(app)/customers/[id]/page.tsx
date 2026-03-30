import { getCustomer, listProjectsByCustomer } from "../actions";
import { CustomerDetail } from "@/components/customers/customer-detail";
import Link from "next/link";
import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { FolderKanban } from "lucide-react";

export const dynamic = "force-dynamic";

interface CustomerDetailPageProps {
  params: Promise<{ id: string }>;
}

const projectStatusBadgeVariant: Record<
  string,
  "success" | "warning" | "neutral"
> = {
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

export default async function CustomerDetailPage({
  params,
}: CustomerDetailPageProps) {
  const { id } = await params;

  const [customerResult, projectsResult] = await Promise.all([
    getCustomer(id),
    listProjectsByCustomer(id),
  ]);

  if (!customerResult.success || !customerResult.data) {
    return (
      <div className="mx-auto max-w-6xl px-4 py-10 sm:px-6 lg:px-8">
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-800 dark:bg-red-950 dark:text-red-300">
          {customerResult.error ?? "Failed to load customer."}
        </div>
      </div>
    );
  }

  const customer = customerResult.data;
  const projects = projectsResult.success ? (projectsResult.data ?? []) : [];

  const sortedProjects = [...projects].sort(
    (a, b) =>
      new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
  );

  return (
    <div className="mx-auto max-w-6xl px-4 py-10 sm:px-6 lg:px-8">
      <div className="space-y-8">
        <CustomerDetail customer={customer} />

        <div>
          <h2 className="font-display text-lg font-semibold tracking-tight text-foreground mb-4">
            Linked Projects
          </h2>
          {sortedProjects.length === 0 ? (
            <div className="flex flex-col items-center gap-2 py-16 text-muted-foreground">
              <FolderKanban className="size-8 opacity-40" />
              <p className="text-sm">
                No projects linked to this customer yet.
              </p>
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Title</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Created By</TableHead>
                  <TableHead>Created</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {sortedProjects.map((project) => (
                  <TableRow key={project.id}>
                    <TableCell className="font-medium">
                      <Link
                        href={`/projects/${project.id}`}
                        className="text-teal-600 hover:underline"
                      >
                        {project.title}
                      </Link>
                    </TableCell>
                    <TableCell>
                      <Badge
                        variant={
                          projectStatusBadgeVariant[project.status] ?? "neutral"
                        }
                      >
                        {project.status}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {project.createdBy.displayName}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {formatDate(project.createdAt)}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </div>
      </div>
    </div>
  );
}
