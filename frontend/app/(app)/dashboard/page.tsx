import { listProjects } from "@/app/(app)/projects/actions";
import { SummaryCards } from "@/components/dashboard/summary-cards";
import { RecentProjects } from "@/components/dashboard/recent-projects";

export const dynamic = "force-dynamic";

const GATEWAY_URL = process.env.GATEWAY_URL ?? "http://localhost:8443";

interface MemberResponse {
  id: string;
}

interface CustomerResponse {
  id: string;
}

async function fetchMembers(): Promise<MemberResponse[]> {
  const { cookies } = await import("next/headers");
  const cookieStore = await cookies();
  const sessionCookie = cookieStore.get("SESSION");
  if (!sessionCookie) return [];

  try {
    const res = await fetch(`${GATEWAY_URL}/api/members`, {
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        Cookie: `SESSION=${sessionCookie.value}`,
      },
    });
    if (res.ok) return await res.json();
    return [];
  } catch {
    return [];
  }
}

async function fetchCustomers(): Promise<CustomerResponse[]> {
  const { cookies } = await import("next/headers");
  const cookieStore = await cookies();
  const sessionCookie = cookieStore.get("SESSION");
  if (!sessionCookie) return [];

  try {
    const res = await fetch(`${GATEWAY_URL}/api/customers`, {
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        Cookie: `SESSION=${sessionCookie.value}`,
      },
    });
    if (res.ok) return await res.json();
    return [];
  } catch {
    return [];
  }
}

export default async function DashboardPage() {
  const [projectsResult, members, customers] = await Promise.all([
    listProjects(),
    fetchMembers(),
    fetchCustomers(),
  ]);

  const projects = projectsResult.success ? (projectsResult.data ?? []) : [];

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
