export default function PublicLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <main
      id="main-content"
      className="flex min-h-dvh items-center justify-center px-4 py-12"
    >
      <div className="w-full max-w-md">{children}</div>
    </main>
  );
}
