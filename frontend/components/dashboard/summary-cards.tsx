import { FolderKanban, Users, UserCog } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

interface SummaryCardsProps {
  totalProjects: number;
  totalCustomers: number;
  totalMembers: number;
}

export function SummaryCards({
  totalProjects,
  totalCustomers,
  totalMembers,
}: SummaryCardsProps) {
  const cards = [
    {
      title: "Total Projects",
      value: totalProjects,
      icon: FolderKanban,
      color: "text-teal-600",
    },
    {
      title: "Total Customers",
      value: totalCustomers,
      icon: Users,
      color: "text-blue-600",
    },
    {
      title: "Total Members",
      value: totalMembers,
      icon: UserCog,
      color: "text-amber-600",
    },
  ];

  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {cards.map((card) => (
        <Card key={card.title}>
          <CardHeader className="flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">
              {card.title}
            </CardTitle>
            <card.icon className={`size-5 ${card.color}`} />
          </CardHeader>
          <CardContent>
            <p className="font-mono text-3xl font-bold tabular-nums tracking-tight">
              {card.value}
            </p>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
