import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Customer Portal",
  description: "Access your projects and comments",
};

export default function PortalLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <>{children}</>;
}
