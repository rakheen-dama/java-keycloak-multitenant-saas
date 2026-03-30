import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  async rewrites() {
    const backendUrl =
      process.env.BACKEND_URL || "http://localhost:8080";
    return [
      {
        source: "/api/portal/:path*",
        destination: `${backendUrl}/api/portal/:path*`,
      },
      {
        source: "/portal/:path*",
        destination: `${backendUrl}/portal/:path*`,
      },
    ];
  },
};

export default nextConfig;
