"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, Loader2, AlertCircle, Calendar } from "lucide-react";
import { portalApi, PortalApiError } from "@/lib/portal-api";
import { clearPortalAuth } from "@/lib/portal-auth";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { PortalCommentSection } from "@/components/portal/portal-comment-section";

interface PortalProject {
  id: string;
  title: string;
  description: string | null;
  status: string;
  createdAt: string;
}

interface PortalComment {
  id: string;
  projectId: string;
  content: string;
  authorType: "MEMBER" | "CUSTOMER";
  authorId: string;
  authorName: string;
  createdAt: string;
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

export default function PortalProjectDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const [project, setProject] = useState<PortalProject | null>(null);
  const [comments, setComments] = useState<PortalComment[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function fetchData() {
      try {
        const [projectData, commentsData] = await Promise.all([
          portalApi.get<PortalProject>(
            `/api/portal/projects/${params.id}`,
          ),
          portalApi.get<PortalComment[]>(
            `/api/portal/projects/${params.id}/comments`,
          ),
        ]);
        setProject(projectData);
        setComments(commentsData);
      } catch (err) {
        if (err instanceof PortalApiError && err.status === 401) {
          clearPortalAuth();
          router.replace("/portal");
          return;
        }
        setError(
          err instanceof Error ? err.message : "Failed to load project",
        );
      } finally {
        setIsLoading(false);
      }
    }
    fetchData();
  }, [params.id, router]);

  if (isLoading) {
    return (
      <div className="flex min-h-[40vh] items-center justify-center">
        <Loader2 className="size-8 animate-spin text-slate-400" />
      </div>
    );
  }

  if (error || !project) {
    return (
      <div className="space-y-4">
        <Link
          href="/portal/projects"
          className="inline-flex items-center text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ArrowLeft className="mr-1.5 size-4" />
          Back to Projects
        </Link>
        <div
          className="flex items-center gap-2 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-950 dark:text-red-300"
          role="alert"
        >
          <AlertCircle className="size-4 shrink-0" />
          {error ?? "Project not found."}
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <Link
        href="/portal/projects"
        className="inline-flex items-center text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
      >
        <ArrowLeft className="mr-1.5 size-4" />
        Back to Projects
      </Link>

      <div>
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-semibold text-slate-950 dark:text-slate-50">
            {project.title}
          </h1>
          <Badge variant={statusBadgeVariant[project.status] ?? "neutral"}>
            {project.status}
          </Badge>
        </div>
        {project.description && (
          <p className="mt-2 text-slate-600 dark:text-slate-400">
            {project.description}
          </p>
        )}
        <div className="mt-2 flex items-center gap-1.5 text-xs text-slate-500">
          <Calendar className="size-3.5" />
          <span>Created {formatDate(project.createdAt)}</span>
        </div>
      </div>

      <Card>
        <CardContent className="pt-6">
          <PortalCommentSection
            projectId={project.id}
            initialComments={comments}
          />
        </CardContent>
      </Card>
    </div>
  );
}
