"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { FolderOpen, Loader2, AlertCircle } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { portalApi, PortalApiError } from "@/lib/portal-api";
import { clearPortalAuth } from "@/lib/portal-auth";
import { PortalProjectCard } from "@/components/portal/portal-project-card";

interface PortalProject {
  id: string;
  title: string;
  description: string | null;
  status: string;
  createdAt: string;
}

export default function PortalProjectListPage() {
  const router = useRouter();
  const [projects, setProjects] = useState<PortalProject[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function fetchProjects() {
      try {
        const data =
          await portalApi.get<PortalProject[]>("/api/portal/projects");
        setProjects(data);
      } catch (err) {
        if (err instanceof PortalApiError && err.status === 401) {
          clearPortalAuth();
          router.replace("/portal");
          return;
        }
        setError(
          err instanceof Error ? err.message : "Failed to load projects",
        );
      } finally {
        setIsLoading(false);
      }
    }
    fetchProjects();
  }, [router]);

  if (isLoading) {
    return (
      <div className="flex min-h-[40vh] items-center justify-center">
        <Loader2 className="size-8 animate-spin text-slate-400" />
      </div>
    );
  }

  if (error) {
    return (
      <div
        className="flex items-center gap-2 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-950 dark:text-red-300"
        role="alert"
      >
        <AlertCircle className="size-4 shrink-0" />
        {error}
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <h1 className="text-2xl font-semibold text-slate-950 dark:text-slate-50">
          Projects
        </h1>
        <Badge variant="neutral">{projects.length}</Badge>
      </div>

      {projects.length === 0 ? (
        <div className="flex flex-col items-center gap-2 py-12 text-slate-500">
          <FolderOpen className="size-8 opacity-40" />
          <p className="text-sm">No projects yet.</p>
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2">
          {projects.map((project) => (
            <PortalProjectCard
              key={project.id}
              id={project.id}
              title={project.title}
              description={project.description}
              status={project.status}
            />
          ))}
        </div>
      )}
    </div>
  );
}
