import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

// In-app patch notes: the main menu's "Patch Notes" button opens them as an in-window overlay.
// Add new releases at the TOP of NOTES: {date, title, bullet, bullet, ...}.
class PatchNotes {

static final String[][] NOTES = {
    {"2026-11-20", "Beasts & Wild Shape",
        "Asset Library: 91 beasts from the core rules (Wolf, Brown Bear, Giant Eagle, Tyrannosaurus Rex...) are built-in NPCs with full stat blocks. Click one for its stats, or Copy to Homebrew to edit it.",
        "Combat: place beasts from the Place Token picker, which now has a search box. Their attacks, Multiattack, initiative and Darkvision come from the stat block.",
        "NPCs: new Initiative, Senses, Languages, Bonus Actions and Reactions fields.",
        "Wild Shape: pick your known forms on the Wild Shape card (four Beasts of CR 1/4 at level 2, six of CR 1/2 at level 4, eight of CR 1 with flying forms at level 8). In Combat, Wild Shape asks which form to take and swaps in its size, Speed, AC, attacks, Darkvision and Strength, Dexterity and Constitution. You keep your own Hit Points, mental scores and proficiencies, gain Temporary Hit Points equal to your Druid level, and can't cast spells. Leave the form with a Bonus Action.",
        "Asset Library: homebrew features can use the same parts: a Creature form choice (creature types, highest CR by level, when Fly Speeds are allowed) and an effect that turns the character into a chosen form."},
    {"2026-11-20", "Class features (levels 1 and 2)",
        "Character sheet: every 2024 class feature for levels 1 and 2 is a card under Features & Traits, with what it does in numbers for your character. Click a card for the full rules.",
        "Character sheet: features with uses (Rage, Second Wind, Channel Divinity, Focus Points, Lay on Hands, Sorcery Points...) show pips you can click to spend or regain. New Short Rest and Long Rest buttons recover uses and spell slots.",
        "Character sheet: pick Expertise, Fighting Styles, Metamagic, Eldritch Invocations and extra languages on their feature cards. Expertise (★) and Jack of All Trades change your skills, and Unarmored Defense, Unarmored Movement, Danger Sense, Martial Arts and Divine / Primal Order training are applied automatically.",
        "Combat: a Class Features section on the Current Turn tab: Rage (resistances, extra damage, extended by attacking), Reckless Attack, Bardic Inspiration, Divine Spark, Turn Undead, Wild Shape, Second Wind, Action Surge, Flurry of Blows, Patient Defense, Step of the Wind, Uncanny Metabolism, Lay on Hands, Innate Sorcery, Font of Magic, Metamagic and more.",
        "Combat: Sneak Attack is added to qualifying hits, Bardic Inspiration can turn a failed attack, save or check into a success, and Tactical Mind is offered on failed checks.",
        "Asset Library: homebrew class and subclass features are built from parts (activation, uses and recharge, targets and saves, healing, damage, a lasting effect, turn-economy grants, extra damage on a hit, always-on bonuses, choices) instead of text, so any built-in feature can be rebuilt. \"Copy a built-in feature…\" starts from one.",
        "Asset Library: Save Draft keeps an unfinished asset without checking it. Drafts are listed at the top of their category and aren't used anywhere until you finish them."},
    {"2026-11-20", "Death saves & more actions",
        "Combat: characters at 0 HP fall Unconscious and Prone and make death saving throws at the start of their turns. Damage at 0 HP adds failures (two on a Critical Hit), three successes make them Stable, and three failures kill them.",
        "Combat: massive damage kills outright when the damage left over at 0 HP equals the character's Hit Point maximum. Dying characters keep their turns, healing wakes them, and dead characters can be revived from their details.",
        "Combat: new actions Grapple and Shove (each replaces one attack), Help (assist an attack, assist a check, or first aid), Hide, Search, Study, Influence and Ready.",
        "Combat: casting a spell and using a magic item are now both under the Magic action. Use Item covers nonmagical items and what each hand holds."},
    {"2026-11-20", "Testing tools & cantrips",
        "Characters: new Level Down button (−) removes the last level from a class: its HP roll, any feat gained at that level (with its ability increases), and spells past the new level's limits.",
        "Characters: new Duplicate button (📋) copies a character as \"<name> (Copy)\".",
        "Characters: \"+ Add Test Characters\" adds ready-made characters for testing, including a level 20 Wizard who can cast every spell.",
        "Combat: cantrips can be cast by characters and NPCs.",
        "Asset Library: homebrew spells get Combat fields (targeting, save, dice, lasting effects), and NPCs get spell lists, slots and per-day uses.",
        "Main menu: these Patch Notes."},
    {"2026-11-19", "1st-level spells in combat",
        "Combat: level 1 spells with slots, saves, attack rolls, area shapes and lasting effects (conditions, AC / Speed changes, ongoing damage, temporary HP).",
        "Combat: Concentration, reaction spells (Shield, Hellish Rebuke) and smites.",
        "Spells without full combat rules can still be cast as tracked utility spells."},
    {"2026-11-18", "Movement, action economy & species traits",
        "Combat: tokens slide square by square, and leaving an enemy's reach prompts its opportunity attack.",
        "Combat: action, Bonus Action and Reaction tracking, plus Dash, Disengage, Dodge and the Rogue's Cunning Action.",
        "Combat: conditions affect attacks, movement and actions, and damage vulnerability / resistance / immunity is applied.",
        "Species traits (2024 PHB): trait cards on the sheet, Darkvision, damage resistances, Dwarven Toughness and Warforged AC.",
        "Light & vision: map lighting (bright / dim / dark), mounted torches and carried light sources, with Darkness affecting attacks."},
    {"2026-11-17", "Combat & weapons",
        "Combat: maps, token placement, initiative order, teams, and weapon attacks with range, line of sight and cover.",
        "Weapons (2024 PHB): Weapon Mastery, a hand system for weapons and shields, and ammunition tracking.",
        "Tokens: custom shapes, colours and pictures for characters and NPCs.",
        "Tags & content filters for homebrew and built-in content.",
        "Backup & Restore: export or import every character and all homebrew as one JSON file.",
        "Website: faster web version, documentation and search."},
    {"2026-11-16", "First release",
        "Character creation wizard using the 2024 rules: species, class, background, ability scores, equipment and spells.",
        "Level Up with hit dice, feats and multiclassing.",
        "Character sheet: customizable, with every value clickable for more detail.",
        "Asset Library: create homebrew classes, subclasses, species, spells, items, NPCs and more.",
        "Map editor for building combat maps."},
};

static void showOverlay(Component anchor) {
    JPanel body = new JPanel();
    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
    body.setBackground(App.SHEET_CARD);
    body.setBorder(new EmptyBorder(0, 0, 0, 8));
    for (int i = 0; i < NOTES.length; i++) {
        String[] n = NOTES[i];
        JLabel head = new JLabel("<html><b>" + App.escapeHtml(n[1]) + "</b>&nbsp;&nbsp;<font color='#9AA0B4'>" + n[0] + "</font></html>");
        head.setForeground(App.GOLD);
        head.setFont(new Font("SansSerif", Font.PLAIN, 15));
        head.setBorder(new EmptyBorder(i == 0 ? 0 : 16, 0, 6, 0));
        head.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(head);
        for (int b = 2; b < n.length; b++) {
            JLabel line = new JLabel("<html><div style='width:460px'>• " + App.escapeHtml(n[b]) + "</div></html>");
            line.setForeground(App.TEXT_LIGHT);
            line.setFont(new Font("SansSerif", Font.PLAIN, 13));
            line.setBorder(new EmptyBorder(2, 6, 2, 0));
            line.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(line);
        }
    }
    JScrollPane scroll = new JScrollPane(body);
    App.applyDarkScrollBar(scroll);
    scroll.setBorder(null);
    scroll.getViewport().setBackground(App.SHEET_CARD);
    scroll.setPreferredSize(new Dimension(540, 460));
    scroll.getVerticalScrollBar().setUnitIncrement(16);
    App.showSheetOverlay(anchor, "Patch Notes", scroll, null);
    SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(0));
}
}
