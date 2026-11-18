import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.*;
import java.util.List;

// Map tokens for characters and NPCs.
//
// A token's look is a small JSON object kept in the owner's "token" field:
//   shape : one of SHAPES (Circle by default)
//   color : "#RRGGBB" fill, used when there's no image
//   image : optional Base64 PNG, a THUMB × THUMB square thumbnail (uploaded or pasted), drawn clipped to the shape
// Characters store it in their record (characters.json, kept on wizard edits by CharacterStorage.carryOverProgression);
// NPCs store it in their asset (custom_assets.json, the NPC schema's TOKEN field). Both travel with Backup export/import.
//
// editor(...) is the shared picker used by the character wizard, the character sheet and the NPC editor.
// Uploads: desktop reads a path typed into an in-window field (no file dialogs), or a file dropped on the editor;
// the browser uses WebBridge.pickImageFile / readClipboardImage, which downscale in JavaScript first.
class Tokens {

static final String[] SHAPES = {"Circle", "Star", "Triangle", "Square", "Diamond", "Hexagon"};
static final String[] COLOR_NAMES = {"Crimson", "Ember", "Gold", "Forest", "Teal", "Sky",
        "Royal", "Violet", "Rose", "Stone", "Bone", "Midnight"};
static final String[] COLOR_HEX = {"#B03A2E", "#D2691E", "#C9A227", "#2E7D32", "#00897B", "#1E88E5",
        "#3949AB", "#7B1FA2", "#C2185B", "#6D6F78", "#D8D0BE", "#263238"};
static final String PC_COLOR = "#1E88E5", NPC_COLOR = "#B03A2E";
static final int THUMB = 128;

// ===============================================================
// Model
// ===============================================================
static Map<String, Object> defaults(String color) {
    Map<String, Object> t = new LinkedHashMap<>();
    t.put("shape", "Circle");
    t.put("color", color == null ? PC_COLOR : color);
    t.put("image", "");
    return t;
}

// A clean copy of a stored token (anything missing or invalid falls back to the defaults)
static Map<String, Object> read(Object json, String defaultColor) {
    Map<String, Object> t = defaults(defaultColor);
    if (json instanceof Map) {
        Map<?, ?> m = (Map<?, ?>) json;
        String shape = CustomAssets.str(m, "shape");
        if (Arrays.asList(SHAPES).contains(shape)) t.put("shape", shape);
        String color = CustomAssets.str(m, "color");
        if (parseColor(color) != null) t.put("color", color.toUpperCase());
        t.put("image", CustomAssets.str(m, "image"));
    }
    return t;
}

static Color parseColor(String hex) {
    if (hex == null || !hex.trim().matches("#?[0-9a-fA-F]{6}")) return null;
    String h = hex.trim().startsWith("#") ? hex.trim().substring(1) : hex.trim();
    return new Color(Integer.parseInt(h, 16));
}

static Color fill(Map<?, ?> tok) {
    Color c = parseColor(CustomAssets.str(tok, "color"));
    return c != null ? c : parseColor(PC_COLOR);
}

static String hex(Color c) {
    return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
}

static String colorName(String hex) {
    for (int i = 0; i < COLOR_HEX.length; i++) if (COLOR_HEX[i].equalsIgnoreCase(hex)) return COLOR_NAMES[i];
    return hex == null ? "" : hex.toUpperCase();
}

static String initials(String name) {
    if (name == null) return "?";
    StringBuilder sb = new StringBuilder();
    for (String w : name.trim().split("\\s+")) {
        if (w.isEmpty()) continue;
        char ch = w.charAt(0);
        if (Character.isLetterOrDigit(ch)) sb.append(Character.toUpperCase(ch));
        if (sb.length() == 2) break;
    }
    return sb.length() == 0 ? "?" : sb.toString();
}

// ===============================================================
// Images: decode (cached) and make thumbnails
// ===============================================================
private static final Map<String, BufferedImage> CACHE = new LinkedHashMap<String, BufferedImage>(16, 0.75f, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> e) {
        return size() > 64;
    }
};

static boolean hasImage(Map<?, ?> tok) {
    return image(tok) != null;
}

static BufferedImage image(Map<?, ?> tok) {
    String b64 = CustomAssets.str(tok, "image");
    if (b64.isEmpty()) return null;
    synchronized (CACHE) {
        if (CACHE.containsKey(b64)) return CACHE.get(b64);
    }
    BufferedImage img;
    try {
        img = ImageIO.read(new ByteArrayInputStream(Base64.getMimeDecoder().decode(b64)));
    } catch (Exception ex) {
        img = null;
    }
    synchronized (CACHE) {
        CACHE.put(b64, img);
    }
    return img;
}

// Center-crops to a square and scales to THUMB × THUMB, returned as Base64 PNG (null if it can't be encoded)
static String thumbnail(BufferedImage src) {
    if (src == null || src.getWidth() <= 0 || src.getHeight() <= 0) return null;
    int s = Math.min(src.getWidth(), src.getHeight());
    BufferedImage cur = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g0 = cur.createGraphics();
    g0.drawImage(src, -(src.getWidth() - s) / 2, -(src.getHeight() - s) / 2, null);
    g0.dispose();
    // Halve step by step so large photos downscale smoothly
    while (cur.getWidth() >= THUMB * 2) {
        int n = cur.getWidth() / 2;
        BufferedImage half = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = half.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(cur, 0, 0, n, n, null);
        g.dispose();
        cur = half;
    }
    BufferedImage out = new BufferedImage(THUMB, THUMB, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = out.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g.drawImage(cur, 0, 0, THUMB, THUMB, null);
    g.dispose();
    try {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(out, "png", bytes)) return null;
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    } catch (Exception ex) {
        return null;
    }
}

static BufferedImage toBuffered(Image img) {
    if (img instanceof BufferedImage) return (BufferedImage) img;
    if (img == null) return null;
    img = new ImageIcon(img).getImage(); // waits for the pixels to load
    int w = img.getWidth(null), h = img.getHeight(null);
    if (w <= 0 || h <= 0) return null;
    BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = b.createGraphics();
    g.drawImage(img, 0, 0, null);
    g.dispose();
    return b;
}

// ===============================================================
// Painting
// ===============================================================
// The token's outline shape centered on (cx, cy) with "radius" r
static Shape shape(String name, double cx, double cy, double r) {
    switch (name == null ? "" : name) {
        case "Star": return star(cx, cy + r * 0.06, r * 1.04, r * 0.5);
        case "Triangle": return polygon(cx, cy + r * 0.29, r * 1.15, 3);
        case "Square": return new RoundRectangle2D.Double(cx - r * 0.86, cy - r * 0.86, r * 1.72, r * 1.72, r * 0.32, r * 0.32);
        case "Diamond": return polygon(cx, cy, r * 1.04, 4);
        case "Hexagon": return polygon(cx, cy, r, 6);
        default: return new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2);
    }
}

private static Shape polygon(double cx, double cy, double r, int sides) {
    Path2D p = new Path2D.Double();
    for (int i = 0; i < sides; i++) {
        double a = Math.toRadians(-90 + 360.0 * i / sides);
        double x = cx + Math.cos(a) * r, y = cy + Math.sin(a) * r;
        if (i == 0) p.moveTo(x, y);
        else p.lineTo(x, y);
    }
    p.closePath();
    return p;
}

private static Shape star(double cx, double cy, double outer, double inner) {
    Path2D p = new Path2D.Double();
    for (int i = 0; i < 10; i++) {
        double a = Math.toRadians(-90 + 36.0 * i);
        double r = i % 2 == 0 ? outer : inner;
        double x = cx + Math.cos(a) * r, y = cy + Math.sin(a) * r;
        if (i == 0) p.moveTo(x, y);
        else p.lineTo(x, y);
    }
    p.closePath();
    return p;
}

// Draws the token: its image clipped to the shape (or the shape filled with its color and the owner's
// initials), then an outline of `outlineWidth` in `outline` (null = a thin dark rim only).
static void paint(Graphics2D g0, Map<?, ?> tok, String label, double cx, double cy, double r, Color outline, float outlineWidth) {
    Graphics2D g = (Graphics2D) g0.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    String shapeName = CustomAssets.str(tok, "shape");
    Shape s = shape(shapeName, cx, cy, r);
    // drop shadow
    g.setColor(new Color(0, 0, 0, 90));
    g.fill(AffineTransform.getTranslateInstance(r * 0.05 + 1, r * 0.08 + 1).createTransformedShape(s));

    BufferedImage img = image(tok);
    Color fill = fill(tok);
    if (img != null) {
        g.setColor(App.SHEET_BG);
        g.fill(s);
        Shape oldClip = g.getClip();
        g.clip(s);
        g.drawImage(img, (int) Math.round(cx - r), (int) Math.round(cy - r), (int) Math.round(r * 2), (int) Math.round(r * 2), null);
        g.setClip(oldClip);
    } else {
        g.setPaint(new GradientPaint((float) cx, (float) (cy - r), fill.brighter(), (float) cx, (float) (cy + r), fill.darker()));
        g.fill(s);
        if (r >= 7 && label != null) {
            String ini = initials(label);
            int fs = (int) Math.max(7, r * (ini.length() > 1 ? 0.72 : 0.9));
            g.setFont(new Font("SansSerif", Font.BOLD, fs));
            FontMetrics fm = g.getFontMetrics();
            double lum = 0.299 * fill.getRed() + 0.587 * fill.getGreen() + 0.114 * fill.getBlue();
            float tx = (float) (cx - fm.stringWidth(ini) / 2.0);
            float ty = (float) (cy + (fm.getAscent() - fm.getDescent()) / 2.0 + ("Triangle".equals(shapeName) ? r * 0.18 : 0));
            g.setColor(lum > 150 ? new Color(0, 0, 0, 70) : new Color(0, 0, 0, 110));
            g.drawString(ini, tx + 1, ty + 1);
            g.setColor(lum > 150 ? new Color(0x1E, 0x20, 0x2C) : Color.WHITE);
            g.drawString(ini, tx, ty);
        }
    }
    g.setStroke(new BasicStroke(Math.max(1f, outlineWidth * 0.35f) + 1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.setColor(new Color(0, 0, 0, 150));
    g.draw(s);
    if (outline != null && outlineWidth > 0) {
        g.setStroke(new BasicStroke(outlineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(outline);
        g.draw(s);
    }
    g.dispose();
}

// A token drawn at a fixed size, e.g. on the sheet, in lists and in the editor
static class Preview extends JComponent {
    Map<String, Object> tok;
    String label;
    Color outline;
    float outlineWidth;
    boolean dimmed;
    java.util.function.Supplier<String> labelSource; // optional live label (e.g. a name field being typed in)

    Preview(Map<String, Object> tok, String label, int size, Color outline) {
        this.tok = tok;
        this.label = label;
        this.outline = outline;
        this.outlineWidth = Math.max(2f, size / 22f);
        Dimension d = new Dimension(size, size);
        setPreferredSize(d);
        setMinimumSize(d);
        setMaximumSize(d);
    }

    void set(Map<String, Object> tok, String label) {
        this.tok = tok;
        this.label = label;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        if (isOpaque()) {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
        }
        double size = Math.min(getWidth(), getHeight());
        double r = size / 2.0 - outlineWidth - 2;
        Tokens.paint(g, tok, labelSource != null ? labelSource.get() : label, getWidth() / 2.0, getHeight() / 2.0, r, outline, outlineWidth);
        if (dimmed) {
            g.setColor(new Color(20, 22, 30, 150));
            g.fill(shape(CustomAssets.str(tok, "shape"), getWidth() / 2.0, getHeight() / 2.0, r));
        }
        g.dispose();
    }
}

// Small icon of a bare shape (for the shape picker)
private static class ShapeIcon implements Icon {
    final String shape;
    final Color color;
    ShapeIcon(String shape, Color color) {
        this.shape = shape;
        this.color = color;
    }
    public int getIconWidth() { return 22; }
    public int getIconHeight() { return 22; }
    public void paintIcon(Component c, Graphics g0, int x, int y) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(color);
        g.fill(shape(shape, x + 11, y + 11, 9));
        g.dispose();
    }
}

static final String INFO = "Your token is how this creature appears on the Combat map. Pick a shape and fill color, "
        + "or use a picture: upload a PNG or JPG, or copy an image and paste it (Ctrl+V or the Paste button). "
        + "Pictures are cropped to a square, shrunk to " + THUMB + " × " + THUMB + " pixels and saved with the "
        + "character or NPC, so they're included in backups. On the map, the token's outline shows its team color "
        + "and its footprint follows its size (Large 2 × 2, Huge 3 × 3, Gargantuan 4 × 4 squares).";

// ===============================================================
// Editor (wizard, character sheet, NPC editor)
// ===============================================================
static final class Editor {
    final JPanel panel;
    private final Map<String, Object> value;

    private Editor(JPanel panel, Map<String, Object> value) {
        this.panel = panel;
        this.value = value;
    }

    Map<String, Object> value() {
        return new LinkedHashMap<>(value);
    }
}

// `label` supplies the owner's current name for the initials (it can change while the editor is open)
static Editor editor(Map<String, Object> initial, java.util.function.Supplier<String> label, String defaultColor, Runnable onChange) {
    Map<String, Object> value = read(initial, defaultColor);
    JPanel panel = new JPanel(new BorderLayout(16, 0)) {
        @Override
        public Dimension getMaximumSize() {
            return new Dimension(760, getPreferredSize().height);
        }
    };
    panel.setOpaque(false);

    Preview preview = new Preview(value, label.get(), 104, App.GOLD);
    preview.labelSource = label;
    preview.setToolTipText("About tokens");
    App.clickable(preview, () -> App.showInfoOverlay(preview, "Token", "Combat map", INFO, null));
    JPanel previewWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
    previewWrap.setOpaque(false);
    previewWrap.add(preview);
    panel.add(previewWrap, BorderLayout.WEST);

    JPanel col = new JPanel();
    col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
    col.setOpaque(false);
    panel.add(col, BorderLayout.CENTER);

    JLabel status = new JLabel(" ");
    status.setFont(new Font("SansSerif", Font.PLAIN, 12));
    status.setForeground(App.TEXT_MUTED);

    Map<String, JButton> shapeButtons = new LinkedHashMap<>();
    List<JButton> swatches = new ArrayList<>();
    JTextField hexField = new JTextField(7);
    App.styleTextField(hexField);
    hexField.setFont(new Font("Monospaced", Font.PLAIN, 13));
    hexField.setToolTipText("Custom fill color as #RRGGBB (press Enter)");
    Dimension hd = new Dimension(86, 28);
    hexField.setPreferredSize(hd);
    hexField.setMaximumSize(hd);
    JButton clear = smallButton("Clear Image", "Remove the picture and go back to the shape and color");

    Runnable[] refresh = new Runnable[1];
    refresh[0] = () -> {
        preview.set(value, label.get());
        String cur = CustomAssets.str(value, "shape");
        for (Map.Entry<String, JButton> en : shapeButtons.entrySet()) {
            boolean on = en.getKey().equals(cur);
            en.getValue().setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(on ? App.GOLD : App.SHEET_BORDER, on ? 2 : 1, true), new EmptyBorder(3, 5, 3, 5)));
            en.getValue().setIcon(new ShapeIcon(en.getKey(), on ? App.GOLD : App.TEXT_MUTED));
        }
        String color = CustomAssets.str(value, "color");
        for (int i = 0; i < swatches.size(); i++) {
            boolean on = COLOR_HEX[i].equalsIgnoreCase(color);
            swatches.get(i).setBorder(new LineBorder(on ? App.GOLD : App.SHEET_BORDER, on ? 3 : 1, true));
        }
        if (!hexField.isFocusOwner()) hexField.setText(color);
        clear.setEnabled(!CustomAssets.str(value, "image").isEmpty());
        if (onChange != null) onChange.run();
    };

    // ---- shape ----
    col.add(caption("SHAPE"));
    JPanel shapes = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    shapes.setOpaque(false);
    for (String sh : SHAPES) {
        JButton b = new JButton(sh);
        b.setFont(new Font("SansSerif", Font.BOLD, 12));
        b.setForeground(App.TEXT_LIGHT);
        b.setBackground(App.SHEET_ROW_ALT);
        b.setOpaque(true);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setToolTipText(sh + " token");
        b.addActionListener(e -> {
            value.put("shape", sh);
            refresh[0].run();
        });
        hover(b);
        shapeButtons.put(sh, b);
        shapes.add(b);
    }
    shapes.setAlignmentX(Component.LEFT_ALIGNMENT);
    col.add(shapes);
    col.add(Box.createRigidArea(new Dimension(0, 10)));

    // ---- color ----
    col.add(caption("FILL COLOR"));
    JPanel colors = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
    colors.setOpaque(false);
    for (int i = 0; i < COLOR_HEX.length; i++) {
        String hx = COLOR_HEX[i];
        JButton sw = new JButton();
        sw.setBackground(parseColor(hx));
        sw.setOpaque(true);
        sw.setContentAreaFilled(false);
        sw.setFocusPainted(false);
        sw.setPreferredSize(new Dimension(24, 24));
        sw.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        sw.setToolTipText(COLOR_NAMES[i]);
        sw.addActionListener(e -> {
            value.put("color", hx);
            refresh[0].run();
        });
        // paint the swatch ourselves so the look-and-feel doesn't hide the color
        sw.setUI(new javax.swing.plaf.basic.BasicButtonUI() {
            @Override
            public void paint(Graphics g, JComponent c) {
                g.setColor(c.getBackground());
                g.fillRect(0, 0, c.getWidth(), c.getHeight());
            }
        });
        swatches.add(sw);
        colors.add(sw);
    }
    hexField.addActionListener(e -> applyHex(hexField, value, status, refresh[0]));
    hexField.addFocusListener(new FocusAdapter() {
        @Override
        public void focusLost(FocusEvent e) {
            applyHex(hexField, value, status, refresh[0]);
        }
    });
    colors.add(Box.createRigidArea(new Dimension(6, 0)));
    colors.add(hexField);
    colors.setAlignmentX(Component.LEFT_ALIGNMENT);
    col.add(colors);
    col.add(Box.createRigidArea(new Dimension(0, 10)));

    // ---- image ----
    col.add(caption("IMAGE (OPTIONAL)"));
    JPanel imgRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    imgRow.setOpaque(false);
    JButton upload = smallButton("Upload Image…", "Use a PNG or JPG picture for this token");
    JButton paste = smallButton("Paste", "Paste an image you copied (Ctrl+V)");
    imgRow.add(upload);
    imgRow.add(paste);
    imgRow.add(clear);
    imgRow.setAlignmentX(Component.LEFT_ALIGNMENT);
    col.add(imgRow);

    // Desktop: in-window path field (no file dialogs)
    JPanel pathRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
    pathRow.setOpaque(false);
    JTextField pathField = new JTextField(26);
    App.styleTextField(pathField);
    pathField.setFont(new Font("SansSerif", Font.PLAIN, 13));
    pathField.setToolTipText("Full path of a PNG or JPG file. You can also drag a file onto this panel.");
    JButton load = smallButton("Load", "Load this image file");
    JButton cancelPath = smallButton("Cancel", "Hide the file path field");
    pathRow.add(pathField);
    pathRow.add(load);
    pathRow.add(cancelPath);
    pathRow.setVisible(false);
    pathRow.setAlignmentX(Component.LEFT_ALIGNMENT);
    col.add(pathRow);

    status.setAlignmentX(Component.LEFT_ALIGNMENT);
    status.setBorder(new EmptyBorder(4, 2, 0, 0));
    col.add(status);

    java.util.function.Consumer<String> applyImage = b64 -> {
        value.put("image", b64);
        status.setForeground(App.TEXT_MUTED);
        status.setText("Image set (" + THUMB + " × " + THUMB + ").");
        refresh[0].run();
    };
    java.util.function.Consumer<String> showError = msg -> {
        status.setForeground(App.ACCENT_HOVER);
        status.setText(msg);
    };
    java.util.function.Consumer<BufferedImage> useImage = img -> {
        String b64 = thumbnail(img);
        if (b64 == null) showError.accept("Couldn't use that image.");
        else applyImage.accept(b64);
    };
    java.util.function.Consumer<File> useFile = f -> {
        if (f == null || !f.isFile()) {
            showError.accept("File not found: " + (f == null ? "" : f.getPath()));
            return;
        }
        if (f.length() > 25L * 1024 * 1024) {
            showError.accept("That file is too large (over 25 MB).");
            return;
        }
        try {
            BufferedImage img = ImageIO.read(f);
            if (img == null) showError.accept("Couldn't read that image. Use a PNG or JPG file.");
            else {
                useImage.accept(img);
                pathRow.setVisible(false);
                col.revalidate();
            }
        } catch (Exception ex) {
            showError.accept("Couldn't read that image. Use a PNG or JPG file.");
        }
    };
    // Browser natives return Base64 PNG thumbnails, "" when cancelled / nothing found, or "\0message" on errors
    java.util.function.BiConsumer<String, String> webResult = (r, emptyMsg) -> {
        if (r == null) showError.accept("The browser refused. Try again, or use Upload instead.");
        else if (r.startsWith("\u0000")) showError.accept(r.substring(1));
        else if (r.isEmpty()) {
            if (emptyMsg != null) showError.accept(emptyMsg);
        } else if (image(Collections.singletonMap("image", r)) == null) showError.accept("Couldn't read that image.");
        else applyImage.accept(r);
    };

    upload.addActionListener(e -> {
        if (DataPaths.web) {
            status.setForeground(App.TEXT_MUTED);
            status.setText("Choose an image in the browser's file picker…");
            WebBridge.callAsync(WebBridge::pickImageFile, r -> webResult.accept(r, null));
            return;
        }
        if (pathField.getText().trim().isEmpty()) pathField.setText(DataPaths.userDirectory().toString() + File.separator);
        pathRow.setVisible(true);
        status.setForeground(App.TEXT_MUTED);
        status.setText("Type the path of a PNG or JPG file and press Load, or drag a file here.");
        col.revalidate();
        SwingUtilities.invokeLater(pathField::requestFocusInWindow);
    });
    load.addActionListener(e -> useFile.accept(new File(pathField.getText().trim().replace("\"", ""))));
    pathField.addActionListener(e -> load.doClick());
    cancelPath.addActionListener(e -> {
        pathRow.setVisible(false);
        status.setText(" ");
        col.revalidate();
    });

    Runnable doPaste = () -> {
        if (DataPaths.web) {
            status.setForeground(App.TEXT_MUTED);
            status.setText("Reading the clipboard…");
            WebBridge.callAsync(WebBridge::readClipboardImage, r -> webResult.accept(r, "The clipboard doesn't contain an image. Copy a picture first."));
            return;
        }
        try {
            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable t = cb.getContents(null);
            if (t != null && t.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                BufferedImage img = toBuffered((Image) t.getTransferData(DataFlavor.imageFlavor));
                if (img == null) showError.accept("Couldn't read the image on the clipboard.");
                else useImage.accept(img);
            } else if (t != null && t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                List<?> files = (List<?>) t.getTransferData(DataFlavor.javaFileListFlavor);
                if (files.isEmpty()) showError.accept("The clipboard doesn't contain an image.");
                else useFile.accept((File) files.get(0));
            } else {
                showError.accept("The clipboard doesn't contain an image. Copy a picture first.");
            }
        } catch (Exception ex) {
            showError.accept("Couldn't read the clipboard.");
        }
    };
    paste.addActionListener(e -> doPaste.run());
    clear.addActionListener(e -> {
        value.put("image", "");
        status.setForeground(App.TEXT_MUTED);
        status.setText("Image cleared. The token uses its shape and color.");
        refresh[0].run();
    });

    // Ctrl+V anywhere on the screen pastes an image (a focused text field still pastes its own text first)
    panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_V, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "pasteTokenImage");
    panel.getActionMap().put("pasteTokenImage", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
            doPaste.run();
        }

        @Override
        public boolean isEnabled() {
            // Only when this editor is visible and not hidden behind an overlay it doesn't belong to
            if (!panel.isShowing()) return false;
            JRootPane root = SwingUtilities.getRootPane(panel);
            if (root == null) return false;
            JLayeredPane layers = root.getLayeredPane();
            Component[] modal = layers.getComponentsInLayer(JLayeredPane.MODAL_LAYER);
            if (modal.length == 0) return true;
            Component top = modal[0];
            for (Component c : modal) if (layers.getPosition(c) == 0) top = c;
            return SwingUtilities.isDescendingFrom(panel, top);
        }
    });

    // Desktop: drop an image file (or image) onto the editor
    if (!DataPaths.web) {
        panel.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport s) {
                return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor) || s.isDataFlavorSupported(DataFlavor.imageFlavor);
            }

            @Override
            public boolean importData(TransferSupport s) {
                try {
                    if (s.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        List<?> files = (List<?>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                        if (!files.isEmpty()) useFile.accept((File) files.get(0));
                        return true;
                    }
                    BufferedImage img = toBuffered((Image) s.getTransferable().getTransferData(DataFlavor.imageFlavor));
                    if (img != null) useImage.accept(img);
                    return img != null;
                } catch (Exception ex) {
                    showError.accept("Couldn't use the dropped file.");
                    return false;
                }
            }
        });
    }

    refresh[0].run();
    return new Editor(panel, value);
}

private static void applyHex(JTextField field, Map<String, Object> value, JLabel status, Runnable refresh) {
    String t = field.getText().trim();
    if (t.isEmpty()) {
        field.setText(CustomAssets.str(value, "color"));
        return;
    }
    Color c = parseColor(t);
    if (c == null) {
        status.setForeground(App.ACCENT_HOVER);
        status.setText("Colors look like #1E88E5 (six hex digits).");
        return;
    }
    String hx = hex(c);
    if (!hx.equalsIgnoreCase(CustomAssets.str(value, "color"))) {
        value.put("color", hx);
        status.setText(" ");
        refresh.run();
    }
    field.setText(hx);
}

private static JLabel caption(String text) {
    JLabel l = new JLabel(text);
    l.setForeground(App.TEXT_MUTED);
    l.setFont(new Font("SansSerif", Font.BOLD, 11));
    l.setBorder(new EmptyBorder(0, 2, 4, 0));
    l.setAlignmentX(Component.LEFT_ALIGNMENT);
    return l;
}

static JButton smallButton(String text, String tip) {
    JButton b = new JButton(text);
    b.setToolTipText(tip);
    b.setFont(new Font("SansSerif", Font.BOLD, 12));
    b.setForeground(App.TEXT_LIGHT);
    b.setBackground(App.SHEET_ROW_ALT);
    b.setOpaque(true);
    b.setFocusPainted(false);
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    b.setBorder(BorderFactory.createCompoundBorder(new LineBorder(App.SHEET_BORDER, 1, true), new EmptyBorder(5, 10, 5, 10)));
    hover(b);
    return b;
}

private static void hover(JButton b) {
    b.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent e) {
            if (b.isEnabled()) b.setForeground(App.GOLD);
        }

        @Override
        public void mouseExited(MouseEvent e) {
            b.setForeground(App.TEXT_LIGHT);
        }
    });
}
}
