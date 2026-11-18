import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

// Light and vision (2024 rules), shared by the map editor's Light layer and the Combat board.
//
// A map's light comes from three things (all saved in its "grid", see MapEditorPanel.MapModel):
//   ambient : the whole map's default level (Bright, Dim or Dark)
//   light   : per-square overrides painted in the editor's Light layer (-1 = use the map default)
//   torches : mounted torches (Bright 20 ft. + Dim 20 ft.), unless unlit or destroyed
// In Combat, creatures holding a light (Torch, Lamp, Hooded Lantern, Candle) also light the squares around them.
// A source lights a square when the square is within its radius (grid distance: every square, diagonals too, is 5 ft.)
// and nothing blocks sight between them (walls and closed doors block light; windows and open doors don't).
//
// Vision: Dim Light is Lightly Obscured (Disadvantage on Wisdom (Perception) checks that rely on sight).
// Darkness is Heavily Obscured: a creature can't see into it. Darkvision sees Dim Light as Bright and Darkness as Dim
// within its range. Attacking a creature you can't see has Disadvantage; a creature that can't see you is attacked
// with Advantage (CombatPanel.checkTarget).
class Lighting {

static final int DEFAULT = -1, BRIGHT = 0, DIM = 1, DARK = 2;
static final String[] NAMES = {"Bright Light", "Dim Light", "Darkness"};
static final String[] SHORT = {"Bright", "Dim", "Dark"};
static final String[] INFO = {
        "Bright Light: most creatures see normally.",
        "Dim Light: the area is Lightly Obscured. Creatures have Disadvantage on Wisdom (Perception) checks that rely on sight. "
                + "Darkvision sees Dim Light as if it were Bright Light. Attacks are not affected.",
        "Darkness: the area is Heavily Obscured. A creature can't see into it (it is effectively Blinded when trying to see something there). "
                + "Attacking a creature you can't see has Disadvantage, and attacks against a creature that can't see its attacker have Advantage. "
                + "Darkvision sees Darkness as if it were Dim Light within its range."};

// Hand-held light sources: {bright radius, additional dim radius} in feet (2024 PHB)
static final Map<String, int[]> HELD_LIGHTS = new LinkedHashMap<>();
static {
    HELD_LIGHTS.put("Torch", new int[]{20, 20});
    HELD_LIGHTS.put("Lamp", new int[]{15, 30});
    HELD_LIGHTS.put("Hooded Lantern", new int[]{30, 30});
    HELD_LIGHTS.put("Candle", new int[]{5, 5});
}
static final int[] TORCH_LIGHT = HELD_LIGHTS.get("Torch");

static final String MOUNTED_TORCH_INFO = "A torch fixed in a wall sconce or bracket. While lit it sheds Bright Light in a 20-foot radius and Dim Light for an additional 20 feet. "
        + "Creatures can walk past it freely. It can be attacked like a creature (it has its own AC and Hit Points, set in the map editor) "
        + "and is immune to Poison and Psychic damage. At 0 Hit Points it is destroyed and its light goes out.";

// {bright, dim} for a held item ("Torch (3)" counts), or null if it gives no light
static int[] heldLight(String item) {
    if (item == null || item.isEmpty()) return null;
    String base = GameData.stripEquipmentCount(item).trim();
    for (Map.Entry<String, int[]> en : HELD_LIGHTS.entrySet()) {
        String k = en.getKey();
        if (k.equalsIgnoreCase(base) || (k + "s").equalsIgnoreCase(base) || (k + "es").equalsIgnoreCase(base)) return en.getValue();
    }
    return null;
}

static String heldLightText(int[] l) {
    return "Bright Light " + l[0] + " ft., Dim Light " + l[1] + " ft. more";
}

// A light source covering a footprint (top-left square x, y and size in squares)
static final class Source {
    final int x, y, size, bright, dim;
    Source(int x, int y, int size, int bright, int dim) {
        this.x = x;
        this.y = y;
        this.size = size;
        this.bright = bright;
        this.dim = dim;
    }
    String key() { return x + "," + y + "," + size + "," + bright + "," + dim; }
}

// Lit mounted torches of a map
static List<Source> torchSources(MapEditorPanel.MapModel m) {
    List<Source> out = new ArrayList<>();
    for (MapEditorPanel.Torch t : m.torches) if (t.lit && t.hp > 0) out.add(new Source(t.x, t.y, 1, TORCH_LIGHT[0], TORCH_LIGHT[1]));
    return out;
}

// ===============================================================
// Light levels
// ===============================================================
static int baseLevel(MapEditorPanel.MapModel m, int x, int y) {
    int v = m.light[y][x];
    return v >= BRIGHT && v <= DARK ? v : Math.max(BRIGHT, Math.min(DARK, m.ambient));
}

// Light level of every square: [y][x] -> BRIGHT / DIM / DARK
static int[][] compute(MapEditorPanel.MapModel m, List<Source> sources) {
    int[][] out = new int[m.height][m.width];
    for (int y = 0; y < m.height; y++) for (int x = 0; x < m.width; x++) out[y][x] = baseLevel(m, x, y);
    for (Source s : sources) {
        int reach = (s.bright + s.dim) / 5;
        double sx = s.x + s.size / 2.0, sy = s.y + s.size / 2.0;
        for (int y = Math.max(0, s.y - reach); y <= Math.min(m.height - 1, s.y + s.size - 1 + reach); y++) {
            for (int x = Math.max(0, s.x - reach); x <= Math.min(m.width - 1, s.x + s.size - 1 + reach); x++) {
                if (out[y][x] == BRIGHT) continue;
                int d = gapFt(s.x, s.y, s.size, x, y);
                int lv = d <= s.bright ? BRIGHT : d <= s.bright + s.dim ? DIM : DARK;
                if (lv >= out[y][x]) continue;
                boolean inside = x >= s.x && y >= s.y && x < s.x + s.size && y < s.y + s.size;
                if (!inside && sight(m, sx, sy, x + 0.5, y + 0.5)[0] == 1) continue;
                out[y][x] = lv;
            }
        }
    }
    return out;
}

// Grid distance in feet from a footprint to a square
static int gapFt(int fx, int fy, int size, int x, int y) {
    int dx = x < fx ? fx - x : x > fx + size - 1 ? x - (fx + size - 1) : 0;
    int dy = y < fy ? fy - y : y > fy + size - 1 ? y - (fy + size - 1) : 0;
    return Math.max(dx, dy) * 5;
}

// How a viewer with `darkvision` feet of Darkvision perceives a square `distFt` away
static int perceived(int level, int distFt, int darkvision) {
    return darkvision > 0 && distFt <= darkvision && level > BRIGHT ? level - 1 : level;
}

// ===============================================================
// Line of sight (walls and closed doors block; windows give half cover)
// ===============================================================
static int edgeValue(MapEditorPanel.MapModel m, int cx, int cy, int dx, int dy) {
    if (cx < 0 || cy < 0 || cx >= m.width || cy >= m.height) return MapEditorPanel.WALL;
    if (dx == 1) return m.vEdges[cy][cx + 1];
    if (dx == -1) return m.vEdges[cy][cx];
    if (dy == 1) return m.hEdges[cy + 1][cx];
    return m.hEdges[cy][cx];
}

static boolean blocksSight(int v) {
    return v == MapEditorPanel.WALL || v == MapEditorPanel.DOOR_CLOSED;
}

// {blocked (1/0), cover (1 = half cover from a window / low wall)} along the line between two points (in squares)
static int[] sight(MapEditorPanel.MapModel m, double x0, double y0, double x1, double y1) {
    double len = Math.hypot(x1 - x0, y1 - y0);
    int steps = Math.max(1, (int) Math.ceil(len / 0.05));
    int px = (int) Math.floor(x0), py = (int) Math.floor(y0);
    boolean cover = false;
    for (int i = 1; i <= steps; i++) {
        double t = i / (double) steps;
        int nx = (int) Math.floor(x0 + (x1 - x0) * t), ny = (int) Math.floor(y0 + (y1 - y0) * t);
        if (nx == px && ny == py) continue;
        int dx = Integer.signum(nx - px), dy = Integer.signum(ny - py);
        if (dx != 0 && dy != 0) {
            // passing a corner: blocked only if both ways around it are blocked
            int a1 = edgeValue(m, px, py, dx, 0), a2 = edgeValue(m, px + dx, py, 0, dy);
            int b1 = edgeValue(m, px, py, 0, dy), b2 = edgeValue(m, px, py + dy, dx, 0);
            boolean pathA = blocksSight(a1) || blocksSight(a2), pathB = blocksSight(b1) || blocksSight(b2);
            if (pathA && pathB) return new int[]{1, 0};
            if (a1 == MapEditorPanel.WINDOW || a2 == MapEditorPanel.WINDOW || b1 == MapEditorPanel.WINDOW || b2 == MapEditorPanel.WINDOW) cover = true;
        } else {
            int v = edgeValue(m, px, py, dx, dy);
            if (blocksSight(v)) return new int[]{1, 0};
            if (v == MapEditorPanel.WINDOW) cover = true;
        }
        px = nx;
        py = ny;
    }
    return new int[]{0, cover ? 1 : 0};
}

// ===============================================================
// Painting
// ===============================================================
private static final Color DIM_SHADE = new Color(4, 5, 14, 85), DARK_SHADE = new Color(2, 3, 9, 165);

// Shades Dim squares lightly and Dark squares heavily (visible part of a w × h viewport)
static void paintOverlay(Graphics2D g, int[][] lv, MapEditorPanel.MapModel m, double scale, double offX, double offY, int w, int h) {
    if (lv == null || lv.length != m.height) return;
    int x0 = Math.max(0, (int) Math.floor(-offX / scale)), y0 = Math.max(0, (int) Math.floor(-offY / scale));
    int x1 = Math.min(m.width - 1, (int) Math.floor((w - offX) / scale)), y1 = Math.min(m.height - 1, (int) Math.floor((h - offY) / scale));
    for (int y = y0; y <= y1; y++) {
        for (int x = x0; x <= x1; x++) {
            if (lv[y][x] == BRIGHT) continue;
            g.setColor(lv[y][x] == DIM ? DIM_SHADE : DARK_SHADE);
            g.fill(new Rectangle2D.Double(offX + x * scale, offY + y * scale, scale + 0.5, scale + 0.5));
        }
    }
}

// Editor Light layer: marks squares painted with their own level (not the map default) with a small corner tag
static void paintOverrides(Graphics2D g, MapEditorPanel.MapModel m, double scale, double offX, double offY) {
    if (scale < 14) return;
    g.setFont(new Font("SansSerif", Font.BOLD, (int) Math.max(8, Math.min(11, scale * 0.24))));
    FontMetrics fm = g.getFontMetrics();
    for (int y = 0; y < m.height; y++) {
        for (int x = 0; x < m.width; x++) {
            int v = m.light[y][x];
            if (v < BRIGHT) continue;
            String s = v == BRIGHT ? "B" : v == DIM ? "D" : "X";
            double px = offX + x * scale + 3, py = offY + y * scale + fm.getAscent() + 1;
            g.setColor(v == BRIGHT ? new Color(255, 225, 140, 200) : v == DIM ? new Color(170, 180, 220, 200) : new Color(140, 140, 170, 200));
            g.drawString(s, (float) px, (float) py);
        }
    }
}

// Bright / Dim / Dark key in the bottom-right corner of a viewport
static void paintLegend(Graphics2D g, int w, int h, int ambient) {
    String[] labels = {"Bright", "Dim", "Dark"};
    Color[] fills = {new Color(0x3B, 0x40, 0x54), blend(new Color(0x3B, 0x40, 0x54), DIM_SHADE), blend(new Color(0x3B, 0x40, 0x54), DARK_SHADE)};
    g.setFont(new Font("SansSerif", Font.BOLD, 11));
    FontMetrics fm = g.getFontMetrics();
    String head = "Map default: " + SHORT[Math.max(0, Math.min(2, ambient))];
    int boxW = Math.max(fm.stringWidth(head) + 16, 3 * 62 + 10), boxH = 44;
    int bx = w - boxW - 12, by = h - boxH - 10;
    g.setColor(new Color(0, 0, 0, 130));
    g.fill(new RoundRectangle2D.Double(bx, by, boxW, boxH, 8, 8));
    g.setColor(App.TEXT_LIGHT);
    g.drawString(head, bx + 8, by + 15);
    for (int i = 0; i < 3; i++) {
        int sx = bx + 8 + i * 62, sy = by + 22;
        g.setColor(fills[i]);
        g.fillRect(sx, sy, 14, 14);
        g.setColor(App.SHEET_BORDER);
        g.drawRect(sx, sy, 14, 14);
        g.setColor(App.TEXT_LIGHT);
        g.drawString(labels[i], sx + 18, sy + 12);
    }
}

private static Color blend(Color base, Color over) {
    double a = over.getAlpha() / 255.0;
    return new Color((int) (base.getRed() * (1 - a) + over.getRed() * a), (int) (base.getGreen() * (1 - a) + over.getGreen() * a),
            (int) (base.getBlue() * (1 - a) + over.getBlue() * a));
}

// A mounted torch: wall bracket + flame (grey when unlit, broken when destroyed)
static void paintTorch(Graphics2D g0, MapEditorPanel.Torch t, double scale, double offX, double offY, boolean hover) {
    Graphics2D g = (Graphics2D) g0.create();
    double cx = offX + (t.x + 0.5) * scale, cy = offY + (t.y + 0.5) * scale, r = Math.max(6, scale * 0.3);
    boolean destroyed = t.hp <= 0;
    if (t.lit && !destroyed) {
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), (float) (r * 2.2),
                new float[]{0f, 1f}, new Color[]{new Color(255, 190, 80, 110), new Color(255, 160, 40, 0)}));
        g.fill(new Ellipse2D.Double(cx - r * 2.2, cy - r * 2.2, r * 4.4, r * 4.4));
    }
    // bracket + handle
    g.setColor(new Color(20, 20, 26, 200));
    g.fill(new Ellipse2D.Double(cx - r * 0.95, cy - r * 0.95, r * 1.9, r * 1.9));
    g.setColor(destroyed ? new Color(110, 105, 100) : new Color(140, 95, 50));
    g.setStroke(new BasicStroke((float) Math.max(2, r * 0.28), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    if (destroyed) {
        g.draw(new Line2D.Double(cx - r * 0.2, cy + r * 0.7, cx - r * 0.05, cy + r * 0.05));
        g.draw(new Line2D.Double(cx + r * 0.15, cy - r * 0.1, cx + r * 0.45, cy - r * 0.45));
        g.setColor(new Color(220, 70, 70, 220));
        g.setStroke(new BasicStroke((float) Math.max(1.5, r * 0.16)));
        double k = r * 0.55;
        g.draw(new Line2D.Double(cx - k, cy - k, cx + k, cy + k));
        g.draw(new Line2D.Double(cx + k, cy - k, cx - k, cy + k));
    } else {
        g.draw(new Line2D.Double(cx, cy + r * 0.75, cx, cy - r * 0.05));
        Path2D flame = new Path2D.Double();
        flame.moveTo(cx, cy - r * 0.85);
        flame.curveTo(cx + r * 0.45, cy - r * 0.35, cx + r * 0.35, cy + r * 0.05, cx, cy + r * 0.05);
        flame.curveTo(cx - r * 0.35, cy + r * 0.05, cx - r * 0.45, cy - r * 0.35, cx, cy - r * 0.85);
        flame.closePath();
        g.setColor(t.lit ? new Color(255, 170, 50) : new Color(90, 90, 100));
        g.fill(flame);
        if (t.lit) {
            g.setColor(new Color(255, 235, 150));
            g.fill(new Ellipse2D.Double(cx - r * 0.13, cy - r * 0.4, r * 0.26, r * 0.36));
        }
    }
    g.setColor(hover ? App.GOLD : new Color(212, 175, 55, 150));
    g.setStroke(new BasicStroke(hover ? 2f : 1.2f));
    g.draw(new Ellipse2D.Double(cx - r * 0.95, cy - r * 0.95, r * 1.9, r * 1.9));
    g.dispose();
}

static String torchStatus(MapEditorPanel.Torch t) {
    return t.hp <= 0 ? "Destroyed" : t.lit ? "Lit" : "Unlit";
}
}
