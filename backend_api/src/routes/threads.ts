import { Router } from "express";
import { Thread, Message, MessageStatus, NotificationCategory } from "../models";
import { asyncHandler, apiError } from "../utils/asyncHandler";
import { paginate } from "../utils/paginate";
import { authenticate, AuthRequest } from "../middleware/auth";
import { notify } from "../utils/notify";

const router = Router();
router.use(authenticate);

/**
 * The caller's conversations, most recently active first. Only the other
 * person's name and role come back with each one - a chat list has no use for
 * their email or rate - plus `unread`, how many messages are waiting for the
 * caller.
 */
router.get(
  "/",
  asyncHandler(async (req: AuthRequest, res) => {
    const me = req.auth!.userId;
    const result = await paginate(
      Thread,
      { participants: me },
      req,
      { path: "participants", select: "fullName role" },
      undefined,
      { lastMessageAt: -1, createdAt: -1 }
    );

    res.status(200).json({
      ...result,
      data: result.data.map((thread) => ({
        ...thread.toObject({ flattenMaps: true }),
        unread: thread.unreadCount?.get(me) ?? 0,
      })),
    });
  })
);

router.get(
  "/:id/messages",
  asyncHandler(async (req: AuthRequest, res) => {
    const me = req.auth!.userId;
    const thread = await Thread.findOne({ _id: req.params.id, participants: me });
    if (!thread) return res.status(404).json(apiError("NOT_FOUND", "Thread not found"));

    //Messages carry sentAt rather than createdAt, so the default sort would be arbitrary.
    const result = await paginate(Message, { threadId: thread._id }, req, undefined, undefined, { sentAt: -1 });

    //Opening the conversation is what counts as reading it.
    await Message.updateMany(
      { threadId: thread._id, senderId: { $ne: me }, readAt: null },
      { $set: { status: MessageStatus.READ, readAt: new Date() } }
    );
    if ((thread.unreadCount?.get(me) ?? 0) > 0) {
      thread.unreadCount.set(me, 0);
      await thread.save();
    }

    res.status(200).json(result);
  })
);

router.post(
  "/:id/messages",
  asyncHandler(async (req: AuthRequest, res) => {
    const me = req.auth!.userId;
    const { body, clientMessageId } = req.body;
    if (!body || !clientMessageId) {
      return res.status(400).json(apiError("VALIDATION_ERROR", "body and clientMessageId are required"));
    }

    const thread = await Thread.findOne({ _id: req.params.id, participants: me });
    if (!thread) return res.status(404).json(apiError("NOT_FOUND", "Thread not found"));

    const existing = await Message.findOne({ threadId: thread._id, clientMessageId });
    if (existing) {
      return res.status(201).json(existing);
    }

    const message = await Message.create({
      threadId: thread._id,
      senderId: me,
      body,
      clientMessageId,
      status: MessageStatus.SENT,
      sentAt: new Date(),
    });

    const recipient = thread.participants.map(String).find((id) => id !== me);
    thread.lastMessagePreview = body.slice(0, 140);
    thread.lastMessageAt = message.sentAt;
    if (recipient) thread.unreadCount.set(recipient, (thread.unreadCount.get(recipient) ?? 0) + 1);
    await thread.save();

    if (recipient) {
      notify(
        req.auth!.institutionId,
        recipient,
        NotificationCategory.MESSAGE,
        "New message",
        body.slice(0, 120),
        `tutorbridge://threads/${thread._id}`
      );
    }

    res.status(201).json(message);
  })
);

export default router;
