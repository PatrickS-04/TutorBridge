import { Request, Response, NextFunction } from "express";
import jwt from "jsonwebtoken";
import { UserRole } from "../models";

export interface AuthClaims {
  userId: string;
  institutionId: string;
  role: UserRole;
}

export interface AuthRequest extends Request {
  auth?: AuthClaims;
}

const ACCESS_TOKEN_SECRET = process.env.ACCESS_TOKEN_SECRET || "dev-secret-change-me";

export function authenticate(req: AuthRequest, res: Response, next: NextFunction) {
  const header = req.headers.authorization;
  if (!header?.startsWith("Bearer ")) {
    return res.status(401).json({
      error: { code: "UNAUTHENTICATED", message: "Missing bearer token", field: null },
    });
  }

  const token = header.slice("Bearer ".length);
  try {
    const payload = jwt.verify(token, ACCESS_TOKEN_SECRET) as AuthClaims;
    req.auth = {
      userId: payload.userId,
      institutionId: payload.institutionId,
      role: payload.role,
    };
    next();
  } catch {
    return res.status(401).json({
      error: { code: "INVALID_TOKEN", message: "Access token is invalid or expired", field: null },
    });
  }
}

export function requireRole(...roles: UserRole[]) {
  return (req: AuthRequest, res: Response, next: NextFunction) => {
    if (!req.auth || !roles.includes(req.auth.role)) {
      return res.status(403).json({
        error: { code: "FORBIDDEN", message: "Role not permitted for this action", field: null },
      });
    }
    next();
  };
}

export function signAccessToken(claims: AuthClaims): string {
  return jwt.sign(claims, ACCESS_TOKEN_SECRET, { expiresIn: "15m" });
}
