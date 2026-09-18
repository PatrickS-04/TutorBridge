import express from "express";
import cors from "cors";
import dotenv from "dotenv";
import mongoose from "mongoose";
import { registerRoutes } from "./routes";

dotenv.config();

async function main() {
  const uri = process.env.MONGODB_URI;
  if (!uri) throw new Error("MONGODB_URI is not set");
  await mongoose.connect(uri);
  console.log("MongoDB connected");

  const app = express();
  app.use(cors());
  app.use(express.json());

  registerRoutes(app);

  // Conventional error body for anything that reaches here unhandled.
  app.use((err: any, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
    console.error(err);
    res.status(500).json({ error: { code: "INTERNAL_ERROR", message: "Something went wrong", field: null } });
  });

  const port = process.env.PORT || 5000;
  app.listen(port, () => console.log(`TutorBridge API listening on port ${port}`));
}

main().catch((err) => {
  console.error("Failed to start server:", err);
  process.exit(1);
});