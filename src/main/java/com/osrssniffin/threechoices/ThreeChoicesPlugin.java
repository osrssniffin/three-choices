package com.osrssniffin.threechoices;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Locale;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.FakeXpDrop;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GrandExchangeSearched;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.StatChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
    name = "Three Choices",
    description = "Earn points, reveal three progression-matched upgrades, choose one, and build a unique restricted account",
    tags = {"gamemode", "points", "unlock", "choices", "threechoices"},
    enabledByDefault = false
)
public class ThreeChoicesPlugin extends Plugin
{
    private static final long LEGACY_ROLL_REFUND = ProgressionEconomyService.ROLL_COST;
    private static final long BLOCK_MESSAGE_COOLDOWN_MS = 1_200L;

    @Inject
    private Client client;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ClientThread clientThread;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private LockedItemOverlay lockedItemOverlay;

    @Inject
    private UnlockCelebrationOverlay unlockCelebrationOverlay;

    @Inject
    private ThreeChoicesModeIcon modeIcon;

    @Inject
    private ThreeChoicesStateService stateService;

    @Inject
    private PointAwardService pointAwardService;

    @Inject
    private NpcKillTracker npcKillTracker;

    @Inject
    private ActivityCreditTracker activityCreditTracker;

    @Inject
    private AccountProgressService accountProgressService;

    @Inject
    private RestrictionService restrictionService;

    @Inject
    private ItemAccessService itemAccessService;

    @Inject
    private ItemPool itemPool;

    @Inject
    private QuestRewardService questRewardService;

    private ThreeChoicesPanel panel;
    private NavigationButton navigationButton;
    private long lastBlockMessageAt;
    private String lastBlockMessage = "";

    @Override
    protected void startUp()
    {
        boolean migrated = false;
        if (client.getGameState() == GameState.LOGGED_IN)
        {
            itemAccessService.clearCache();
            itemPool.clearCache();
            migrated = stateService.loadAndMigrate(LEGACY_ROLL_REFUND);
        }

        panel = injector.getInstance(ThreeChoicesPanel.class);
        panel.setResetHandler(this::performReset);
        navigationButton = NavigationButton.builder()
            .tooltip("Three Choices")
            .icon(createIcon())
            .priority(7)
            .panel(panel)
            .build();
        clientToolbar.addNavigation(navigationButton);
        overlayManager.add(lockedItemOverlay);
        overlayManager.add(unlockCelebrationOverlay);
        clientThread.invoke(modeIcon::startUp);

        final boolean migrationResult = migrated;
        if (client.getGameState() == GameState.LOGGED_IN)
        {
            clientThread.invoke(() ->
            {
                accountProgressService.refreshFromClient();
                itemPool.warmDisplayCache(stateService.getUnlockedItems());
                itemPool.warmDisplayCache(stateService.getPendingRoll());
                boolean questChanged = questRewardService.initializeOrSync();
                pointAwardService.resetForLoginOrHop();
                npcKillTracker.reset();
                if (panel != null)
                {
                    SwingUtilities.invokeLater(() ->
                    {
                        panel.onProfileLoaded(migrationResult);
                        if (questChanged) panel.refresh();
                    });
                }
            });
        }
    }

    @Override
    protected void shutDown()
    {
        if (navigationButton != null)
        {
            clientToolbar.removeNavigation(navigationButton);
        }
        overlayManager.remove(lockedItemOverlay);
        overlayManager.remove(unlockCelebrationOverlay);
        clientThread.invoke(modeIcon::shutDown);
        if (panel != null)
        {
            SwingUtilities.invokeLater(panel::closeTransientWindows);
        }
        npcKillTracker.reset();
        accountProgressService.clear();
        panel = null;
        navigationButton = null;
        lastBlockMessageAt = 0L;
        lastBlockMessage = "";
    }

    @Provides
    ThreeChoicesConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(ThreeChoicesConfig.class);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            itemAccessService.clearCache();
            itemPool.clearCache();
            boolean migrated = stateService.loadAndMigrate(LEGACY_ROLL_REFUND);
            accountProgressService.refreshFromClient();
            itemPool.warmDisplayCache(stateService.getUnlockedItems());
            itemPool.warmDisplayCache(stateService.getPendingRoll());
            boolean questChanged = questRewardService.initializeOrSync();
            pointAwardService.resetForLoginOrHop();
            npcKillTracker.reset();
            if (panel != null)
            {
                final boolean migrationResult = migrated;
                SwingUtilities.invokeLater(() ->
                {
                    panel.onProfileLoaded(migrationResult);
                    if (questChanged) panel.refresh();
                });
            }
        }
        else if (event.getGameState() == GameState.HOPPING || event.getGameState() == GameState.LOGIN_SCREEN)
        {
            pointAwardService.resetForLoginOrHop();
            npcKillTracker.reset();
            accountProgressService.clear();
            refreshPanel();
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        pointAwardService.onGameTick();
        boolean questChanged = questRewardService.syncNext();
        long questPointAward = questRewardService.consumeLastPointAward();
        if (questPointAward > 0L)
        {
            addLocalMessage("Three Choices: QUEST COMPLETE — +"
                + String.format(Locale.US, "%,d", questPointAward) + " POINTS");
            questChanged = true;
        }
        if (questChanged) refreshPanel();
    }

    @Subscribe
    public void onStatChanged(StatChanged event)
    {
        accountProgressService.refreshFromClient();
        pointAwardService.onStatChanged(event);
        refreshPanel();
    }

    @Subscribe
    public void onFakeXpDrop(FakeXpDrop event)
    {
        if (pointAwardService.onFakeXpDrop(event))
        {
            refreshPanel();
        }
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event)
    {
        npcKillTracker.onHitsplatApplied(event);
    }

    @Subscribe
    public void onActorDeath(ActorDeath event)
    {
        if (npcKillTracker.onActorDeath(event))
        {
            refreshPanel();
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event)
    {
        npcKillTracker.onNpcDespawned(event);
    }

    @Subscribe
    public void onClientTick(ClientTick event)
    {
        modeIcon.decoratePlayerMenus();
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        modeIcon.decorateChat(event);
        if (activityCreditTracker.onChatMessage(event))
        {
            refreshPanel();
        }
    }

    /**
     * Run before RuneLite's GE fuzzy-search handler. Consuming the event here
     * makes locked items absent from the native GE search results entirely.
     */
    @Subscribe(priority = 100)
    public void onGrandExchangeSearched(GrandExchangeSearched event)
    {
        restrictionService.filterGrandExchangeSearch(event);
    }

    /*
     * Restrictions are enforced at click time rather than by removing native
     * menu entries. This keeps Three Choices within Plugin Hub menu rules
     * (notably Construction and Blackjacking) while still preventing the action.
     */
    @Subscribe(priority = 100)
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        RestrictionService.BlockResult result = restrictionService.shouldBlock(event);
        if (!result.isBlocked())
        {
            return;
        }

        event.consume();
        showBlockedMessage(result.getMessage());
    }


    private void performReset()
    {
        stateService.resetAll();
        questRewardService.resetBaseline();
        accountProgressService.refreshFromClient();
        itemAccessService.clearCache();
        itemPool.clearCache();
        unlockCelebrationOverlay.hide();
        pointAwardService.resetForLoginOrHop();
        npcKillTracker.reset();
        lastBlockMessageAt = 0L;
        lastBlockMessage = "";
        addLocalMessage("Three Choices: everything has been reset to a fresh profile.");
        if (panel != null)
        {
            SwingUtilities.invokeLater(panel::onReset);
        }
    }

    private void showBlockedMessage(String message)
    {
        long now = System.currentTimeMillis();
        if (message.equals(lastBlockMessage) && now - lastBlockMessageAt < BLOCK_MESSAGE_COOLDOWN_MS)
        {
            return;
        }
        lastBlockMessage = message;
        lastBlockMessageAt = now;
        addLocalMessage("Three Choices: " + message);
    }

    private void addLocalMessage(String message)
    {
        client.addChatMessage(
            ChatMessageType.GAMEMESSAGE,
            "",
            message,
            null,
            false);
    }

    private void refreshPanel()
    {
        if (panel != null)
        {
            SwingUtilities.invokeLater(panel::refresh);
        }
    }

    private static BufferedImage createIcon()
    {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            g.setColor(new Color(120, 120, 120));
            g.drawRoundRect(1, 4, 8, 10, 2, 2);
            g.setColor(new Color(185, 185, 185));
            g.drawRoundRect(4, 2, 8, 11, 2, 2);
            g.setColor(Color.WHITE);
            g.drawRoundRect(7, 1, 8, 12, 2, 2);

            g.setColor(new Color(220, 138, 0));
            g.fillOval(9, 4, 4, 4);
            g.drawLine(9, 11, 11, 13);
            g.drawLine(11, 13, 15, 9);
        }
        finally
        {
            g.dispose();
        }
        return image;
    }
}
