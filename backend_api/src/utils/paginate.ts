import { Request } from "express";
import { Model, PopulateOptions } from "mongoose";

const PAGE_SIZE = 20;

export async function paginate<T>(
  model: Model<T>,
  filter: Record<string, unknown>,
  req: Request,
  populate?: string | string[] | PopulateOptions | PopulateOptions[],
  /** Extra projection, for pulling back a path the schema marks `select: false`. */
  select?: string,
  /** Newest-created first unless the collection has a better clock (messages use sentAt). */
  sort: Record<string, 1 | -1> = { createdAt: -1 }
) {
  const page = Math.max(1, parseInt(String(req.query.page ?? "1"), 10) || 1);
  const skip = (page - 1) * PAGE_SIZE;

  let query = model.find(filter).sort(sort).skip(skip).limit(PAGE_SIZE);
  if (populate) query = query.populate(populate as any);
  if (select) query = query.select(select);

  const [data, total] = await Promise.all([query.exec(), model.countDocuments(filter)]);

  return {
    data,
    page,
    pageSize: PAGE_SIZE,
    totalPages: Math.max(1, Math.ceil(total / PAGE_SIZE)),
  };
}
