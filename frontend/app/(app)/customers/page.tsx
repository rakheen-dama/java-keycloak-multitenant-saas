import { listCustomers } from "./actions";
import { CustomersPageClient } from "./customers-page-client";

export const dynamic = "force-dynamic";

export default async function CustomersPage() {
  const customersResult = await listCustomers();

  if (!customersResult.success) {
    return (
      <div className="mx-auto max-w-6xl px-4 py-10 sm:px-6 lg:px-8">
        <div className="mb-8">
          <h1 className="font-display text-2xl font-bold tracking-tight text-foreground">
            Customers
          </h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Manage your customers.
          </p>
        </div>
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-800 dark:bg-red-950 dark:text-red-300">
          {customersResult.error ?? "Failed to load customers."}
        </div>
      </div>
    );
  }

  const customers = customersResult.data ?? [];

  return <CustomersPageClient customers={customers} />;
}
