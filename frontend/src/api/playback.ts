import { request } from "./client";
export const createPlaybackSession = (videoId: string) =>
  request<{ masterUrl: string; expiresAt: string }>(`/api/videos/${videoId}/playback-session`, {
    method: "POST",
  });
