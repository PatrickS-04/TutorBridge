import { Router } from "express";
import { IThread, Thread, Message, Report, MessageStatus, NotificationCategory, ReportReason } from "../models";
import { asyncHandler, apiError } from "../utils/asyncHandler";
import { paginate } from "../utils/paginate";
import { authenticate, AuthRequest } from "../middleware/auth";
import { notify } from "../utils/notify";

const router = Router();
router.use(authenticate);

/** A report keeps this many of the latest messages, so it survives the conversation being erased. */
const REPORT_MESSAGE_LIMIT = 50;

/** Who blocked whom stays private: the other person only learns that they cannot send. */
function blockState(thread: Pick<IThread, "blockedBy">, me: string) {
  const blockedBy = (thread.blockedBy ?? []).map(String);
  return { blocked: blockedBy.length > 0, blockedByMe: blockedBy.includes(me) };
}

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
      data: result.data.map((thread) => {
        const { blockedBy: _blockedBy, ...rest } = thread.toObject({ flattenMaps: true });
        return { ...rest, unread: thread.unreadCount?.get(me) ?? 0, ...blockState(thread, me) };
      }),
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

    if (blockState(thread, me).blocked) {
      return res.status(403).json(apiError("BLOCKED", "You cannot send messages in this conversation"));
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
        "message.new",
        { text: body.slice(0, 120) },
        `tutorbridge://threads/${thread._id}`
      );
    }

    res.status(201).json(message);
  })
);

router.post(
  "/:id/block",
  asyncHandler(async (req: AuthRequest, res) => {
    const me = req.auth!.userId;
    const thread = await Thread.findOneAndUpdate(
      { _id: req.params.id, participants: me },
      { $addToSet: { blockedBy: me } },
      { new: true }
    );
    if (!thread) return res.status(404).json(apiError("NOT_FOUND", "Thread not found"));
    res.status(200).json(blockState(thread, me));
  })
);

router.delete(
  "/:id/block",
  asyncHandler(async (req: AuthRequest, res) => {
    const me = req.auth!.userId;
    // Only lifts the caller's own block; if the other person blocked too, theirs stands.
    const thread = await Thread.findOneAndUpdate(
      { _id: req.params.id, participants: me },
      { $pull: { blockedBy: me } },
      { new: true }
    );
    if (!thread) return res.status(404).json(apiError("NOT_FOUND", "Thread not found"));
    res.status(200).json(blockState(thread, me));
  })
);

router.post(
  "/:id/report",
  asyncHandler(async (req: AuthRequest, res) => {
    const me = req.auth!.userId;
    const { reason, details } = req.body;
    if (!Object.values(ReportReason).includes(reason)) {
      return res.status(400).json(apiError("VALIDATION_ERROR", "reason is not valid", "reason"));
    }
    if (details !== undefined && (typeof details !== "string" || details.length > 1000)) {
      return res.status(400).json(apiError("VALIDATION_ERROR", "details must be 1000 characters or fewer", "details"));
    }

    const thread = await Thread.findOne({ _id: req.params.id, participants: me });
    if (!thread) return res.status(404).json(apiError("NOT_FOUND", "Thread not found"));

    const latest = await Message.find({ threadId: thread._id }).sort({ sentAt: -1 }).limit(REPORT_MESSAGE_LIMIT);
    const report = await Report.create({
      institutionId: req.auth!.institutionId,
      threadId: thread._id,
      reporterId: me,
      reportedUserId: thread.participants.map(String).find((id) => id !== me),
      reason,
      details: details?.trim() || undefined,
      messages: latest.map(({ senderId, body, sentAt }) => ({ senderId, body, sentAt })),
    });
    res.status(201).json({ id: report._id, status: report.status });
  })
);

export default router;
