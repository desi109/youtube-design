package com.desi.youtube.catalog;

import java.util.List;

public record VideoPage(List<VideoDto> items, int page, long total, int totalPages) {}
