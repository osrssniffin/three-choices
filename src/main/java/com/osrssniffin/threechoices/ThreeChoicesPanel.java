package com.osrssniffin.threechoices;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Dialog;
import java.awt.Window;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Skill;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.QuantityFormatter;

public class ThreeChoicesPanel extends PluginPanel
{
    private static final String VIEW_PLAY = "play";
    private static final String VIEW_WELCOME = "welcome";
    private static final int MAX_UNLOCKS_SHOWN = 5;
    private static final Skill[] DISPLAY_SKILLS = {
        Skill.ATTACK, Skill.HITPOINTS, Skill.MINING, Skill.STRENGTH, Skill.AGILITY, Skill.SMITHING,
        Skill.DEFENCE, Skill.HERBLORE, Skill.FISHING, Skill.RANGED, Skill.THIEVING, Skill.COOKING,
        Skill.PRAYER, Skill.CRAFTING, Skill.FIREMAKING, Skill.MAGIC, Skill.FLETCHING, Skill.WOODCUTTING,
        Skill.RUNECRAFT, Skill.SLAYER, Skill.FARMING, Skill.CONSTRUCTION, Skill.HUNTER, Skill.SAILING
    };

    // RuneLite's side panel is narrow. Keep preferred widths below the available
    // inner width so BoxLayout never shifts cards sideways or clips text.
    private static final int VIEW_PREFERRED_WIDTH = 188;
    private static final int VIEW_PREFERRED_HEIGHT = 570;
    private static final int WIDE_TEXT_WIDTH = 182;
    private static final int CARD_TEXT_WIDTH = 160;
    private static final int STEP_TEXT_WIDTH = 128;

    private static final Color TEXT_MUTED = new Color(155, 155, 155);
    private static final Color CARD = new Color(35, 35, 35);
    private static final Color CARD_LIGHT = new Color(42, 42, 42);
    private static final Color BORDER = new Color(62, 62, 62);

    private final ThreeChoicesStateService stateService;
    private final RollService rollService;
    private final ProgressionEconomyService economyService;
    private final ItemManager itemManager;
    private final ItemPool itemPool;
    private final AccountProgressService accountProgressService;
    private final ItemAccessService itemAccessService;
    private final ClientThread clientThread;

    private final CardLayout viewLayout = new CardLayout();
    private final JPanel viewContainer = new JPanel(viewLayout);
    private final JButton playTab = tabButton("PLAY");
    private final JButton welcomeTab = tabButton("WELCOME");

    private final JLabel pointsValue = new JLabel("0");
    private final JLabel unlocksValue = new JLabel("0");
    private final JLabel nextRollLabel = centeredMuted(" ", 24);
    private final JProgressBar progressBar = new JProgressBar();
    private final JLabel combatLabel = new JLabel("Log in to read combat stats");
    private final JLabel combatSubLabel = muted(" ");
    private final JLabel skillingLabel = muted(" ");
    private final JLabel profileModeLabel = muted(" ");
    private final JButton rollButton = new JButton("ROLL 3");
    private final JButton rerollButton = new JButton("RE-ROLL");
    private final JLabel rerollHint = muted("Every other roll");
    private final JLabel statusLabel = centeredMuted(" ", 24);
    private final JPanel choicesPanel = verticalPanel();
    private final JPanel unlockedPanel = verticalPanel();
    private final JPanel choiceSection = verticalPanel();
    private final JPanel recentSection = verticalPanel();
    private final JButton showUnlocksButton = new JButton("SHOW UNLOCKS");
    private final JButton resetProgressButton = new JButton("RESET PROGRESS");

    private boolean rollInProgress;
    private String activeView = VIEW_WELCOME;
    private JDialog unlockBrowserDialog;
    private JDialog skillStatusDialog;
    private Runnable resetHandler;

    @Inject
    public ThreeChoicesPanel(
        ThreeChoicesStateService stateService,
        RollService rollService,
        ProgressionEconomyService economyService,
        ItemManager itemManager,
        ItemPool itemPool,
        AccountProgressService accountProgressService,
        ItemAccessService itemAccessService,
        ClientThread clientThread)
    {
        this.stateService = stateService;
        this.rollService = rollService;
        this.economyService = economyService;
        this.itemManager = itemManager;
        this.itemPool = itemPool;
        this.accountProgressService = accountProgressService;
        this.itemAccessService = itemAccessService;
        this.clientThread = clientThread;

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(8, 8, 8, 8));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        add(buildTopChrome(), BorderLayout.NORTH);

        viewContainer.setOpaque(false);
        viewContainer.add(buildPlayView(), VIEW_PLAY);
        viewContainer.add(buildWelcomeView(), VIEW_WELCOME);
        add(viewContainer, BorderLayout.CENTER);
        add(buildResetControl(), BorderLayout.SOUTH);

        playTab.addActionListener(e -> showView(VIEW_PLAY));
        welcomeTab.addActionListener(e -> showView(VIEW_WELCOME));

        showView(stateService.isWelcomeSeen() ? VIEW_PLAY : VIEW_WELCOME);
        refresh();
    }

    public void setResetHandler(Runnable resetHandler)
    {
        this.resetHandler = resetHandler;
    }

    private JPanel buildResetControl()
    {
        JPanel root = new JPanel(new BorderLayout());
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(6, 0, 0, 0));

        resetProgressButton.setFont(FontManager.getRunescapeSmallFont());
        resetProgressButton.setFocusPainted(false);
        resetProgressButton.setForeground(TEXT_MUTED);
        resetProgressButton.setToolTipText("Permanently reset Three Choices progression");
        resetProgressButton.addActionListener(e -> confirmReset());
        root.add(resetProgressButton, BorderLayout.CENTER);
        return root;
    }

    private void confirmReset()
    {
        Object[] options = {"Cancel", "Reset Progress"};
        int choice = JOptionPane.showOptionDialog(
            this,
            "This will permanently reset your Three Choices account progression, including points, rolls, unlocks, and progression data.\n\nThis cannot be undone.",
            "Reset Three Choices Progress?",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.WARNING_MESSAGE,
            null,
            options,
            options[0]);

        if (choice == 1 && resetHandler != null)
        {
            clientThread.invokeLater(resetHandler);
        }
    }

    public void onProfileLoaded(boolean migratedPendingRoll)
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(() -> onProfileLoaded(migratedPendingRoll));
            return;
        }

        // Login/hop/profile refreshes must never steal the user's selected tab.
        if (migratedPendingRoll)
        {
            setStatus("Old roll cleared and refunded.", ColorScheme.PROGRESS_COMPLETE_COLOR);
        }
        refresh();
    }

    public void onReset()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(this::onReset);
            return;
        }
        rollInProgress = false;
        closeTransientWindows();
        showView(VIEW_WELCOME);
        setStatus("Fresh Three Choices profile ready.", TEXT_MUTED);
        refresh();
    }

    public void refresh()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(this::refresh);
            return;
        }

        long points = stateService.getPoints();
        long cost = Math.max(1L, economyService.getRollCost());
        boolean pending = stateService.getPendingRoll().size() == 3;

        pointsValue.setText(format(points));
        unlocksValue.setText(Integer.toString(stateService.getUnlockedItems().size()));

        int progressMax = (int) Math.min(Integer.MAX_VALUE, cost);
        progressBar.setMaximum(progressMax);
        progressBar.setValue((int) Math.min(progressMax, points >= cost ? progressMax : points % cost));
        if (points >= cost)
        {
            nextRollLabel.setText(htmlCenter("READY  •  " + format(cost) + " PER ROLL"));
            nextRollLabel.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
        }
        else
        {
            nextRollLabel.setText(htmlCenter(format(cost - points) + " TO NEXT ROLL"));
            nextRollLabel.setForeground(TEXT_MUTED);
        }

        CombatProfile profile = accountProgressService.getSnapshot();
        if (profile.isReady())
        {
            combatLabel.setText("CB " + profile.getCombatLevel()
                + "  •  ATK " + profile.getAttack()
                + "  STR " + profile.getStrength()
                + "  DEF " + profile.getDefence());
            combatSubLabel.setText("RNG " + profile.getRanged()
                + "  MAG " + profile.getMagic()
                + "  PRAY " + profile.getPrayer());
            skillingLabel.setText("<html><u>SKILLS OPEN " + itemAccessService.countUnlockedSkills()
                + "/" + itemAccessService.getTotalSkillCount() + "</u>  •  VIEW ›</html>");

            profileModeLabel.setText("LIVE ACCOUNT PROGRESSION");
            profileModeLabel.setForeground(TEXT_MUTED);
        }
        else
        {
            combatLabel.setText("Log in to read account progression");
            combatSubLabel.setText("PvM rolls use your combat stats.");
            skillingLabel.setText("<html><u>SKILLS OPEN —/24</u>  •  VIEW ›</html>");
            profileModeLabel.setText(" ");
            profileModeLabel.setForeground(TEXT_MUTED);
        }

        rollButton.setEnabled(!rollInProgress && !pending && points >= cost && profile.isReady());
        resetProgressButton.setEnabled(profile.isReady());
        boolean rerollAvailable = pending && stateService.canRerollPending();
        rerollButton.setEnabled(!rollInProgress && rerollAvailable && profile.isReady());
        rerollButton.setText(rerollAvailable ? "RE-ROLL" : "RE-ROLL");
        rerollHint.setText(rerollAvailable ? "Every other roll  •  READY" : "Every other roll  •  NEXT TIME");
        rerollHint.setForeground(rerollAvailable ? ColorScheme.PROGRESS_COMPLETE_COLOR : TEXT_MUTED);
        if (rollInProgress)
        {
            rollButton.setText("ROLLING CHOICES...");
        }
        else if (pending)
        {
            rollButton.setText("CHOOSE ONE BELOW");
        }
        else
        {
            rollButton.setText("ROLL 3  •  " + format(cost));
        }

        showUnlocksButton.setText("SHOW UNLOCKS  •  " + stateService.getUnlockedItems().size());
        showUnlocksButton.setEnabled(!stateService.getUnlockedItems().isEmpty());

        rebuildChoices();
        rebuildUnlockedItems();
        choiceSection.setVisible(pending);
        // Pending choices temporarily use the space normally occupied by Recent Unlocks.
        // This keeps the Play tab a fixed, non-scrolling RuneLite panel.
        recentSection.setVisible(!pending);

        revalidate();
        repaint();
    }

    private JPanel buildTopChrome()
    {
        JPanel chrome = verticalPanel();

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 1, 7, 1));
        lockHeight(header, 34);

        JLabel title = new JLabel("THREE CHOICES");
        title.setForeground(Color.WHITE);
        title.setFont(FontManager.getRunescapeBoldFont().deriveFont(18f));
        header.add(title, BorderLayout.WEST);

        JLabel mark = new JLabel("III", SwingConstants.CENTER);
        mark.setForeground(ColorScheme.BRAND_ORANGE);
        mark.setFont(FontManager.getRunescapeBoldFont());
        mark.setPreferredSize(new Dimension(34, 27));
        mark.setBorder(BorderFactory.createLineBorder(BORDER));
        header.add(mark, BorderLayout.EAST);
        chrome.add(header);

        JPanel tabs = new JPanel(new GridLayout(1, 2, 4, 0));
        tabs.setOpaque(false);
        tabs.add(playTab);
        tabs.add(welcomeTab);
        lockHeight(tabs, 26);
        chrome.add(tabs);
        chrome.add(Box.createVerticalStrut(8));
        return chrome;
    }

    private JPanel buildPlayView()
    {
        JPanel root = new JPanel(new BorderLayout(0, 5));
        root.setOpaque(false);
        root.setPreferredSize(new Dimension(VIEW_PREFERRED_WIDTH, VIEW_PREFERRED_HEIGHT));

        JPanel body = verticalPanel();

        JPanel balance = cardPanel();
        lockHeight(balance, 90);

        JPanel balanceTop = new JPanel(new GridLayout(1, 2, 8, 0));
        balanceTop.setOpaque(false);
        balanceTop.add(bigMetric(pointsValue, "POINTS"));
        balanceTop.add(bigMetric(unlocksValue, "UNLOCKS"));
        lockHeight(balanceTop, 38);
        balance.add(balanceTop);
        balance.add(Box.createVerticalStrut(4));

        progressBar.setMinimum(0);
        progressBar.setBorderPainted(false);
        progressBar.setStringPainted(false);
        progressBar.setPreferredSize(new Dimension(0, 6));
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
        progressBar.setBackground(ColorScheme.DARK_GRAY_COLOR);
        progressBar.setForeground(ColorScheme.BRAND_ORANGE);
        balance.add(progressBar);
        balance.add(Box.createVerticalStrut(4));
        balance.add(nextRollLabel);
        body.add(balance);
        body.add(Box.createVerticalStrut(5));

        JPanel adaptive = cardPanel();
        lockHeight(adaptive, 108);
        JLabel adaptiveTitle = section("ACCOUNT-MATCHED CHOICES");
        adaptiveTitle.setForeground(ColorScheme.BRAND_ORANGE);
        adaptive.add(adaptiveTitle);
        adaptive.add(Box.createVerticalStrut(3));

        combatLabel.setForeground(Color.WHITE);
        combatLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        combatLabel.setAlignmentX(LEFT_ALIGNMENT);
        adaptive.add(combatLabel);
        adaptive.add(Box.createVerticalStrut(2));

        combatSubLabel.setFont(FontManager.getRunescapeSmallFont());
        adaptive.add(combatSubLabel);
        adaptive.add(Box.createVerticalStrut(2));

        skillingLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        skillingLabel.setForeground(ColorScheme.BRAND_ORANGE);
        skillingLabel.setAlignmentX(LEFT_ALIGNMENT);
        skillingLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        skillingLabel.setToolTipText("Click to view all skill access");
        skillingLabel.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                showSkillStatusDialog();
            }
        });
        adaptive.add(skillingLabel);
        adaptive.add(Box.createVerticalStrut(2));

        profileModeLabel.setFont(FontManager.getRunescapeSmallFont());
        adaptive.add(profileModeLabel);
        body.add(adaptive);
        body.add(Box.createVerticalStrut(5));

        rollButton.setFont(FontManager.getRunescapeBoldFont());
        rollButton.setFocusPainted(false);
        rollButton.setPreferredSize(new Dimension(VIEW_PREFERRED_WIDTH, 32));
        rollButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        rollButton.setAlignmentX(LEFT_ALIGNMENT);
        rollButton.addActionListener(e -> onRollClicked());
        body.add(rollButton);
        body.add(statusLabel);
        body.add(Box.createVerticalStrut(2));

        choiceSection.add(section("YOUR THREE  •  PICK ONE"));
        choiceSection.add(Box.createVerticalStrut(4));

        JPanel rerollRow = new JPanel(new BorderLayout(5, 0));
        rerollRow.setOpaque(false);
        rerollRow.setAlignmentX(LEFT_ALIGNMENT);
        rerollRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        rerollButton.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        rerollButton.setFocusPainted(false);
        rerollButton.setPreferredSize(new Dimension(78, 26));
        rerollButton.setMargin(new Insets(2, 5, 2, 5));
        rerollButton.addActionListener(e -> onRerollClicked());
        rerollRow.add(rerollButton, BorderLayout.WEST);
        rerollHint.setFont(FontManager.getRunescapeSmallFont());
        rerollHint.setHorizontalAlignment(SwingConstants.LEFT);
        rerollRow.add(rerollHint, BorderLayout.CENTER);
        choiceSection.add(rerollRow);
        choiceSection.add(Box.createVerticalStrut(4));
        choiceSection.add(choicesPanel);
        choiceSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 252));
        body.add(choiceSection);
        body.add(Box.createVerticalStrut(4));

        recentSection.add(section("RECENT UNLOCKS"));
        recentSection.add(Box.createVerticalStrut(2));
        recentSection.add(unlockedPanel);
        recentSection.add(Box.createVerticalStrut(3));

        JLabel footer = centeredMuted("Earn points  •  Roll 3  •  Pick 1", 18);
        footer.setFont(FontManager.getRunescapeSmallFont());
        recentSection.add(footer);
        body.add(recentSection);
        body.add(Box.createVerticalGlue());

        // The normal Play tab intentionally has no scroll pane. Recent Unlocks
        // is capped and is hidden while three choices are pending, keeping all
        // primary controls visible in the standard RuneLite sidebar.
        root.add(body, BorderLayout.CENTER);

        showUnlocksButton.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        showUnlocksButton.setFocusPainted(false);
        showUnlocksButton.setPreferredSize(new Dimension(VIEW_PREFERRED_WIDTH, 30));
        showUnlocksButton.addActionListener(e -> showUnlockBrowser());
        root.add(showUnlocksButton, BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildWelcomeView()
    {
        JPanel root = new JPanel(new BorderLayout(0, 7));
        root.setOpaque(false);
        root.setPreferredSize(new Dimension(VIEW_PREFERRED_WIDTH, VIEW_PREFERRED_HEIGHT));

        JPanel body = verticalPanel();

        JPanel intro = cardPanel();
        lockHeight(intro, 116);
        JLabel eyebrow = section("WELCOME TO THREE CHOICES");
        eyebrow.setForeground(ColorScheme.BRAND_ORANGE);
        intro.add(eyebrow);
        intro.add(Box.createVerticalStrut(4));

        JLabel headline = new JLabel("THREE OPTIONS. ONE STAYS.");
        headline.setForeground(Color.WHITE);
        headline.setFont(FontManager.getRunescapeBoldFont().deriveFont(14f));
        headline.setAlignmentX(LEFT_ALIGNMENT);
        intro.add(headline);
        intro.add(Box.createVerticalStrut(4));
        intro.add(wrappedText(
            "Start restricted and earn points<br>just by playing. Fill the meter,<br>roll three useful choices, then<br>pick one permanent unlock.",
            CARD_TEXT_WIDTH,
            64,
            TEXT_MUTED));
        body.add(intro);
        body.add(Box.createVerticalStrut(6));

        body.add(stepCard("1", "EARN POINTS",
            "Train unlocked skills, fight,<br>boss and quest. It all pushes<br>you toward the next roll."));
        body.add(Box.createVerticalStrut(5));
        body.add(stepCard("2", "ROLL THREE",
            "Your stats and current unlocks<br>shape the three choices you're<br>offered."));
        body.add(Box.createVerticalStrut(5));
        body.add(stepCard("3", "KEEP ONE",
            "Pick one permanently.<br>The other two are passed over<br>and may show up again later."));
        body.add(Box.createVerticalStrut(6));

        JPanel rules = cardPanel();
        lockHeight(rules, 122);
        JLabel rulesTitle = wrappedText("START LOCKED. BUILD<br>YOUR OWN ACCOUNT.", CARD_TEXT_WIDTH, 30, ColorScheme.PROGRESS_COMPLETE_COLOR);
        rulesTitle.setFont(FontManager.getRunescapeBoldFont().deriveFont(11f));
        rules.add(rulesTitle);
        rules.add(Box.createVerticalStrut(4));
        rules.add(wrappedText(
            "Woodcutting and Firemaking start<br>open. Combat starts open except Prayer.<br>Mining, Fishing, the other skills and<br>progression gear must be opened<br>through your choices.",
            CARD_TEXT_WIDTH,
            72,
            TEXT_MUTED));
        body.add(rules);

        // The Welcome page is deliberately a single fixed RuneLite panel, just
        // like Play. Compact cards instead of a nested scrollbar keep the full
        // explanation and START PLAYING visible at normal sidebar heights.
        root.add(body, BorderLayout.CENTER);

        JButton start = new JButton("START PLAYING");
        start.setFont(FontManager.getRunescapeBoldFont());
        start.setFocusPainted(false);
        start.setPreferredSize(new Dimension(VIEW_PREFERRED_WIDTH, 36));
        start.addActionListener(e ->
        {
            stateService.setWelcomeSeen(true);
            showView(VIEW_PLAY);
            refresh();
        });
        root.add(start, BorderLayout.SOUTH);
        return root;
    }

    private JPanel stepCard(String number, String title, String description)
    {
        JPanel card = new JPanel(new BorderLayout(8, 0));
        card.setBackground(CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            new EmptyBorder(7, 8, 7, 8)));
        card.setPreferredSize(new Dimension(VIEW_PREFERRED_WIDTH, 74));
        card.setMinimumSize(new Dimension(0, 74));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 74));
        card.setAlignmentX(LEFT_ALIGNMENT);

        JLabel badge = new JLabel(number, SwingConstants.CENTER);
        badge.setForeground(ColorScheme.BRAND_ORANGE);
        badge.setFont(FontManager.getRunescapeBoldFont());
        badge.setPreferredSize(new Dimension(27, 27));
        badge.setMinimumSize(new Dimension(27, 27));
        badge.setMaximumSize(new Dimension(27, 27));
        badge.setBorder(BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE));
        card.add(badge, BorderLayout.WEST);

        JPanel text = verticalPanel();
        text.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        JLabel heading = section(title);
        heading.setForeground(Color.WHITE);
        text.add(heading);
        text.add(Box.createVerticalStrut(2));
        text.add(wrappedText(description, STEP_TEXT_WIDTH, 52, TEXT_MUTED));
        card.add(text, BorderLayout.CENTER);
        return card;
    }

    private void onRollClicked()
    {
        if (rollInProgress)
        {
            return;
        }

        rollInProgress = true;
        setStatus("Finding three useful choices for your account...", TEXT_MUTED);
        refresh();

        clientThread.invokeLater(() ->
        {
            RollService.RollStatus result = rollService.rollThree();
            SwingUtilities.invokeLater(() -> handleRollResult(result));
        });
    }

    private void onRerollClicked()
    {
        if (rollInProgress || !stateService.canRerollPending())
        {
            return;
        }

        rollInProgress = true;
        setStatus("Re-rolling your three choices...", TEXT_MUTED);
        refresh();
        clientThread.invokeLater(() ->
        {
            RollService.RollStatus result = rollService.rerollThree();
            SwingUtilities.invokeLater(() ->
            {
                rollInProgress = false;
                if (result == RollService.RollStatus.SUCCESS)
                {
                    setStatus("Three new choices ready. Next roll cannot re-roll.", ColorScheme.BRAND_ORANGE);
                }
                else if (result == RollService.RollStatus.REROLL_NOT_AVAILABLE)
                {
                    setStatus("Re-roll is available every other roll.", TEXT_MUTED);
                }
                else
                {
                    handleRollResult(result);
                    return;
                }
                refresh();
            });
        });
    }

    private void handleRollResult(RollService.RollStatus result)
    {
        rollInProgress = false;
        switch (result)
        {
            case SUCCESS:
                setStatus("Three choices ready. Pick one to keep.", ColorScheme.BRAND_ORANGE);
                break;
            case REROLL_NOT_AVAILABLE:
                setStatus("Re-roll is available every other roll.", TEXT_MUTED);
                break;
            case PENDING_ROLL:
                setStatus("Choose from your current three first.", TEXT_MUTED);
                break;
            case NOT_ENOUGH_POINTS:
                setStatus("Not enough points for another roll yet.", TEXT_MUTED);
                break;
            case NOT_LOGGED_IN:
                setStatus("Log in before rolling.", TEXT_MUTED);
                break;
            case ITEM_DATA_LOADING:
                setStatus("RuneLite item data is still loading. Try again shortly.", TEXT_MUTED);
                break;
            case NO_ELIGIBLE_ITEMS:
                setStatus("No useful progression items matched yet.", ColorScheme.PROGRESS_ERROR_COLOR);
                break;
            case FAILED:
            default:
                setStatus("Roll failed safely — no points were spent.", ColorScheme.PROGRESS_ERROR_COLOR);
                break;
        }
        refresh();
    }

    private void rebuildChoices()
    {
        choicesPanel.removeAll();
        List<Integer> pending = stateService.getPendingRoll();
        if (pending.size() != 3)
        {
            choicesPanel.setPreferredSize(new Dimension(VIEW_PREFERRED_WIDTH, 0));
            choicesPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 0));
            return;
        }

        for (int i = 0; i < pending.size(); i++)
        {
            choicesPanel.add(buildChoiceCard(pending.get(i)));
            if (i < pending.size() - 1)
            {
                choicesPanel.add(Box.createVerticalStrut(5));
            }
        }
        choicesPanel.setPreferredSize(new Dimension(VIEW_PREFERRED_WIDTH, 196));
        choicesPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 196));
    }

    private JPanel buildChoiceCard(int itemId)
    {
        RollCandidate candidate = itemPool.describeExistingCached(itemId);

        JPanel card = new JPanel(new BorderLayout(3, 0));
        card.setBackground(CARD_LIGHT);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 3, 0, 0, candidateColor(candidate)),
            new EmptyBorder(5, 4, 5, 4)));
        card.setPreferredSize(new Dimension(VIEW_PREFERRED_WIDTH, 62));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        card.setAlignmentX(LEFT_ALIGNMENT);

        JLabel icon = new JLabel();
        icon.setPreferredSize(new Dimension(28, 34));
        AsyncBufferedImage image = itemManager.getImage(itemId);
        if (image != null)
        {
            image.addTo(icon);
        }
        card.add(icon, BorderLayout.WEST);

        JPanel text = verticalPanel();
        JLabel name = fixedHtml(
            candidate.getName(),
            80,
            28,
            SwingConstants.LEFT,
            Color.WHITE,
            FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        text.add(name);

        StringBuilder meta = new StringBuilder(
            candidate.getCategory().isShiny() ? candidate.getCategory().getDisplayName()
                : candidate.isSkilling() && candidate.getSkillGate() != null
                    ? candidate.getSkillGate().getDisplayName() : candidate.getStyle().getDisplayName());
        if (candidate.getPrice() > 0)
        {
            meta.append("  •  ").append(format(candidate.getPrice()));
        }
        JLabel detail = fixedHtml(
            meta.toString(),
            80,
            18,
            SwingConstants.LEFT,
            TEXT_MUTED,
            FontManager.getRunescapeSmallFont());
        text.add(detail);
        card.add(text, BorderLayout.CENTER);

        JButton choose = new JButton("PICK");
        choose.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        choose.setFocusPainted(false);
        choose.setPreferredSize(new Dimension(52, 28));
        choose.setMargin(new Insets(2, 4, 2, 4));
        choose.addActionListener(e ->
        {
            choose.setEnabled(false);
            setStatus("Locking in your choice...", TEXT_MUTED);
            clientThread.invokeLater(() ->
            {
                boolean saved = rollService.choose(itemId);
                SwingUtilities.invokeLater(() ->
                {
                    if (saved)
                    {
                        setStatus(candidate.getName() + " permanently unlocked.", ColorScheme.PROGRESS_COMPLETE_COLOR);
                    }
                    else
                    {
                        setStatus("That choice could not be saved.", ColorScheme.PROGRESS_ERROR_COLOR);
                    }
                    refresh();
                });
            });
        });
        card.add(choose, BorderLayout.EAST);
        return card;
    }

    private void rebuildUnlockedItems()
    {
        unlockedPanel.removeAll();
        if (stateService.getUnlockedItems().isEmpty())
        {
            JLabel empty = centeredMuted("No permanent unlocks yet.", 26);
            unlockedPanel.add(empty);
            unlockedPanel.setPreferredSize(new Dimension(VIEW_PREFERRED_WIDTH, 26));
            unlockedPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
            return;
        }

        List<Integer> ids = new ArrayList<>(stateService.getUnlockedItems());
        Collections.reverse(ids);
        int shown = Math.min(ids.size(), MAX_UNLOCKS_SHOWN);
        for (int i = 0; i < shown; i++)
        {
            unlockedPanel.add(unlockRow(ids.get(i)));
        }

        int height = shown * 40;
        unlockedPanel.setPreferredSize(new Dimension(VIEW_PREFERRED_WIDTH, height));
        unlockedPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
    }

    private JPanel unlockRow(int itemId)
    {
        RollCandidate candidate = itemPool.describeExistingCached(itemId);
        JPanel row = new JPanel(new BorderLayout(5, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(2, 1, 2, 1));
        row.setPreferredSize(new Dimension(VIEW_PREFERRED_WIDTH, 40));
        row.setMinimumSize(new Dimension(0, 40));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JLabel icon = new JLabel();
        icon.setPreferredSize(new Dimension(40, 36));
        icon.setMinimumSize(new Dimension(40, 36));
        AsyncBufferedImage image = itemManager.getImage(itemId);
        if (image != null) image.addTo(icon);
        row.add(icon, BorderLayout.WEST);

        JLabel name = fixedHtml(candidate.getName(), 120, 30, SwingConstants.LEFT,
            ColorScheme.TEXT_COLOR, FontManager.getRunescapeSmallFont());
        row.add(name, BorderLayout.CENTER);

        JLabel check = new JLabel("✓");
        check.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
        row.add(check, BorderLayout.EAST);
        return row;
    }

    private static String skillDisplayName(Skill skill)
    {
        SkillGate gate = SkillGate.fromSkill(skill);
        if (gate != null) return gate.getDisplayName();
        String raw = skill.name().toLowerCase();
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private void showSkillStatusDialog()
    {
        closeTransientWindows();
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, "Three Choices — Skills", Dialog.ModalityType.MODELESS);
        skillStatusDialog = dialog;
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(6, 8));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel title = section("SKILLS OPEN " + itemAccessService.countUnlockedSkills() + "/24");
        title.setForeground(ColorScheme.BRAND_ORANGE);
        content.add(title, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(12, 2, 6, 4));
        grid.setOpaque(false);
        for (Skill skill : DISPLAY_SKILLS)
        {
            JPanel cell = new JPanel(new BorderLayout(4, 0));
            cell.setBackground(CARD);
            cell.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), new EmptyBorder(4, 5, 4, 5)));

            JLabel name = new JLabel(skillDisplayName(skill));
            name.setFont(FontManager.getRunescapeSmallFont());
            name.setForeground(ColorScheme.TEXT_COLOR);
            cell.add(name, BorderLayout.CENTER);

            boolean open = itemAccessService.isSkillOpen(skill);
            JLabel status = new JLabel(open ? "OPEN" : "LOCKED");
            status.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
            status.setForeground(open ? ColorScheme.PROGRESS_COMPLETE_COLOR : TEXT_MUTED);
            cell.add(status, BorderLayout.EAST);
            grid.add(cell);
        }
        content.add(grid, BorderLayout.CENTER);

        dialog.setContentPane(content);
        dialog.setSize(430, 390);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private void showUnlockBrowser()
    {
        closeTransientWindows();
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, "Three Choices — Unlocks", Dialog.ModalityType.MODELESS);
        unlockBrowserDialog = dialog;
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(6, 6));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.setBorder(new EmptyBorder(10, 10, 10, 10));

        JTextField search = new JTextField();
        search.setToolTipText("Search unlocked items");
        content.add(search, BorderLayout.NORTH);

        JPanel rows = verticalPanel();
        JScrollPane scroll = new JScrollPane(rows);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        content.add(scroll, BorderLayout.CENTER);

        Runnable rebuild = () -> rebuildUnlockBrowserRows(rows, search.getText());
        search.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override public void insertUpdate(DocumentEvent e) { rebuild.run(); }
            @Override public void removeUpdate(DocumentEvent e) { rebuild.run(); }
            @Override public void changedUpdate(DocumentEvent e) { rebuild.run(); }
        });
        rebuild.run();

        dialog.setContentPane(content);
        dialog.setSize(330, 470);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        search.requestFocusInWindow();
    }

    private void rebuildUnlockBrowserRows(JPanel rows, String rawQuery)
    {
        rows.removeAll();
        String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase();
        List<Integer> ids = new ArrayList<>(stateService.getUnlockedItems());
        Collections.reverse(ids);
        int matches = 0;
        for (int itemId : ids)
        {
            RollCandidate candidate = itemPool.describeExistingCached(itemId);
            if (!query.isEmpty() && !candidate.getName().toLowerCase().contains(query)) continue;
            rows.add(unlockRow(itemId));
            matches++;
        }
        if (matches == 0)
        {
            rows.add(centeredMuted(query.isEmpty() ? "No unlocks yet." : "No matching unlocks.", 30));
        }
        rows.revalidate();
        rows.repaint();
    }

    public void closeTransientWindows()
    {
        if (unlockBrowserDialog != null)
        {
            unlockBrowserDialog.dispose();
            unlockBrowserDialog = null;
        }
        if (skillStatusDialog != null)
        {
            skillStatusDialog.dispose();
            skillStatusDialog = null;
        }
    }

    private void showView(String view)
    {
        activeView = VIEW_WELCOME.equals(view) ? VIEW_WELCOME : VIEW_PLAY;
        viewLayout.show(viewContainer, activeView);
        styleTab(playTab, VIEW_PLAY.equals(activeView));
        styleTab(welcomeTab, VIEW_WELCOME.equals(activeView));
    }

    String getActiveViewForTesting()
    {
        return activeView;
    }

    private void setStatus(String text, Color color)
    {
        statusLabel.setText(htmlCenter(text == null || text.isBlank() ? " " : text));
        statusLabel.setForeground(color);
    }

    private static JPanel bigMetric(JLabel value, String caption)
    {
        JPanel panel = verticalPanel();
        lockHeight(panel, 41);

        value.setForeground(Color.WHITE);
        value.setFont(FontManager.getRunescapeBoldFont().deriveFont(17f));
        value.setHorizontalAlignment(SwingConstants.CENTER);
        value.setAlignmentX(CENTER_ALIGNMENT);
        panel.add(value);

        JLabel label = muted(caption);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setAlignmentX(CENTER_ALIGNMENT);
        panel.add(label);
        return panel;
    }

    private static JPanel cardPanel()
    {
        JPanel panel = verticalPanel();
        panel.setBackground(CARD);
        panel.setOpaque(true);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            new EmptyBorder(9, 9, 9, 9)));
        panel.setPreferredSize(new Dimension(VIEW_PREFERRED_WIDTH, 80));
        return panel;
    }

    private static JPanel verticalPanel()
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return panel;
    }

    private static JLabel section(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setFont(FontManager.getRunescapeBoldFont().deriveFont(11f));
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private static JLabel muted(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT_MUTED);
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private static JLabel centeredMuted(String text, int height)
    {
        JLabel label = fixedHtml(text, WIDE_TEXT_WIDTH, height, SwingConstants.CENTER, TEXT_MUTED,
            FontManager.getRunescapeSmallFont());
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private static JLabel wrappedText(String text, int width, int height, Color color)
    {
        JLabel label = new JLabel("<html><div style='width:" + width + "px;'>" + text + "</div></html>");
        label.setForeground(color);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setPreferredSize(new Dimension(width, height));
        label.setMinimumSize(new Dimension(0, height));
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private static JLabel fixedHtml(
        String text,
        int width,
        int height,
        int horizontalAlignment,
        Color color,
        Font font)
    {
        String align = horizontalAlignment == SwingConstants.CENTER ? "center" : "left";
        JLabel label = new JLabel("<html><div style='text-align:" + align + ";'>" + text + "</div></html>");
        label.setForeground(color);
        label.setFont(font);
        label.setHorizontalAlignment(horizontalAlignment);
        label.setPreferredSize(new Dimension(width, height));
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private static String htmlCenter(String text)
    {
        return "<html><div style='text-align:center;'>" + text + "</div></html>";
    }

    private static JButton tabButton(String text)
    {
        JButton button = new JButton(text);
        button.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        button.setFocusPainted(false);
        button.setPreferredSize(new Dimension(0, 26));
        return button;
    }

    private static void styleTab(JButton button, boolean active)
    {
        button.setForeground(active ? Color.WHITE : TEXT_MUTED);
        button.setBackground(active ? ColorScheme.MEDIUM_GRAY_COLOR : ColorScheme.DARKER_GRAY_COLOR);
        button.setBorder(BorderFactory.createMatteBorder(0, 0, active ? 2 : 1, 0,
            active ? ColorScheme.BRAND_ORANGE : BORDER));
    }

    private static void lockHeight(javax.swing.JComponent component, int height)
    {
        component.setPreferredSize(new Dimension(VIEW_PREFERRED_WIDTH, height));
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        component.setAlignmentX(LEFT_ALIGNMENT);
    }

    private static Color candidateColor(RollCandidate candidate)
    {
        if (candidate.getCategory().isShiny())
        {
            return new Color(238, 184, 55);
        }
        if (!candidate.isSkilling())
        {
            return styleColor(candidate.getStyle());
        }
        SkillGate gate = candidate.getSkillGate();
        if (gate == SkillGate.WOODCUTTING) return new Color(88, 154, 82);
        if (gate == SkillGate.MINING) return new Color(155, 155, 165);
        if (gate == SkillGate.FISHING) return new Color(72, 145, 205);
        return new Color(200, 155, 65);
    }

    private static Color styleColor(CombatStyle style)
    {
        switch (style)
        {
            case RANGED:
                return new Color(78, 170, 90);
            case MAGIC:
                return new Color(96, 135, 220);
            case MELEE:
                return new Color(205, 95, 80);
            case GENERAL:
            default:
                return ColorScheme.BRAND_ORANGE;
        }
    }

    private static String format(long amount)
    {
        return QuantityFormatter.quantityToStackSize(Math.max(0L, amount));
    }
}
