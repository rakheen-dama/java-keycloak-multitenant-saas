"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { ArrowLeft } from "lucide-react";
import Link from "next/link";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import {
  archiveCustomer,
  type CustomerResponse,
} from "@/app/(app)/customers/actions";
import { EditCustomerDialog } from "@/components/customers/edit-customer-dialog";

interface CustomerDetailProps {
  customer: CustomerResponse;
}

const statusBadgeVariant: Record<string, "success" | "neutral"> = {
  ACTIVE: "success",
  ARCHIVED: "neutral",
};

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString("en-ZA", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

export function CustomerDetail({ customer }: CustomerDetailProps) {
  const [editOpen, setEditOpen] = useState(false);
  const [isArchiving, setIsArchiving] = useState(false);
  const router = useRouter();

  async function handleArchive() {
    setIsArchiving(true);
    try {
      const result = await archiveCustomer(customer.id);
      if (result.success) {
        toast.success(`Customer "${customer.name}" archived.`);
        router.refresh();
      } else {
        toast.error(result.error ?? "Failed to archive customer.");
      }
    } catch {
      toast.error("An unexpected error occurred.");
    } finally {
      setIsArchiving(false);
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="icon-sm" asChild>
          <Link href="/customers">
            <ArrowLeft className="size-4" />
            <span className="sr-only">Back to customers</span>
          </Link>
        </Button>
        <h1 className="font-display text-2xl font-bold tracking-tight text-foreground">
          {customer.name}
        </h1>
        <Badge variant={statusBadgeVariant[customer.status] ?? "neutral"}>
          {customer.status}
        </Badge>
      </div>

      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle>Customer Details</CardTitle>
            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setEditOpen(true)}
                data-testid="edit-customer-btn"
              >
                Edit
              </Button>

              {customer.status !== "ARCHIVED" && (
                <AlertDialog>
                  <AlertDialogTrigger asChild>
                    <Button
                      variant="outline"
                      size="sm"
                      data-testid="archive-customer-btn"
                    >
                      Archive
                    </Button>
                  </AlertDialogTrigger>
                  <AlertDialogContent>
                    <AlertDialogHeader>
                      <AlertDialogTitle>Archive Customer</AlertDialogTitle>
                      <AlertDialogDescription>
                        Are you sure you want to archive &quot;{customer.name}
                        &quot;? This action cannot be undone.
                      </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                      <AlertDialogCancel disabled={isArchiving}>
                        Cancel
                      </AlertDialogCancel>
                      <AlertDialogAction
                        onClick={handleArchive}
                        disabled={isArchiving}
                        className="bg-red-600 text-white hover:bg-red-600/90"
                      >
                        {isArchiving ? "Archiving..." : "Archive"}
                      </AlertDialogAction>
                    </AlertDialogFooter>
                  </AlertDialogContent>
                </AlertDialog>
              )}
            </div>
          </div>
        </CardHeader>
        <CardContent>
          <dl className="grid gap-4 sm:grid-cols-2">
            <div>
              <dt className="text-sm font-medium text-muted-foreground">
                Name
              </dt>
              <dd className="mt-1 text-sm text-foreground">{customer.name}</dd>
            </div>
            <div>
              <dt className="text-sm font-medium text-muted-foreground">
                Email
              </dt>
              <dd className="mt-1 text-sm text-foreground">{customer.email}</dd>
            </div>
            <div>
              <dt className="text-sm font-medium text-muted-foreground">
                Company
              </dt>
              <dd className="mt-1 text-sm text-foreground">
                {customer.company ?? "\u2014"}
              </dd>
            </div>
            <div>
              <dt className="text-sm font-medium text-muted-foreground">
                Status
              </dt>
              <dd className="mt-1">
                <Badge
                  variant={statusBadgeVariant[customer.status] ?? "neutral"}
                >
                  {customer.status}
                </Badge>
              </dd>
            </div>
            <div>
              <dt className="text-sm font-medium text-muted-foreground">
                Created
              </dt>
              <dd className="mt-1 text-sm text-foreground">
                {formatDate(customer.createdAt)}
              </dd>
            </div>
            <div>
              <dt className="text-sm font-medium text-muted-foreground">
                Last Updated
              </dt>
              <dd className="mt-1 text-sm text-foreground">
                {formatDate(customer.updatedAt)}
              </dd>
            </div>
          </dl>
        </CardContent>
      </Card>

      <EditCustomerDialog
        open={editOpen}
        onOpenChange={setEditOpen}
        customer={customer}
      />
    </div>
  );
}
