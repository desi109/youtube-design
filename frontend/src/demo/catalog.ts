import { ApiError } from "../api/client";
import type { VideoDto, VideoPage } from "../api/videos";
import samples from "./videos.json";

const asset = (id: string, path: string) => `${import.meta.env.BASE_URL}demo/${id}/${path}`;

const videos: VideoDto[] = samples.map((sample) => ({
  id: sample.id,
  title: sample.title,
  description: sample.description,
  tags: sample.tags,
  durationSeconds: 8,
  thumbnailUrl: asset(sample.id, "thumbnail.jpg"),
  createdAt: "2026-10-21T00:00:00Z",
  renditions: [
    {
      label: "360p",
      width: 640,
      height: 360,
      bitrate: 800000,
      playlist: asset(sample.id, "360p/index.m3u8"),
    },
    {
      label: "720p",
      width: 1280,
      height: 720,
      bitrate: 2800000,
      playlist: asset(sample.id, "720p/index.m3u8"),
    },
  ],
}));

function checkSignal(signal?: AbortSignal) {
  if (signal?.aborted) throw new DOMException("Request cancelled", "AbortError");
}

export async function listDemoVideos(
  query: string,
  page: number,
  signal?: AbortSignal,
): Promise<VideoPage> {
  checkSignal(signal);
  const term = query.trim().toLowerCase();
  const matches = videos.filter((video) =>
    `${video.title} ${video.tags.join(" ")}`.toLowerCase().includes(term),
  );
  return {
    items: matches.slice(page * 24, (page + 1) * 24),
    page,
    total: matches.length,
    totalPages: Math.ceil(matches.length / 24),
  };
}

export async function getDemoVideo(id: string, signal?: AbortSignal): Promise<VideoDto> {
  checkSignal(signal);
  const video = videos.find((candidate) => candidate.id === id);
  if (!video) throw new ApiError(404, "This sample film is not in the demo library.", "NOT_FOUND");
  return video;
}

export async function getDemoRelated(id: string, signal?: AbortSignal): Promise<VideoDto[]> {
  const selected = await getDemoVideo(id, signal);
  checkSignal(signal);
  const score = (video: VideoDto) => video.tags.filter((tag) => selected.tags.includes(tag)).length;
  return videos.filter((video) => video.id !== id).sort((a, b) => score(b) - score(a));
}

export async function getDemoPlayback(id: string) {
  await getDemoVideo(id);
  return { masterUrl: asset(id, "master.m3u8"), expiresAt: null };
}
