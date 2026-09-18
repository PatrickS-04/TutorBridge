import { Router } from "express";
import { Thread, Message, MessageStatus } from "../models";
import { asyncHandler, apiError } from "../utils/asyncHandler";
import { paginate } from "../utils/paginate";
import { authenticate, AuthRequest } from "../middleware/auth";

const router = Router();
router.use(authenticate);

router.get(
  "/",
  asyncHandler(async (req: AuthRequest, res) => {
    const result = await paginate(Thread, { participants: req.auth!.userId }, req, "participants");
    res.status(200).json(result);
  })
);

router.get(
  "/:id/messages",
  asyncHandler(async (req: AuthRequest, res) => {
    const thread = await Thread.findOne({ _id: req.params.id, participants: req.auth!.userId });
    if (!thread) return res.status(404).json(apiError("NOT_FOUND", "Thread not found"));

    const result = await paginate(Message, { threadId: thread._id }, req);
    res.status(200).json(result);
  })
);

router.post(
  "/:id/messages",
  asyncHandler(async (req: AuthRequest, res) => {
    const { body, clientMessageId } = req.body;
    if (!body || !clientMessageId) {
      return res.status(400).json(apiError("VALIDATION_ERROR", "body and clientMessageId are required"));
    }

    const thread = await Thread.findOne({ _id: req.params.id, participants: req.auth!.userId });
    if (!thread) return res.status(404).json(apiError("NOT_FOUND", "Thread not found"));

    const existing = await Message.findOne({ threadId: thread._id, clientMessageId });
    if (existing) {
      return res.status(201).json(existing);
    }

    const message = await Message.create({
      threadId: thread._id,
      senderId: req.auth!.userId,
      body,
      clientMessageId,
      status: MessageStatus.SENT,
      sentAt: new Date(),
    });

    thread.lastMessagePreview = body.slice(0, 140);
    thread.lastMessageAt = message.sentAt;
    await thread.save();

    res.status(201).json({
      id: message._id,
      body: message.body,
      sentAt: message.sentAt,
      status: message.status,
      clientMessageId: message.clientMessageId,
    });
  })
);

export default router;
