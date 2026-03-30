import { listAccessRequests } from "./actions";
import { AccessRequestTable } from "@/components/platform-admin/access-request-table";

export const dynamic = "force-dynamic";

export default async function AccessRequestsPage() {
  const result = await listAccessRequests();

  return (
    <div className="mx-auto max-w-6xl px-4 py-10 sm:px-6 lg:px-8">
      <div className="mb-8">
        <h1 className="font-display text-2xl font-bold tracking-tight text-foreground">
          Access Requests
        </h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Review and manage organisation access requests.
        </p>
      </div>

      {!result.success ? (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-800 dark:bg-red-950 dark:text-red-300">
          {result.error ?? "Failed to load access requests."}
        </div>
      ) : (
        <AccessRequestTable requests={result.data ?? []} />
      )}
    </div>
  );
}
