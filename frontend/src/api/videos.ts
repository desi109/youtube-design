import { request } from "./client";
export interface Rendition {
  label: string;
  width: number;
  height: number;
  bitrate: number;
  playlist: string;
}
export interface VideoDto {
  id: string;
  title: string;
  description: string;
  tags: string[];
  durationSeconds: number;
  thumbnailUrl: string;
  createdAt: string;
  renditions: Rendition[];
}
export interface VideoPage {
  items: VideoDto[];
  page: number;
  total: number;
  totalPages: number;
}
export const listVideos = (query = "", page = 0, signal?: AbortSignal) =>
  request<VideoPage>(`/api/videos?q=${encodeURIComponent(query)}&page=${page}&size=24`, { signal });
export const getVideo = (id: string, signal?: AbortSignal) =>
  request<VideoDto>(`/api/videos/${id}`, { signal });
export const getRelatedVideos = (id: string, signal?: AbortSignal) =>
  request<VideoDto[]>(`/api/videos/${id}/related?limit=12`, { signal });
export function duration(seconds: number) {
  const whole = Math.floor(seconds);
  return `${Math.floor(whole / 60)}:${String(whole % 60).padStart(2, "0")}`;
}
export function addedDate(value: string) {
  return new Intl.DateTimeFormat("en", { month: "short", day: "numeric" }).format(new Date(value));
}
