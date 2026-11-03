import express from "express";
import cors from "cors";
import dotenv from "dotenv";
import mongoose from "mongoose";
import swaggerUi from "swagger-ui-express";
import { registerRoutes } from "./routes";
import { openapiSpec } from "./openapi";
import { errorHandler } from "./middleware/errors";
import { purgeScheduledDeletions } from "./utils/purgeScheduledDeletions";
import { completePastSessions } from "./utils/completePastSessions";

// Reference: Motte, S. 2026. dotenv (Version 17.4.2). [Computer program].
//   Available at: https://github.com/motdotla/dotenv [Accessed 21 September 2026].
dotenv.config();

async function main() {
  // References:
  // MongoDB. n.d. MongoDB Atlas: multi-cloud database service. [Online].
  //   Available at: https://www.mongodb.com/docs/atlas/ [Accessed 21 September 2026].
  // MongoDB. n.d. Connection strings. [Online].
  //   Available at: https://www.mongodb.com/docs/manual/reference/connection-string/ [Accessed 21 September 2026].
  const uri = process.env.MONGODB_URI;
  if (!uri) throw new Error("MONGODB_URI is not set");
  await mongoose.connect(uri);
  console.log("MongoDB connected");

  const app = express();
  // Reference: Express.js. 2026. cors (Version 2.8.6). [Computer program].
  //   Available at: https://github.com/expressjs/cors [Accessed 21 September 2026].
  app.use(cors());
  app.use(express.json());

  // Browsable docs, plus the raw spec for client generators.
  // Reference: Scott, S. 2024. swagger-ui-express (Version 5.0.1). [Computer program].
  //   Available at: https://github.com/scottie1984/swagger-ui-express [Accessed 21 September 2026].
  app.use("/docs", swaggerUi.serve, swaggerUi.setup(openapiSpec, { customSiteTitle: "TutorBridge API" }));
  app.get("/openapi.json", (_req, res) => res.json(openapiSpec));

  // Public and needs no token: the app calls it on start to wake a sleeping free host.
  app.get("/health", (_req, res) => {
    const database = mongoose.connection.readyState === 1 ? "connected" : "disconnected";
    res.status(database === "connected" ? 200 : 503).json({ status: database === "connected" ? "ok" : "degraded", database });
  });

  registerRoutes(app);

  // Housekeeping no request can trigger. Hourly suits both - a 14-day
  // deletion window, and a claim nobody reads until month end - and the pass
  // on boot catches whatever came due while the server was off.
  // ponytail: in-process timer, one instance - move it to a real scheduler if
  // the API is ever run horizontally.
  const hourly = (job: () => Promise<number>, done: (count: number) => string) => {
    const run = () =>
      job().then(
        (count) => {
          if (count) console.log(done(count));
        },
        (error) => console.error(`${job.name} failed:`, error)
      );
    run();
    setInterval(run, 60 * 60 * 1000).unref();
  };

  hourly(purgeScheduledDeletions, (n) => `Purged ${n} profile(s) past the deletion grace period`);
  hourly(completePastSessions, (n) => `Completed ${n} session(s) whose end time has passed`);

  // Conventional error body for anything that reaches here unhandled.
  app.use(errorHandler);

  const port = process.env.PORT || 5000;
  app.listen(port, () => console.log(`TutorBridge API listening on port ${port}`));
}

main().catch((err) => {
  console.error("Failed to start server:", err);
  process.exit(1);
});
