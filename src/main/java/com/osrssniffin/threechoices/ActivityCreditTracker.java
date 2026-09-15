package com.osrssniffin.threechoices;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.util.Text;

@Singleton
public class ActivityCreditTracker
{
    private static final Set<ChatMessageType> CREDIT_CHAT_TYPES = EnumSet.of(
        ChatMessageType.GAMEMESSAGE,
        ChatMessageType.SPAM
    );

    // These values are the old 50,000-point economy rewards scaled to the
    // fixed 3,000-point roll economy (3,000 / 50,000 = 0.06). Their relative
    // value is preserved, but no ordinary completion can print several rolls.
    private static final List<Rule> RULES = buildRules();

    private final ThreeChoicesStateService stateService;
    private final ProgressionEconomyService economyService;

    @Inject
    public ActivityCreditTracker(
        ThreeChoicesStateService stateService,
        ProgressionEconomyService economyService)
    {
        this.stateService = stateService;
        this.economyService = economyService;
    }

    public boolean onChatMessage(ChatMessage event)
    {
        if (event == null || !CREDIT_CHAT_TYPES.contains(event.getType()) || event.getMessage() == null)
        {
            return false;
        }

        String message = Text.removeTags(event.getMessage());
        long basePoints = basePointsForMessage(message);
        if (basePoints <= 0L)
        {
            return false;
        }

        stateService.addEarnedPoints(economyService.scaleAward(basePoints));
        return true;
    }

    static long basePointsForMessage(String message)
    {
        if (message == null || message.isBlank()) return 0L;
        for (Rule rule : RULES)
        {
            if (rule.matches(message)) return rule.points;
        }
        return 0L;
    }

    private static List<Rule> buildRules()
    {
        List<Rule> rules = new ArrayList<>();

        rules.add(Rule.prefix("Your completed Chambers of Xeric Challenge Mode count is:", 1_110L));
        rules.add(Rule.prefix("Your completed Chambers of Xeric count is:", 750L));
        rules.add(Rule.prefix("Your completed Theatre of Blood: Hard Mode count is:", 1_110L));
        rules.add(Rule.prefix("Your completed Theatre of Blood: Entry Mode count is:", 210L));
        rules.add(Rule.prefix("Your completed Theatre of Blood count is:", 750L));
        rules.add(Rule.prefix("Your completed Tombs of Amascut: Expert Mode count is:", 1_110L));
        rules.add(Rule.prefix("Your completed Tombs of Amascut: Entry Mode count is:", 210L));
        rules.add(Rule.prefix("Your completed Tombs of Amascut count is:", 750L));
        rules.add(Rule.prefix("Your Corrupted Gauntlet completion count is:", 270L));
        rules.add(Rule.prefix("Your Gauntlet completion count is:", 105L));
        rules.add(Rule.prefix("Your TzKal-Zuk kill count is:", 1_500L));
        rules.add(Rule.prefix("Your TzTok-Jad kill count is:", 600L));

        rules.add(treasureTrail("beginner", 30L));
        rules.add(treasureTrail("easy", 60L));
        rules.add(treasureTrail("medium", 120L));
        rules.add(treasureTrail("hard", 180L));
        rules.add(treasureTrail("elite", 240L));
        rules.add(treasureTrail("master", 300L));

        return List.copyOf(rules);
    }

    private static Rule treasureTrail(String difficulty, long points)
    {
        Pattern pattern = Pattern.compile("^You have completed \\d+ " + Pattern.quote(difficulty) + " Treasure Trails?\\.$");
        return Rule.pattern(pattern, points);
    }

    private static final class Rule
    {
        private final String prefix;
        private final Pattern pattern;
        private final long points;

        private Rule(String prefix, Pattern pattern, long points)
        {
            this.prefix = prefix;
            this.pattern = pattern;
            this.points = points;
        }

        private static Rule prefix(String prefix, long points)
        {
            return new Rule(prefix, null, points);
        }

        private static Rule pattern(Pattern pattern, long points)
        {
            return new Rule(null, pattern, points);
        }

        private boolean matches(String message)
        {
            return prefix != null ? message.startsWith(prefix) : pattern.matcher(message).matches();
        }
    }
}
