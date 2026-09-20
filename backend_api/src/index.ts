import express from "express";
import cors from "cors";
import dotenv from "dotenv";
import mongoose from "mongoose";
import swaggerUi from "swagger-ui-express";
import { registerRoutes } from "./routes";
import { openapiSpec } from "./openapi";
import { errorHandler } from "./middleware/errors";
import { purgeScheduledDeletions } from "./utils/purgeScheduledDeletions";

dotenv.config();

async function main() {
  const uri = process.env.MONGODB_URI;
  if (!uri) throw new Error("MONGODB_URI is not set");
  await mongoose.connect(uri);
  console.log("MongoDB connected");

  const app = express();
  app.use(cors());
  app.use(express.json());

  // Browsable docs, plus the raw spec for client generators.
  app.use("/docs", swaggerUi.serve, swaggerUi.setup(openapiSpec, { customSiteTitle: "TutorBridge API" }));
  app.get("/openapi.json", (_req, res) => res.json(openapiSpec));

  registerRoutes(app);

  // Profiles whose deletion grace period has run out. Hourly is plenty for a
  // 14-day window, and the pass on boot catches anything that came due while
  // the server was off.
  // ponytail: in-process timer, one instance - move it to a real scheduler if
  // the API is ever run horizontally.
  const purge = () =>
    purgeScheduledDeletions().then(
      (count) => {
        if (count) console.log(`Purged ${count} profile(s) past the deletion grace period`);
      },
      (error) => console.error("Scheduled deletion purge failed:", error)
    );
  purge();
  setInterval(purge, 60 * 60 * 1000).unref();

  // Conventional error body for anything that reaches here unhandled.
  app.use(errorHandler);

  const port = process.env.PORT || 5000;
  app.listen(port, () => console.log(`TutorBridge API listening on port ${port}`));
}

main().catch((err) => {
  console.error("Failed to start server:", err);
  process.exit(1);
});