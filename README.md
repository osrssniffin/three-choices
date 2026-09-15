# Three Choices

Three Choices is a restricted-account game mode for RuneLite. You earn points by playing, spend 3,000 points to roll three progression options, and permanently keep one of them.

The account starts small on purpose. Woodcutting and Firemaking are open from the start, along with the normal combat skills except Prayer. Mining, Fishing, Prayer, and the rest of the gated skills have to be opened through rolls. Progression gear works the same way: if an item belongs to the roll system, owning it early does not make it usable.

## How it works

1. Play the account and earn points from eligible XP, quests, kills, clues, and larger PvM completions.
2. At 3,000 points, roll three choices matched to what your account can realistically use next.
3. Pick one. That item, skill opener, bundle, or training method becomes a permanent unlock.
4. The other two are passed over and can show up again later.
5. Keep building the account from the choices you have earned.

Point gain slows as more rolls are completed. The first few choices arrive quickly enough to get the account moving, while later rolls take more work. Combat earns points at a reduced rate so it contributes without becoming the obvious way to farm rolls.

A free re-roll is available every other roll. Using it replaces all three current choices and starts the one-roll cooldown.

## Restrictions

Three Choices enforces the mode on the client rather than relying on self-policing. Locked skills cannot be trained, Prayer cannot be activated before it is opened, player trading is disabled, and locked progression items cannot be bought, picked up for use, equipped, or operated until they are unlocked.

Tutorial Island starter items remain available, but the starter bronze pickaxe and small fishing net do not open Mining or Fishing. Those skills still need their own progression choice.

Untradeable rewards that are not part of the tradeable roll pool can still be earned normally through quests, minigames, bosses, and other in-game content.

## Resetting

The side panel has a **Reset Progress** button at the bottom. Resetting requires a confirmation and clears the Three Choices profile back to its fresh starting state. It does not use a chat command.

## Development

The project follows RuneLite's standalone Plugin Hub layout and targets Java 11.

```text
gradle clean test
gradle run
```

`gradle run` starts the RuneLite developer client through `ThreeChoicesPluginTest` for manual testing.
