export interface PlatformSession {
  id: number;
  email: string;
}

export interface PlatformAuthResponse {
  token: string;
  expiresAt: string;
  admin: PlatformSession;
}

export interface PlatformLoginRequest {
  email: string;
  password: string;
}
