"use client";

import { useState } from "react";
import { Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { CustomerList } from "@/components/customers/customer-list";
import { CreateCustomerDialog } from "@/components/customers/create-customer-dialog";
import type { CustomerResponse } from "@/app/(app)/customers/actions";

interface CustomersPageClientProps {
  customers: CustomerResponse[];
}

export function CustomersPageClient({
  customers,
}: CustomersPageClientProps) {
  const [createOpen, setCreateOpen] = useState(false);

  return (
    <div className="mx-auto max-w-6xl px-4 py-10 sm:px-6 lg:px-8">
      <div className="mb-8 flex items-center justify-between">
        <div>
          <h1 className="font-display text-2xl font-bold tracking-tight text-foreground">
            Customers
          </h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Manage your customers.
          </p>
        </div>
        <Button
          variant="accent"
          onClick={() => setCreateOpen(true)}
          data-testid="new-customer-btn"
        >
          <Plus className="mr-2 size-4" />
          New Customer
        </Button>
      </div>

      <CustomerList customers={customers} />

      <CreateCustomerDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
      />
    </div>
  );
}
