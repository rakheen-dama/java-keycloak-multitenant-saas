"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { MoreHorizontal, Users, Archive } from "lucide-react";
import { toast } from "sonner";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import type { CustomerResponse } from "@/app/(app)/customers/actions";
import { archiveCustomer } from "@/app/(app)/customers/actions";

interface CustomerListProps {
  customers: CustomerResponse[];
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

export function CustomerList({ customers }: CustomerListProps) {
  const [archiveTarget, setArchiveTarget] = useState<CustomerResponse | null>(
    null,
  );
  const [isArchiving, setIsArchiving] = useState(false);
  const router = useRouter();

  async function handleArchive() {
    if (!archiveTarget) return;
    setIsArchiving(true);
    try {
      const result = await archiveCustomer(archiveTarget.id);
      if (result.success) {
        toast.success(`Customer "${archiveTarget.name}" archived.`);
        setArchiveTarget(null);
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

  const sorted = [...customers].sort(
    (a, b) =>
      new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
  );

  if (sorted.length === 0) {
    return (
      <div className="flex flex-col items-center gap-2 py-16 text-muted-foreground">
        <Users className="size-8 opacity-40" />
        <p className="text-sm">No customers yet. Create your first customer.</p>
      </div>
    );
  }

  return (
    <>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Name</TableHead>
            <TableHead>Email</TableHead>
            <TableHead>Company</TableHead>
            <TableHead>Status</TableHead>
            <TableHead>Created</TableHead>
            <TableHead className="w-12">
              <span className="sr-only">Actions</span>
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {sorted.map((customer) => (
            <TableRow key={customer.id}>
              <TableCell className="font-medium">
                <Link
                  href={`/customers/${customer.id}`}
                  className="text-teal-600 hover:underline"
                >
                  {customer.name}
                </Link>
              </TableCell>
              <TableCell className="text-muted-foreground">
                {customer.email}
              </TableCell>
              <TableCell className="text-muted-foreground">
                {customer.company ?? "\u2014"}
              </TableCell>
              <TableCell>
                <Badge variant={statusBadgeVariant[customer.status] ?? "neutral"}>
                  {customer.status}
                </Badge>
              </TableCell>
              <TableCell className="text-muted-foreground">
                {formatDate(customer.createdAt)}
              </TableCell>
              <TableCell>
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <Button variant="ghost" size="sm" className="size-8 p-0">
                      <MoreHorizontal className="size-4" />
                      <span className="sr-only">Actions</span>
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <DropdownMenuItem asChild>
                      <Link href={`/customers/${customer.id}`}>
                        View Details
                      </Link>
                    </DropdownMenuItem>
                    {customer.status !== "ARCHIVED" && (
                      <DropdownMenuItem
                        className="text-destructive"
                        onSelect={() => setArchiveTarget(customer)}
                      >
                        <Archive className="mr-2 size-4" />
                        Archive
                      </DropdownMenuItem>
                    )}
                  </DropdownMenuContent>
                </DropdownMenu>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      <AlertDialog
        open={!!archiveTarget}
        onOpenChange={(open) => {
          if (!open) setArchiveTarget(null);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Archive Customer</AlertDialogTitle>
            <AlertDialogDescription>
              Are you sure you want to archive &quot;{archiveTarget?.name}
              &quot;? This action cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isArchiving}>Cancel</AlertDialogCancel>
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
    </>
  );
}
