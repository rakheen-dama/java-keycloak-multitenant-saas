"use client";

import { useState } from "react";
import { Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ProjectList } from "@/components/projects/project-list";
import { CreateProjectDialog } from "@/components/projects/create-project-dialog";
import type {
  ProjectResponse,
  CustomerResponse,
} from "@/app/(app)/projects/actions";

interface ProjectsPageClientProps {
  projects: ProjectResponse[];
  customers: CustomerResponse[];
}

export function ProjectsPageClient({
  projects,
  customers,
}: ProjectsPageClientProps) {
  const [createOpen, setCreateOpen] = useState(false);

  return (
    <div className="mx-auto max-w-6xl px-4 py-10 sm:px-6 lg:px-8">
      <div className="mb-8 flex items-center justify-between">
        <div>
          <h1 className="font-display text-2xl font-bold tracking-tight text-foreground">
            Projects
          </h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Manage your projects.
          </p>
        </div>
        <Button
          variant="accent"
          onClick={() => setCreateOpen(true)}
          data-testid="new-project-btn"
        >
          <Plus className="mr-2 size-4" />
          New Project
        </Button>
      </div>

      <ProjectList projects={projects} />

      <CreateProjectDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        customers={customers}
      />
    </div>
  );
}
