package com.desi.youtube.processing;

/** Only self-contained video containers are accepted as user uploads. */
public final class MediaInputPolicy {

  private MediaInputPolicy() {}

  public static final String FORMATS =
    "mov,mp4,m4a,3gp,3g2,mj2,matroska,webm,avi,mpegts,mpeg,ogg,flv";
}
