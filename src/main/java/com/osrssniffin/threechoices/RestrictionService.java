package com.osrssniffin.threechoices;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.GrandExchangeSearched;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;

/** Client-side enforcement for the Three Choices ruleset. */
@Singleton
public class RestrictionService
{
    private static final int MAX_GE_RESULTS = 250;

    public static final class BlockResult
    {
        private final boolean blocked;
        private final String message;

        private BlockResult(boolean blocked, String message)
        {
            this.blocked = blocked;
            this.message = message;
        }

        public boolean isBlocked() { return blocked; }
        public String getMessage() { return message; }
        static BlockResult allow() { return new BlockResult(false, ""); }
        static BlockResult block(String message) { return new BlockResult(true, message); }
    }

    private final Client client;
    private final ItemManager itemManager;
    private final ItemAccessService itemAccessService;

    @Inject
    public RestrictionService(Client client, ItemManager itemManager, ItemAccessService itemAccessService)
    {
        this.client = client;
        this.itemManager = itemManager;
        this.itemAccessService = itemAccessService;
    }

    /**
     * Replace vanilla GE search results with the strict Three Choices allowlist.
     * A rollable item is completely absent until that exact item is chosen.
     */
    public void filterGrandExchangeSearch(GrandExchangeSearched event)
    {
        if (event.isConsumed()) return;

        String input = client.getVarcStrValue(VarClientID.MESLAYERINPUT);
        if (input == null || input.isBlank()) return;

        List<ItemPrice> prices = new ArrayList<>(itemManager.search(input));
        prices.sort(Comparator.comparing(p -> p.getName() == null ? "" : p.getName(), String.CASE_INSENSITIVE_ORDER));

        List<Short> ids = new ArrayList<>(MAX_GE_RESULTS);
        for (ItemPrice price : prices)
        {
            if (ids.size() >= MAX_GE_RESULTS) break;

            int itemId = price.getId();
            try
            {
                ItemComposition composition = itemManager.getItemComposition(itemId);
                if (composition == null || !composition.isGeTradeable() || composition.getNote() != -1)
                {
                    continue;
                }
            }
            catch (RuntimeException ignored)
            {
                continue;
            }

            if (itemAccessService.isGeAllowed(itemId)) ids.add((short) itemId);
        }

        short[] result = new short[ids.size()];
        for (int i = 0; i < ids.size(); i++) result[i] = ids.get(i);

        event.consume();
        client.setGeSearchResultIndex(0);
        client.setGeSearchResultCount(result.length);
        client.setGeSearchResultIds(result);
    }

    public boolean isTradeEntry(MenuEntry entry)
    {
        if (entry == null) return false;
        String option = normalizeOption(entry.getOption());
        if (option.startsWith("accept trade") || option.startsWith("trade with")) return true;

        int interfaceId = interfaceIdOf(entry.getParam1());
        return isTradeInterface(interfaceId) && !isTradeExitOption(option);
    }

    public BlockResult shouldBlock(MenuOptionClicked event)
    {
        MenuEntry entry = event.getMenuEntry();
        String option = normalizeOption(event.getMenuOption());
        int interfaceId = interfaceIdOf(event.getParam1());
        if (isTradeEntry(entry) || option.startsWith("accept trade")
            || (isTradeInterface(interfaceId) && !isTradeExitOption(option)))
        {
            return BlockResult.block("Player trading is disabled in Three Choices.");
        }

        if (!itemAccessService.isSkillUnlocked(SkillGate.PRAYER) && isPrayerActivation(entry))
        {
            return BlockResult.block(skillLockedReason(SkillGate.PRAYER));
        }

        MenuAction menuAction = event.getMenuAction();
        if (isGroundItemAction(menuAction))
        {
            int groundItemId = event.getId();
            boolean acquisitionAttempt = option.equals("take") || menuAction == MenuAction.WIDGET_TARGET_ON_GROUND_ITEM;
            if (acquisitionAttempt && groundItemId > 0 && !itemAccessService.isGroundPickupAllowed(groundItemId))
            {
                return BlockResult.block(itemAccessService.lockedReason(groundItemId));
            }
        }

        SkillGate gate = skillGateForAction(event.getMenuOption(), event.getMenuTarget());
        if (gate != null && !itemAccessService.isSkillUnlocked(gate))
        {
            return BlockResult.block(skillLockedReason(gate));
        }
        if (isLockedSlayerCombat(entry))
        {
            return BlockResult.block(skillLockedReason(SkillGate.SLAYER));
        }

        // Existing locked equipment is another acquisition/use loophole: a
        // player could enable the mode while already wearing strong gear and
        // continue benefiting from it without ever selecting that unlock. We
        // cannot force a server-side unequip, so block combat/training actions
        // until the locked equipment is removed. Remove/unequip itself remains
        // an allowed management action.
        if (gate != null || option.startsWith("attack") || option.startsWith("cast"))
        {
            int lockedEquipped = firstLockedEquippedItem();
            if (lockedEquipped > 0)
            {
                return BlockResult.block("Unequip locked gear before training or fighting. "
                    + itemAccessService.lockedReason(lockedEquipped));
            }
        }

        int selectedLockedItem = selectedLockedItemId();
        if (selectedLockedItem > 0 && isWidgetTargetAction(menuAction))
        {
            return BlockResult.block(itemAccessService.lockedReason(selectedLockedItem));
        }

        if (normalizeOption(event.getMenuOption()).equals("confirm") && currentGeBuyItemIsLocked())
        {
            int geItem = client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH);
            return BlockResult.block(itemAccessService.lockedReason(geItem));
        }

        int itemId = entry == null
            ? resolveItemId(event.getItemId(), event.getWidget())
            : resolveItemId(entry.getItemId() > 0 ? entry.getItemId() : event.getItemId(), event.getWidget());

        SkillGate itemUseGate = skillGateForItemUse(itemId, option, event.getMenuTarget());
        if (itemUseGate != null && !itemAccessService.isSkillUnlocked(itemUseGate))
        {
            return BlockResult.block(skillLockedReason(itemUseGate));
        }

        // GE item selection and shop purchase both use the strict acquisition rule.
        if (itemId > 0 && (option.equals("select") || option.startsWith("select "))
            && !itemAccessService.isGeAllowed(itemId))
        {
            return BlockResult.block(itemAccessService.lockedReason(itemId));
        }
        if (itemId > 0 && option.startsWith("buy") && !itemAccessService.isAcquisitionAllowed(itemId))
        {
            return BlockResult.block(itemAccessService.lockedReason(itemId));
        }

        if (itemId <= 0 || !itemAccessService.isLocked(itemId)) return BlockResult.allow();

        if (!isAllowedManagementOption(option)
            && ((entry != null && entry.isItemOp()) || option.equals("use") || option.startsWith("use ")
                || isInventorySide(interfaceId)))
        {
            return BlockResult.block(itemAccessService.lockedReason(itemId));
        }

        return BlockResult.allow();
    }

    private int firstLockedEquippedItem()
    {
        try
        {
            ItemContainer worn = client.getItemContainer(InventoryID.WORN);
            if (worn == null || worn.getItems() == null) return -1;
            for (Item item : worn.getItems())
            {
                if (item != null && item.getId() > 0 && itemAccessService.isLocked(item.getId()))
                {
                    return item.getId();
                }
            }
        }
        catch (RuntimeException ignored)
        {
            // Fail without breaking unrelated menu actions if the equipment
            // container is temporarily unavailable during login/hop.
        }
        return -1;
    }

    private SkillGate skillGateForItemUse(int itemId, String option, String rawTarget)
    {
        if (itemId <= 0 || !(option.equals("use") || option.startsWith("use ")))
        {
            return null;
        }

        String itemName;
        try
        {
            ItemComposition composition = itemManager.getItemComposition(itemId);
            itemName = composition == null ? "" : ProgressionRules.normalize(composition.getName());
        }
        catch (RuntimeException ignored)
        {
            return null;
        }
        return skillGateForItemUseNames(itemName, normalizeTarget(rawTarget));
    }

    /** Pure name matcher used by runtime enforcement and regression tests. */
    static SkillGate skillGateForItemUseNames(String rawItemName, String rawTarget)
    {
        String itemName = ProgressionRules.normalize(rawItemName);
        String target = normalizeTarget(rawTarget);
        if (itemName.isEmpty()) return null;

        // Prayer: bones/ashes on altars, shrines and other sacrifice targets.
        if (ProgressionRules.resourceGates(itemName).contains(SkillGate.PRAYER)
            && containsAny(target, "altar", "shrine", "ectofuntus", "libation", "sacrificial", "bone crusher"))
        {
            return SkillGate.PRAYER;
        }

        // Cooking: wine is a direct item-on-item method; other raw food/ingredients
        // are caught when used on normal cooking objects.
        if ((itemName.equals("grapes") && target.contains("jug of water"))
            || (itemName.equals("jug of water") && target.contains("grapes")))
        {
            return SkillGate.COOKING;
        }
        if (containsAny(target, "range", "stove", "fire", "sulphur vent", "cooking pot", "oven")
            && (itemName.startsWith("raw ") || itemName.equals("jug of water") || itemName.equals("grapes")
                || ProgressionRules.resourceGates(itemName).contains(SkillGate.COOKING)))
        {
            return SkillGate.COOKING;
        }

        // Firemaking: either side of the classic tinderbox + logs interaction.
        if ((itemName.equals("tinderbox") && (target.equals("logs") || target.endsWith(" logs")))
            || ((itemName.equals("logs") || itemName.endsWith(" logs")) && target.contains("tinderbox")))
        {
            return SkillGate.FIREMAKING;
        }

        // Fletching: knives on logs and unfinished/finished ammo assembly.
        if ((containsAny(itemName, "knife") && (target.equals("logs") || target.endsWith(" logs")))
            || ((itemName.equals("logs") || itemName.endsWith(" logs")) && target.contains("knife"))
            || (ProgressionRules.resourceGates(itemName).contains(SkillGate.FLETCHING)
                && containsAny(target, "arrow", "dart", "bolt", "javelin", "feather", "shaft", "bow string", "unfinished", "unstrung", "(u)", " bow")))
        {
            return SkillGate.FLETCHING;
        }

        // Herblore: herbs, vials, unfinished potions and secondaries used together.
        if (ProgressionRules.resourceGates(itemName).contains(SkillGate.HERBLORE)
            && (ProgressionRules.resourceGates(target).contains(SkillGate.HERBLORE)
                || containsAny(target, "vial", "potion", "grimy", "herb", "root", "berries", "grass", "eggs",
                    "cactus", "horn", "nest", "crystal", "mushroom", "dust")))
        {
            return SkillGate.HERBLORE;
        }

        // Smithing and Crafting share furnaces. Ores/coal are Smithing;
        // gold/silver bars used on a furnace are jewellery Crafting. Other bars
        // at an anvil (or steel bars used for cannonballs) remain Smithing.
        if (containsAny(target, "furnace", "blast furnace")
            && (itemName.endsWith(" ore") || itemName.equals("coal")))
        {
            return SkillGate.SMITHING;
        }
        if (target.contains("anvil") && itemName.endsWith(" bar"))
        {
            return SkillGate.SMITHING;
        }
        if (containsAny(target, "furnace", "blast furnace")
            && (itemName.equals("gold bar") || itemName.equals("silver bar")
                || itemName.equals("molten glass") || itemName.equals("soda ash") || itemName.equals("bucket of sand")))
        {
            return SkillGate.CRAFTING;
        }
        if (target.contains("furnace") && itemName.equals("steel bar"))
        {
            return SkillGate.SMITHING;
        }
        if (containsAny(target, "potter's wheel", "pottery oven", "spinning wheel", "loom")
            && ProgressionRules.resourceGates(itemName).contains(SkillGate.CRAFTING))
        {
            return SkillGate.CRAFTING;
        }
        if ((itemName.equals("chisel") && containsAny(target, "uncut", "gem", "amethyst"))
            || (itemName.equals("needle") && target.contains("leather"))
            || (itemName.equals("glassblowing pipe") && target.contains("molten glass"))
            || (ProgressionRules.resourceGates(itemName).contains(SkillGate.CRAFTING)
                && containsAny(target, "chisel", "needle", "glassblowing pipe", "spinning wheel", "loom", "potter")))
        {
            return SkillGate.CRAFTING;
        }

        // Farming: seeds/compost/tools on patches.
        if ((ProgressionRules.resourceGates(itemName).contains(SkillGate.FARMING)
                || containsAny(itemName, "seed dibber", "rake", "spade", "watering can", "compost"))
            && containsAny(target, "patch", "allotment", "flower", "herb", "tree patch", "hop", "bush", "cactus", "nightshade"))
        {
            return SkillGate.FARMING;
        }

        // Runecraft: essence/talismans/tiaras at ruins or altars.
        if (ProgressionRules.resourceGates(itemName).contains(SkillGate.RUNECRAFT)
            && containsAny(target, "altar", "mysterious ruins", "ruins", "rift"))
        {
            return SkillGate.RUNECRAFT;
        }

        // Construction: planks/hammer/saw on POH hotspots.
        if ((ProgressionRules.resourceGates(itemName).contains(SkillGate.CONSTRUCTION)
                || itemName.equals("hammer") || itemName.equals("saw"))
            && containsAny(target, "space", "hotspot", "furniture", "room", "door hotspot", "garden"))
        {
            return SkillGate.CONSTRUCTION;
        }

        // Hunter: traps/tools used on hunting targets.
        if ((containsAny(itemName, "bird snare", "box trap", "noose wand", "butterfly net", "rope", "small fishing net"))
            && containsAny(target, "trap", "snare", "deadfall", "burrow", "butterfly", "moth", "kebbit", "salamander", "chinchompa"))
        {
            return SkillGate.HUNTER;
        }

        return null;
    }

    private boolean isLockedSlayerCombat(MenuEntry entry)
    {
        if (entry == null || itemAccessService.isSkillUnlocked(SkillGate.SLAYER))
        {
            return false;
        }

        String option = normalizeOption(entry.getOption());
        boolean combatAction = option.startsWith("attack")
            || (option.startsWith("cast") && entry.getNpc() != null);
        if (!combatAction) return false;

        try
        {
            return client.getVarpValue(VarPlayerID.SLAYER_COUNT) > 0;
        }
        catch (RuntimeException ignored)
        {
            return false;
        }
    }

    private int selectedLockedItemId()
    {
        try
        {
            if (!client.isWidgetSelected()) return -1;
            Widget selected = client.getSelectedWidget();
            if (selected == null) return -1;
            int itemId = selected.getItemId();
            return itemId > 0 && itemAccessService.isLocked(itemId) ? itemId : -1;
        }
        catch (RuntimeException ignored)
        {
            return -1;
        }
    }

    /**
     * Map normal OSRS interaction text to the skill it trains. Restrictions are
     * enforced when the player clicks rather than by rewriting native menus.
     */
    static SkillGate skillGateForAction(String rawOption, String rawTarget)
    {
        String option = normalizeOption(rawOption);
        String target = normalizeTarget(rawTarget);

        if (option.startsWith("chop")) return SkillGate.WOODCUTTING;
        if (option.equals("mine") || option.startsWith("mine ")) return SkillGate.MINING;

        if (isFishingAction(option, target)) return SkillGate.FISHING;
        if (option.startsWith("cast") && isPrayerTrainingSpell(target)) return SkillGate.PRAYER;
        if (option.equals("bury") || option.startsWith("bury ") || option.equals("scatter")
            || option.startsWith("reanimate")
            || ((option.startsWith("offer") || option.startsWith("sacrifice"))
                && containsAny(target, "altar", "shrine", "libation", "ectofuntus"))) return SkillGate.PRAYER;

        if (option.equals("light") || option.startsWith("light ")
            || ((option.startsWith("feed") || option.startsWith("add")) && target.contains("brazier")))
        {
            return SkillGate.FIREMAKING;
        }
        if (option.startsWith("fletch")) return SkillGate.FLETCHING;
        if (option.equals("clean") || option.startsWith("clean ") || option.startsWith("mix")) return SkillGate.HERBLORE;
        if (option.startsWith("smith") || option.startsWith("smelt") || option.startsWith("forge")) return SkillGate.SMITHING;
        if (option.startsWith("cook") || option.startsWith("roast") || option.startsWith("bake")) return SkillGate.COOKING;
        if (option.startsWith("craft-rune") || option.startsWith("craft rune") || option.equals("bind")) return SkillGate.RUNECRAFT;
        if (option.startsWith("craft") || option.startsWith("spin") || option.startsWith("tan")) return SkillGate.CRAFTING;

        if (option.startsWith("pickpocket") || option.startsWith("steal-from") || option.startsWith("steal from")
            || option.startsWith("pick-lock") || option.startsWith("pick lock")
            || ((option.startsWith("loot") || option.startsWith("search") || option.startsWith("open"))
                && containsAny(target, "stall", "chest", "coffer", "urn", "sarcophagus", "grand gold chest")))
        {
            return SkillGate.THIEVING;
        }

        if (isAgilityAction(option, target)) return SkillGate.AGILITY;

        if (option.startsWith("rake") || option.startsWith("plant") || option.startsWith("harvest")
            || option.startsWith("check-health") || option.startsWith("check health")
            || ((option.startsWith("pick") || option.startsWith("clear"))
                && containsAny(target, "patch", "allotment", "herb", "flower", "hops", "bush", "cactus", "crop", "weeds")))
        {
            return SkillGate.FARMING;
        }

        if (isHunterAction(option, target)) return SkillGate.HUNTER;

        if ((option.equals("build") || option.startsWith("build ")
                || option.equals("remove") || option.startsWith("remove "))
            && containsAny(target, "space", "hotspot", "furniture", "room", "door hotspot", "garden")
            && !containsAny(target, "bird house", "birdhouse"))
        {
            return SkillGate.CONSTRUCTION;
        }
        if (option.startsWith("fix") && target.contains("brazier")) return SkillGate.CONSTRUCTION;

        if (option.startsWith("get-task") || option.startsWith("get task") || option.startsWith("assignment")
            || option.startsWith("slayer task")) return SkillGate.SLAYER;

        if (isSailingAction(option, target)) return SkillGate.SAILING;

        return null;
    }

    static boolean isPrayerTrainingSpell(String rawTarget)
    {
        String target = normalizeTarget(rawTarget);
        return target.contains("reanimation")
            || target.equals("sinister offering")
            || target.equals("demonic offering");
    }

    private static boolean isPrayerActivation(MenuEntry entry)
    {
        if (entry == null) return false;
        String option = normalizeOption(entry.getOption());
        if (!option.equals("activate") && !option.startsWith("activate ")) return false;

        int interfaceId = interfaceIdOf(entry.getParam1());
        if (interfaceId == InterfaceID.PRAYERBOOK) return true;

        String target = normalizeTarget(entry.getTarget());
        return target.contains("quick-prayer") || target.contains("quick prayer");
    }

    private static boolean isWidgetTargetAction(MenuAction action)
    {
        return action == MenuAction.WIDGET_TARGET_ON_GAME_OBJECT
            || action == MenuAction.WIDGET_TARGET_ON_NPC
            || action == MenuAction.WIDGET_TARGET_ON_PLAYER
            || action == MenuAction.WIDGET_TARGET_ON_GROUND_ITEM
            || action == MenuAction.WIDGET_TARGET_ON_WIDGET
            || action == MenuAction.WIDGET_USE_ON_ITEM
            || action == MenuAction.ITEM_USE_ON_GAME_OBJECT
            || action == MenuAction.ITEM_USE_ON_NPC
            || action == MenuAction.ITEM_USE_ON_PLAYER
            || action == MenuAction.ITEM_USE_ON_GROUND_ITEM
            || action == MenuAction.ITEM_USE_ON_ITEM;
    }

    private static boolean isFishingAction(String option, String target)
    {
        boolean action = option.equals("net") || option.equals("small net") || option.equals("big net")
            || option.equals("bait") || option.equals("lure") || option.equals("cage")
            || option.equals("harpoon") || option.equals("use-rod") || option.equals("fish")
            || option.startsWith("net ") || option.startsWith("bait ") || option.startsWith("lure ")
            || option.startsWith("harpoon ");
        return action && containsAny(target, "fishing spot", "shoal", "pool", "fish", "eels");
    }

    private static boolean isAgilityAction(String option, String target)
    {
        boolean action = option.startsWith("climb-over") || option.equals("climb")
            || option.startsWith("climb-up") || option.startsWith("climb-down") || option.startsWith("climb-under")
            || option.startsWith("squeeze-through") || option.startsWith("squeeze-past")
            || option.equals("cross") || option.startsWith("cross ") || option.startsWith("jump")
            || option.startsWith("walk-across") || option.startsWith("walk-up") || option.startsWith("walk-down")
            || option.startsWith("traverse") || option.startsWith("pass") || option.startsWith("leap")
            || option.startsWith("balance") || option.startsWith("swing") || option.startsWith("vault")
            || option.startsWith("crawl") || option.startsWith("scale") || option.startsWith("grapple");
        if (!action) return false;
        return containsAny(target, "obstacle", "course", "rooftop", "gap", "ledge", "tightrope",
            "agility pipe", "cargo net", "monkey bars", "hurdle", "zip line", "hand holds",
            "stepping stone", "balance log", "balance beam", "rough wall", "monkeybars", "roof",
            "rope swing", "plank", "log balance", "wall shortcut", "shortcut");
    }

    private static boolean isHunterAction(String option, String target)
    {
        if (option.startsWith("lay") || option.startsWith("set-trap") || option.startsWith("set trap")) return true;
        if ((option.startsWith("check") || option.startsWith("reset"))
            && containsAny(target, "trap", "snare", "box", "deadfall", "net trap")) return true;
        if ((option.startsWith("empty") || option.startsWith("dismantle"))
            && containsAny(target, "bird house", "birdhouse")) return true;
        if (option.startsWith("inspect") && containsAny(target, "burrow", "track", "tunnel")) return true;
        return option.startsWith("catch") && containsAny(target, "butterfly", "impling", "moth", "kebbit", "salamander");
    }

    private static boolean isSailingAction(String option, String target)
    {
        if (option.startsWith("chart") || option.startsWith("salvage") || option.startsWith("trawl")) return true;
        if ((option.startsWith("board") || option.startsWith("sail"))
            && containsAny(target, "raft", "boat", "ship", "gangplank", "vessel")) return true;
        return option.startsWith("accept") && containsAny(target, "notice board", "port task", "courier");
    }

    private static String skillLockedReason(SkillGate gate)
    {
        switch (gate)
        {
            case WOODCUTTING: return "Woodcutting should be open from your Tutorial Island bronze axe.";
            case MINING: return "Mining is locked until you choose a Mining progression unlock.";
            case FISHING: return "Fishing is locked until you choose a Fishing progression unlock.";
            case PRAYER: return "Prayer is locked until you choose Bones.";
            default: return gate.getDisplayName() + " is locked until you choose its skill unlock.";
        }
    }

    private static int interfaceIdOf(int componentId)
    {
        try
        {
            return WidgetUtil.componentToInterface(componentId);
        }
        catch (RuntimeException ignored)
        {
            return -1;
        }
    }

    private static boolean isTradeInterface(int interfaceId)
    {
        return interfaceId == InterfaceID.TRADEMAIN
            || interfaceId == InterfaceID.TRADESIDE
            || interfaceId == InterfaceID.TRADECONFIRM;
    }

    private static boolean isTradeExitOption(String option)
    {
        return option == null || option.isEmpty() || option.startsWith("decline")
            || option.startsWith("remove") || option.startsWith("examine");
    }

    private static boolean isInventorySide(int interfaceId)
    {
        return interfaceId == InterfaceID.INVENTORY
            || interfaceId == InterfaceID.WORNITEMS
            || interfaceId == InterfaceID.BANKSIDE
            || interfaceId == InterfaceID.SHOPSIDE
            || interfaceId == InterfaceID.GE_OFFERS_SIDE
            || interfaceId == InterfaceID.GE_PRICECHECKER_SIDE
            || interfaceId == InterfaceID.EQUIPMENT_SIDE;
    }

    private static boolean isAllowedManagementOption(String option)
    {
        return option.isEmpty() || option.equals("cancel") || option.startsWith("drop")
            || option.startsWith("destroy") || option.startsWith("examine") || option.startsWith("deposit")
            || option.startsWith("remove") || option.startsWith("store") || option.startsWith("sell")
            || option.startsWith("offer");
    }

    private boolean currentGeBuyItemIsLocked()
    {
        try
        {
            int itemId = client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH);
            if (itemId <= 0 || client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE) != 0) return false;
            return !itemAccessService.isGeAllowed(itemId);
        }
        catch (RuntimeException ignored)
        {
            return false;
        }
    }

    private static boolean isGroundItemAction(MenuAction action)
    {
        return action == MenuAction.GROUND_ITEM_FIRST_OPTION
            || action == MenuAction.GROUND_ITEM_SECOND_OPTION
            || action == MenuAction.GROUND_ITEM_THIRD_OPTION
            || action == MenuAction.GROUND_ITEM_FOURTH_OPTION
            || action == MenuAction.GROUND_ITEM_FIFTH_OPTION
            || action == MenuAction.WIDGET_TARGET_ON_GROUND_ITEM;
    }

    private static boolean containsAny(String haystack, String... needles)
    {
        for (String needle : needles) if (haystack.contains(needle)) return true;
        return false;
    }

    private static int resolveItemId(int itemId, Widget widget)
    {
        return itemId > 0 ? itemId : widget == null ? itemId : widget.getItemId();
    }

    private static String normalizeOption(String option)
    {
        return option == null ? "" : option.replaceAll("<[^>]*>", "").trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeTarget(String target)
    {
        return target == null ? "" : target.replaceAll("<[^>]*>", "").trim().toLowerCase(Locale.ROOT);
    }
}
