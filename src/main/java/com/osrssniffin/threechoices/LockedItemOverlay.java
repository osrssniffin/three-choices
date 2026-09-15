package com.osrssniffin.threechoices;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/** Draws a clear but non-destructive lock marker over restricted owned items. */
public class LockedItemOverlay extends WidgetItemOverlay
{
    private static final Color SHADE = new Color(90, 0, 0, 95);
    private static final Color BORDER = new Color(235, 80, 70, 205);
    private static final Color LOCK = new Color(255, 210, 90, 230);

    private final ItemAccessService itemAccessService;

    @Inject
    public LockedItemOverlay(ItemAccessService itemAccessService)
    {
        this.itemAccessService = itemAccessService;
        showOnInventory();
        showOnBank();
        showOnEquipment();
    }

    @Override
    public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
    {
        if (!itemAccessService.isLocked(itemId))
        {
            return;
        }

        Rectangle bounds = widgetItem.getCanvasBounds();
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0)
        {
            return;
        }

        graphics.setColor(SHADE);
        graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

        graphics.setStroke(new BasicStroke(1.2f));
        graphics.setColor(BORDER);
        graphics.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);

        int lockWidth = Math.min(12, Math.max(8, bounds.width / 3));
        int lockHeight = Math.min(10, Math.max(7, bounds.height / 4));
        int x = bounds.x + (bounds.width - lockWidth) / 2;
        int y = bounds.y + (bounds.height - lockHeight) / 2 + 2;

        graphics.setColor(LOCK);
        graphics.drawArc(x + 2, y - 6, lockWidth - 4, 10, 0, 180);
        graphics.fillRoundRect(x, y, lockWidth, lockHeight, 2, 2);
    }
}
