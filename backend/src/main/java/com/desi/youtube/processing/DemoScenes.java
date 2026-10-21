package com.desi.youtube.processing;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.Random;
import javax.imageio.ImageIO;

/** Original illustrated scenes shared by local seeding and the static Pages build. */
public final class DemoScenes {

  private DemoScenes() {}

  public static void main(String[] args) throws Exception {
    if (args.length != 1) throw new IllegalArgumentException("Supply an output directory");
    Path directory = Files.createDirectories(Path.of(args[0]));
    for (int scene = 0; scene < 3; scene++) drawScene(directory.resolve(scene + ".png"), scene);
  }

  public static void drawScene(Path destination, int scene) throws Exception {
    var image = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    if (scene == 0) {
      g.setPaint(new GradientPaint(0, 0, new Color(55, 76, 91), 0, 540, new Color(219, 150, 114)));
      g.fillRect(0, 0, 1280, 720);
      g.setColor(new Color(247, 206, 151));
      g.fillOval(810, 152, 128, 128);
      g.setColor(new Color(49, 78, 78));
      g.fillPolygon(
        new int[] { 0, 0, 270, 470, 690, 960, 1280, 1280 },
        new int[] { 720, 460, 290, 440, 340, 510, 400, 720 },
        8
      );
      g.setPaint(new GradientPaint(0, 460, new Color(41, 67, 70), 0, 720, new Color(17, 42, 49)));
      g.fillRect(0, 490, 1280, 230);
      g.setColor(new Color(216, 165, 123, 70));
      for (int i = 0; i < 32; i++) {
        int y = 505 + i * 7,
          w = 20 + i * 9;
        g.fillRoundRect(865 - w / 2 + (i % 3) * 5, y, w, 2, 2, 2);
      }
    } else if (scene == 1) {
      g.setPaint(new GradientPaint(0, 0, new Color(34, 27, 62), 1280, 720, new Color(153, 67, 77)));
      g.fillRect(0, 0, 1280, 720);
      for (int i = 9; i >= 0; i--) {
        int x = 230 + i * 38,
          y = 540 - i * 38;
        g.setColor(new Color(130 + i * 11, 68 + i * 11, 104 + i * 6));
        g.fillRoundRect(x, y - 220, 700 - i * 36, 240, 120, 120);
      }
      g.setColor(new Color(245, 174, 120));
      g.fillOval(780, 114, 175, 175);
      g.setColor(new Color(46, 35, 73));
      g.fillOval(812, 82, 175, 175);
    } else {
      g.setPaint(new GradientPaint(0, 0, new Color(12, 28, 45), 0, 720, new Color(44, 65, 77)));
      g.fillRect(0, 0, 1280, 720);
      var random = new Random(42);
      g.setColor(new Color(182, 191, 187));
      for (int i = 0; i < 80; i++) g.fillOval(random.nextInt(1280), random.nextInt(430), 2, 2);
      g.setColor(new Color(223, 199, 150));
      g.fillOval(850, 102, 102, 102);
      for (int i = 0; i < 18; i++) {
        int x = i * 78 - 30,
          h = 100 + random.nextInt(260);
        g.setColor(new Color(17 + (i % 3) * 5, 29 + (i % 3) * 5, 39 + (i % 3) * 5));
        g.fillRect(x, 720 - h, 70, h);
        for (int y = 735 - h; y < 700; y += 20) for (int w = 0; w < 4; w++) if (
          random.nextInt(4) == 0
        ) {
          g.setColor(new Color(213, 167, 100, 120));
          g.fillRect(x + 9 + w * 14, y, 6, 9);
        }
      }
    }
    g.setColor(new Color(255, 255, 255, 105));
    g.setFont(new Font("SansSerif", Font.PLAIN, 12));
    g.drawString("FRAME  /  MOTION STUDIES  0" + (scene + 1), 50, 670);
    g.dispose();
    ImageIO.write(image, "png", destination.toFile());
  }
}
