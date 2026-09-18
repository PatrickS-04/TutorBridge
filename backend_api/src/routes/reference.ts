import { Router } from "express";
import { Subject, Province, Area } from "../models";
import { asyncHandler } from "../utils/asyncHandler";
import { authenticate } from "../middleware/auth";

const router = Router();
router.use(authenticate);

router.get(
  "/subjects-and-locations",
  asyncHandler(async (_req, res) => {
    const [subjects, provinces, areas] = await Promise.all([
      Subject.find().sort({ name: 1 }),
      Province.find().sort({ name: 1 }),
      Area.find().sort({ name: 1 }),
    ]);

    const areasByProvince = new Map<string, typeof areas>();
    for (const area of areas) {
      const key = String(area.provinceId);
      if (!areasByProvince.has(key)) areasByProvince.set(key, []);
      areasByProvince.get(key)!.push(area);
    }

    res.set("Cache-Control", "public, max-age=3600");
    res.status(200).json({
      subjects: subjects.map((s) => ({ id: s._id, name: s.name })),
      provinces: provinces.map((p) => ({
        id: p._id,
        name: p.name,
        areas: (areasByProvince.get(String(p._id)) ?? []).map((a) => ({ id: a._id, name: a.name })),
      })),
    });
  })
);

export default router;
