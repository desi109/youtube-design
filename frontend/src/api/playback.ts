import { request } from "./client";
import { isPagesDemo } from "../demo/mode";
import { getDemoPlayback } from "../demo/catalog";
export const createPlaybackSession = (videoId: string) =>
  isPagesDemo
    ? getDemoPlayback(videoId)
    : request<{ masterUrl: string; expiresAt: string }>(`/api/videos/${videoId}/playback-session`, {
        method: "POST",
      });
