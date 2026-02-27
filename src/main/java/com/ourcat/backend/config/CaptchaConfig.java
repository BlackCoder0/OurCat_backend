package com.ourcat.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Random;

/**
 * Simple in-memory captcha (no Kaptcha dependency for minimal setup).
 * Returns base64 PNG image and stores expected code in memory by key.
 */
@Configuration
public class CaptchaConfig {

    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;
    private static final int CODE_LENGTH = 4;
    private static final String CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    @Bean
    public Random captchaRandom() {
        return new Random();
    }

    public static class CaptchaResult {
        public final String imageBase64;
        public final String code;

        public CaptchaResult(String imageBase64, String code) {
            this.imageBase64 = imageBase64;
            this.code = code;
        }
    }

    public static CaptchaResult generate(Random random) {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setColor(new Color(255, 152, 0));
        g.setFont(new Font("SansSerif", Font.BOLD, 28));
        g.drawString(code.toString(), 20, 28);
        g.dispose();
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
            return new CaptchaResult("data:image/png;base64," + base64, code.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
