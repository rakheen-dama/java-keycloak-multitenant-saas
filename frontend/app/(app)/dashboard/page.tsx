import { listProjects, listMembers, listCustomers } from "@/app/(app)/projects/actions";
import { SummaryCards } from "@/components/dashboard/summary-cards";
import { RecentProjects } from "@/components/dashboard/recent-projects";

export const dynamic = "force-dynamic";

export default async function DashboardPage() {
  const [projectsResult, membersResult, customersResult] = await Promise.all([
    listProjects(),
    listMembers(),
    listCustomers(),
  ]);

  if (!projectsResult.success || !membersResult.success || !customersResult.success) {
    const error =
      projectsResult.error ?? membersResult.error ?? customersResult.error ?? "Failed to load dashboard.";
    return (
      <div className="mx-auto max-w-6xl px-4 py-10 sm:px-6 lg:px-8">
        <p className="text-sm text-destructive">{error}</p>
      </div>
    );
  }

  const projects = projectsResult.data ?? [];
  const members = membersResult.data ?? [];
  const customers = customersResult.data ?? [];

  const recentProjects = [...projects]
    .sort(
      (a, b) =>
        new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
    )
    .slice(0, 5);

  return (
    <div className="mx-auto max-w-6xl px-4 py-10 sm:px-6 lg:px-8">
      <div className="mb-8">
        <h1 className="font-display text-2xl font-bold tracking-tight text-foreground">
          Dashboard
        </h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Overview of your organisation.
        </p>
      </div>

      <div className="space-y-8">
        <SummaryCards
          totalProjects={projects.length}
          totalCustomers={customers.length}
          totalMembers={members.length}
        />

        <div>
          <h2 className="mb-4 font-display text-lg font-semibold tracking-tight text-foreground">
            Recent Projects
          </h2>
          <RecentProjects projects={recentProjects} />
        </div>
      </div>
    </div>
  );
}
