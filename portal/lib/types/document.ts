// ---- Portal types matching backend PortalController responses ----

export interface PortalProject {
  id: string;
  title: string;
  description: string | null;
  status: string;
  customerId: string;
  createdAt: string;
  updatedAt: string;
}

export interface PortalComment {
  id: string;
  projectId: string;
  content: string;
  authorType: string;
  authorId: string;
  authorName: string;
  createdAt: string;
  updatedAt: string;
}

export interface PortalAuthResponse {
  token: string;
  customerId: string;
  customerName: string;
}

export interface MagicLinkResponse {
  message: string;
}
