package com.desi.youtube.upload;

import java.util.UUID;

public record UploadCompletionDto(UUID videoId, String status) {}
