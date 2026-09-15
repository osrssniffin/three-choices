package com.osrssniffin.threechoices;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Collection-log-inspired unlock toast shown when one of the three choices is
 * permanently selected.
 */
@Singleton
public class UnlockCelebrationOverlay extends Overlay
{
    private static final long DISPLAY_MS = 5_000L;
    private static final long FADE_MS = 1_350L;
    private static final int WIDTH = 310;
    private static final int HEIGHT = 78;
    private static final int SKILL_HEIGHT = 96;
    private static final Color PANEL = new Color(28, 24, 18, 242);
    private static final Color GOLD = new Color(235, 162, 38);
    private static final Color GOLD_SOFT = new Color(172, 112, 30);

    private final ItemManager itemManager;
    private volatile int itemId = -1;
    private volatile String itemName = "";
    private volatile long shownAt;
    private volatile long expiresAt;
    private volatile BufferedImage image;
    private volatile boolean shiny;
    private volatile SkillGate openedSkill;
    private volatile String subtitle = "Permanently unlocked";

    @Inject
    public UnlockCelebrationOverlay(ItemManager itemManager)
    {
        this.itemManager = itemManager;
        setPosition(OverlayPosition.TOP_CENTER);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(PRIORITY_HIGHEST);
    }

    public void show(int itemId, String itemName)
    {
        show(itemId, itemName, false);
    }

    public void show(int itemId, String itemName, boolean shiny)
    {
        show(itemId, itemName, shiny, null, "Permanently unlocked");
    }

    public void show(int itemId, String itemName, boolean shiny, SkillGate openedSkill, String subtitle)
    {
        this.itemId = itemId;
        this.itemName = itemName == null ? "Unlocked item" : itemName;
        this.shiny = shiny;
        this.openedSkill = openedSkill;
        this.subtitle = subtitle == null || subtitle.isBlank() ? "Permanently unlocked" : subtitle;
        this.shownAt = System.currentTimeMillis();
        this.expiresAt = shownAt + DISPLAY_MS;
        this.image = itemManager.getImage(itemId);
    }

    public void hide()
    {
        itemId = -1;
        itemName = "";
        shiny = false;
        openedSkill = null;
        subtitle = "Permanently unlocked";
        shownAt = 0L;
        expiresAt = 0L;
        image = null;
    }

    public boolean isShowing()
    {
        return itemId > 0 && System.currentTimeMillis() < expiresAt;
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!isShowing())
        {
            return null;
        }

        long now = System.currentTimeMillis();
        long remaining = Math.max(0L, expiresAt - now);
        float alpha = remaining >= FADE_MS ? 1.0f : Math.max(0.0f, remaining / (float) FADE_MS);
        int lift = remaining >= FADE_MS ? 0 : Math.round((1.0f - alpha) * 8.0f);

        int height = openedSkill == null ? HEIGHT : SKILL_HEIGHT;
        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g.translate(0, -lift);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            long age = now - shownAt;
            float pulse = age < 900L ? (float) (0.5d + 0.5d * Math.sin(age / 95.0d)) : 0f;
            int pulseBoost = Math.round(35f * pulse);
            Color border = new Color(
                Math.min(255, GOLD.getRed() + pulseBoost),
                Math.min(255, GOLD.getGreen() + pulseBoost),
                GOLD.getBlue());

            g.setColor(PANEL);
            g.fillRoundRect(0, 0, WIDTH, height, 7, 7);
            g.setStroke(new BasicStroke(2f));
            g.setColor(border);
            g.drawRoundRect(1, 1, WIDTH - 3, height - 3, 7, 7);
            g.setColor(GOLD_SOFT);
            g.drawLine(6, 24, WIDTH - 7, 24);

            g.setFont(FontManager.getRunescapeBoldFont());
            g.setColor(GOLD);
            String title = shiny ? "SHINY UNLOCK" : "NEW UNLOCK";
            FontMetrics titleMetrics = g.getFontMetrics();
            g.drawString(title, (WIDTH - titleMetrics.stringWidth(title)) / 2, 17);

            if (image != null)
            {
                g.drawImage(image, 15, 32, 36, 32, null);
            }

            g.setFont(FontManager.getRunescapeBoldFont().deriveFont(15f));
            g.setColor(Color.WHITE);
            drawClipped(g, itemName, 61, 48, WIDTH - 72);

            g.setFont(FontManager.getRunescapeSmallFont());
            g.setColor(new Color(205, 205, 205));
            g.drawString(subtitle, 61, 65);

            if (openedSkill != null)
            {
                g.setFont(FontManager.getRunescapeBoldFont());
                g.setColor(new Color(85, 220, 105));
                String skillText = openedSkill.getDisplayName().toUpperCase() + " UNLOCKED";
                FontMetrics skillMetrics = g.getFontMetrics();
                g.drawString(skillText, (WIDTH - skillMetrics.stringWidth(skillText)) / 2, 85);
            }
        }
        finally
        {
            g.dispose();
        }
        return new Dimension(WIDTH, height);
    }

    private static void drawClipped(Graphics2D graphics, String text, int x, int y, int maxWidth)
    {
        FontMetrics metrics = graphics.getFontMetrics();
        if (metrics.stringWidth(text) <= maxWidth)
        {
            graphics.drawString(text, x, y);
            return;
        }

        String suffix = "...";
        String clipped = text;
        while (!clipped.isEmpty() && metrics.stringWidth(clipped + suffix) > maxWidth)
        {
            clipped = clipped.substring(0, clipped.length() - 1);
        }
        graphics.drawString(clipped + suffix, x, y);
    }
}
