"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { ArrowLeft, Share2 } from "lucide-react";
import Link from "next/link";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  updateProjectStatus,
  type ProjectResponse,
} from "@/app/(app)/projects/actions";
import { SharePortalDialog } from "@/components/projects/share-portal-dialog";

interface ProjectDetailProps {
  project: ProjectResponse;
  orgId: string;
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

export function ProjectDetail({ project, orgId }: ProjectDetailProps) {
  const [isUpdating, setIsUpdating] = useState(false);
  const [shareOpen, setShareOpen] = useState(false);
  const router = useRouter();

  async function handleStatusChange(newStatus: string) {
    setIsUpdating(true);
    try {
      const result = await updateProjectStatus(project.id, newStatus);
      if (result.success) {
        toast.success(`Project marked as ${newStatus.toLowerCase()}.`);
        router.refresh();
      } else {
        toast.error(result.error ?? "Failed to update status.");
      }
    } catch {
      toast.error("An unexpected error occurred.");
    } finally {
      setIsUpdating(false);
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="icon-sm" asChild>
          <Link href="/projects">
            <ArrowLeft className="size-4" />
            <span className="sr-only">Back to projects</span>
          </Link>
        </Button>
        <h1 className="font-display text-2xl font-bold tracking-tight text-foreground">
          {project.title}
        </h1>
        <Badge variant={statusBadgeVariant[project.status] ?? "neutral"}>
          {project.status}
        </Badge>
      </div>

      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle>Project Details</CardTitle>
            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setShareOpen(true)}
                data-testid="share-portal-link-btn"
              >
                <Share2 className="mr-2 size-4" />
                Share Portal Link
              </Button>
              <SharePortalDialog
                open={shareOpen}
                onOpenChange={setShareOpen}
                customerEmail={project.customerEmail ?? ""}
                orgId={orgId}
              />

              {project.status === "ACTIVE" && (
                <Button
                  variant="accent"
                  size="sm"
                  disabled={isUpdating}
                  onClick={() => handleStatusChange("COMPLETED")}
                  data-testid="mark-completed-btn"
                >
                  {isUpdating ? "Updating..." : "Mark Completed"}
                </Button>
              )}

              {project.status === "COMPLETED" && (
                <Button
                  variant="outline"
                  size="sm"
                  disabled={isUpdating}
                  onClick={() => handleStatusChange("ARCHIVED")}
                  data-testid="archive-btn"
                >
                  {isUpdating ? "Updating..." : "Archive"}
                </Button>
              )}
            </div>
          </div>
        </CardHeader>
        <CardContent>
          <dl className="grid gap-4 sm:grid-cols-2">
            <div>
              <dt className="text-sm font-medium text-muted-foreground">
                Customer
              </dt>
              <dd className="mt-1 text-sm text-foreground">
                {project.customerName ?? "—"}
              </dd>
            </div>
            <div>
              <dt className="text-sm font-medium text-muted-foreground">
                Created By
              </dt>
              <dd className="mt-1 text-sm text-foreground">
                {project.createdByName ?? "—"}
              </dd>
            </div>
            <div>
              <dt className="text-sm font-medium text-muted-foreground">
                Created
              </dt>
              <dd className="mt-1 text-sm text-foreground">
                {formatDate(project.createdAt)}
              </dd>
            </div>
            <div>
              <dt className="text-sm font-medium text-muted-foreground">
                Status
              </dt>
              <dd className="mt-1">
                <Badge
                  variant={statusBadgeVariant[project.status] ?? "neutral"}
                >
                  {project.status}
                </Badge>
              </dd>
            </div>
            {project.description && (
              <div className="sm:col-span-2">
                <dt className="text-sm font-medium text-muted-foreground">
                  Description
                </dt>
                <dd className="mt-1 text-sm text-foreground whitespace-pre-wrap">
                  {project.description}
                </dd>
              </div>
            )}
          </dl>
        </CardContent>
      </Card>
    </div>
  );
}
