import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  async rewrites() {
    return [
      { source: "/api/:path*", destination: "http://localhost:8443/api/:path*" },
      { source: "/bff/:path*", destination: "http://localhost:8443/bff/:path*" },
      { source: "/oauth2/:path*", destination: "http://localhost:8443/oauth2/:path*" },
      { source: "/login/:path*", destination: "http://localhost:8443/login/:path*" },
      { source: "/logout", destination: "http://localhost:8443/logout" },
    ];
  },
};

export default nextConfig;
