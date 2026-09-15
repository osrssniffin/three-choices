package com.osrssniffin.threechoices;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.MessageNode;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.util.Text;

/**
 * Native RuneLite chat/mod-icon integration for Three Choices.
 *
 * The custom image is registered through RuneLite's ChatIconManager instead of
 * editing Client#getModIcons() directly. ChatIconManager owns re-registration
 * when the client rebuilds the mod-icon array during startup/login, which is
 * the same lifecycle-safe mechanism RuneLite itself uses for custom chat icons.
 */
@Singleton
class ThreeChoicesModeIcon
{
    private static final int TRANSPARENT = 0x00000000;
    private static final int OUTLINE = 0xFF241B13;
    private static final int BRONZE_DARK = 0xFF70401F;
    private static final int BRONZE = 0xFFA9632F;
    private static final int BRONZE_LIGHT = 0xFFD5944E;
    private static final int GOLD = 0xFFF1C453;
    private static final int STEEL = 0xFFB7B1A1;

    /*
     * A deliberately tiny OSRS-style account-mode emblem: a bronze/steel helm
     * with a three-point crest and three bright visor marks. It is designed at
     * native chat-icon scale instead of shrinking a larger logo at runtime.
     */
    private static final String[] ICON_PIXELS =
    {
        "...Y..Y..Y...",
        "...G..G..G...",
        "..XGXXGXXGX..",
        ".XBBBBBBBBBX.",
        "XBDDDDDDDDBX.",
        "XBDLLLLLDBBX.",
        "XBDDXDXDXDBX.",
        "XBDDGDGDGDBX.",
        "XBDDXDXDXDBX.",
        ".XBBBBBBBBX..",
        "..XBB..BBX...",
        "..XBB..BBX...",
        "...XX..XX...."
    };

    private final Client client;
    private final ChatIconManager chatIconManager;
    private final Set<Integer> previouslyAppliedIndexes = new HashSet<>();

    /** ChatIconManager handle, not the current mod-icons array index. */
    private int chatIconId = -1;

    @Inject
    ThreeChoicesModeIcon(
        Client client,
        ChatIconManager chatIconManager)
    {
        this.client = client;
        this.chatIconManager = chatIconManager;
    }

    void startUp()
    {
        if (chatIconId == -1)
        {
            chatIconId = chatIconManager.registerChatIcon(buildIconImage());
        }
    }

    void shutDown()
    {
        // ChatIconManager owns the registered sprite lifecycle. There is no
        // chatbox-input text to restore because Three Choices never edits it.
    }

    void decorateChat(ChatMessage event)
    {
        if (event == null || client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        int idx = currentIconIndex();
        if (idx < 0)
        {
            return;
        }

        Player local = client.getLocalPlayer();
        if (local == null || local.getName() == null || event.getName() == null)
        {
            return;
        }

        if (!sanitizeName(local.getName()).equalsIgnoreCase(sanitizeName(event.getName())))
        {
            return;
        }

        MessageNode messageNode = event.getMessageNode();
        if (messageNode == null)
        {
            return;
        }

        String currentName = event.getName();
        String tag = iconTag(idx);
        if (currentName.contains(tag))
        {
            return;
        }

        // Preserve the game's native account/rank tags (Ironman, clan rank,
        // etc.) and add Three Choices alongside them.
        messageNode.setName(tag + stripOldThreeChoicesTags(currentName));
        client.refreshChat();
    }

    /**
     * Mirrors RuneLite Player Indicators' native menu-entry decoration path.
     */
    void decoratePlayerMenus()
    {
        if (client.getGameState() != GameState.LOGGED_IN || client.isMenuOpen())
        {
            return;
        }

        int idx = currentIconIndex();
        Player local = client.getLocalPlayer();
        if (idx < 0 || local == null)
        {
            return;
        }

        String tag = iconTag(idx);
        for (MenuEntry entry : client.getMenuEntries())
        {
            if (!isPlayerMenuAction(entry.getType()))
            {
                continue;
            }

            Player targetPlayer = entry.getPlayer();
            if (targetPlayer != local)
            {
                continue;
            }

            String target = entry.getTarget();
            if (target != null && !target.contains(tag))
            {
                entry.setTarget(tag + stripOldThreeChoicesTags(target));
            }
        }
    }

    private int currentIconIndex()
    {
        if (chatIconId < 0)
        {
            return -1;
        }

        int idx = chatIconManager.chatIconIndex(chatIconId);
        if (idx >= 0)
        {
            previouslyAppliedIndexes.add(idx);
        }
        return idx;
    }

    private String stripOldThreeChoicesTags(String text)
    {
        String cleaned = text;
        for (Integer idx : previouslyAppliedIndexes)
        {
            cleaned = cleaned.replace(iconTag(idx), "");
        }
        return cleaned;
    }

    private static String sanitizeName(String value)
    {
        if (value == null)
        {
            return "";
        }
        return Text.sanitize(Text.removeTags(value)).replace('\u00A0', ' ').trim();
    }

    private static String iconTag(int index)
    {
        return "<img=" + index + ">";
    }

    private static boolean isPlayerMenuAction(MenuAction action)
    {
        return action == MenuAction.WALK
            || action == MenuAction.WIDGET_TARGET_ON_PLAYER
            || action == MenuAction.ITEM_USE_ON_PLAYER
            || action == MenuAction.PLAYER_FIRST_OPTION
            || action == MenuAction.PLAYER_SECOND_OPTION
            || action == MenuAction.PLAYER_THIRD_OPTION
            || action == MenuAction.PLAYER_FOURTH_OPTION
            || action == MenuAction.PLAYER_FIFTH_OPTION
            || action == MenuAction.PLAYER_SIXTH_OPTION
            || action == MenuAction.PLAYER_SEVENTH_OPTION
            || action == MenuAction.PLAYER_EIGHTH_OPTION
            || action == MenuAction.RUNELITE_PLAYER;
    }

    private static BufferedImage buildIconImage()
    {
        BufferedImage image = new BufferedImage(13, 13, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < ICON_PIXELS.length; y++)
        {
            String row = ICON_PIXELS[y];
            for (int x = 0; x < row.length(); x++)
            {
                image.setRGB(x, y, colorFor(row.charAt(x)));
            }
        }
        return image;
    }

    private static int colorFor(char pixel)
    {
        switch (pixel)
        {
            case 'X': return OUTLINE;
            case 'B': return BRONZE_DARK;
            case 'D': return BRONZE;
            case 'L': return BRONZE_LIGHT;
            case 'G': return GOLD;
            case 'Y': return STEEL;
            default: return TRANSPARENT;
        }
    }
}
