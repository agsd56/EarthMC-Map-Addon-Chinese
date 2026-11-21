package net.townymap.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Shown once, on the first launch where no EarthMC API key is set.
 *
 * <p>Built to inform, not to block: the addon is fully usable without a key and the panel says so
 * before it says anything else. Everything drawn on the map -- borders, town names, nation colours,
 * players, the imagery layer, hovering a town, searching by name -- reads the live map and needs
 * nothing. A key only adds what EarthMC keeps in its API alone.
 *
 * <p>The panel measures itself: width comes from the longest line it actually holds and height from
 * the rows it actually draws, both at the font in use. Nothing is positioned by a hand-tuned
 * constant, because the first version was, and its last line was clipped off the bottom edge.
 *
 * <p>A sibling of {@link BlockedScreen} rather than a generalisation of it: that screen carries a
 * message from the developer and an appeal route, this one carries instructions and a way into the
 * settings, and the only thing they genuinely share is how they look.
 */
public final class ApiKeyNoticeScreen extends Screen {

    private static final int PANEL_BG     = 0xF00E1013;
    private static final int PANEL_BORDER = 0xCC3A3D42;
    private static final int ACCENT       = 0xFF57A8FF;
    private static final int TITLE        = 0xFFFFFFFF;
    private static final int SUBTITLE     = 0xFF9AA2AD;
    private static final int LABEL        = 0xFF6F9FD8;
    private static final int BODY         = 0xFFC9CDD3;
    private static final int DIM          = 0xFF7D838C;
    private static final int COMMAND      = 0xFFFFD866;
    private static final int BULLET       = 0xFF4D5560;

    private static final int PAD = 14;
    private static final int LINE = 11;
    private static final int GAP = 7;
    private static final int BTN_H = 20;
    private static final int BTN_GAP = 8;

    /** Named so step 1 can quote the button by the words actually printed on it. */
    private static final String BTN_CHAT = "从聊天中获取密钥";
    private static final String BTN_SETTINGS = "打开设置";
    private static final String BTN_SKIP = "跳过";

    /** One drawn row. {@code tail} is appended straight after {@code text} in its own colour, which
     *  is how a command sits inside a sentence without splitting the row into two. */
    private record Row(String text, int color, String tail, int tailColor, int indent, int gapBefore) {
        static Row of(String t, int c)              { return new Row(t, c, "", 0, 0, 0); }
        static Row gap(String t, int c)             { return new Row(t, c, "", 0, 0, GAP); }
        static Row bullet(String t)                 { return new Row(t, BODY, "", 0, 10, 0); }
        static Row step(String n, String t, String cmd) { return new Row(n + "  " + t, BODY, cmd, COMMAND, 10, 0); }
    }

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();

    public ApiKeyNoticeScreen(Screen parent) {
        super(Component.literal("EarthMC 地图附加组件"));
        this.parent = parent;

        rows.add(Row.of("添加你的 EarthMC API 密钥", TITLE));
        rows.add(Row.of("可选——没有密钥，地图上的一切功能也已可用。", SUBTITLE));

        rows.add(Row.gap("已经可用", LABEL));
        rows.add(Row.bullet("城镇边界、国家颜色与城镇名称"));
        rows.add(Row.bullet("玩家、EarthMC 影像图层与搜索"));
        rows.add(Row.bullet("悬停城镇查看市长、国家与规模"));
        rows.add(Row.bullet("城镇卡片：市长、公告、金币、居民与领地"));

        rows.add(Row.gap("密钥还能显示", LABEL));
        rows.add(Row.bullet("开放与出售状态、领地上限"));
        rows.add(Row.bullet("活跃居民数"));
        rows.add(Row.bullet("超占与外来者出生点覆盖层"));
        rows.add(Row.bullet("玩家卡片与国家页面"));

        rows.add(Row.gap("添加方法", LABEL));
        // The button does steps 1 and 3; the manual route stays written down because it has to keep
        // working for anyone who declines the chat prompt, and because a panel reading only "press
        // the button" would leave them nothing to fall back on.
        //
        // copy first, create second. Which one a player needs depends on whether they already have
        // a key, which nothing here can know -- but copy is harmless when they do not, and create
        // is the one that would act on an account that is already set up.
        rows.add(Row.step("1", "Press \"" + BTN_CHAT + "\" - it runs ", "/api key copy"));
        rows.add(Row.step("2", "还没有密钥？它会提供 ", "/api key create"));
        rows.add(Row.step("3", "或者粘贴到 ", "设置 > 高级 > EarthMC API 密钥"));

        rows.add(Row.gap("EarthMC 的 API 自 11 月 19 日起要求提供密钥。", DIM));
        rows.add(Row.of("无论是否填写密钥，本模组的地图功能都不受影响。", DIM));
    }

    private int contentHeight() {
        int h = 0;
        for (Row r : rows) h += r.gapBefore() + LINE;
        return h;
    }

    private int buttonWidth(String label) {
        return this.font.width(label) + 22;
    }

    private int buttonRowWidth() {
        return buttonWidth(BTN_CHAT) + BTN_GAP + buttonWidth(BTN_SETTINGS) + BTN_GAP + buttonWidth(BTN_SKIP);
    }

    private int panelWidth() {
        int longest = 0;
        for (Row r : rows) {
            longest = Math.max(longest, r.indent() + this.font.width(r.text()) + this.font.width(r.tail()));
        }
        // The buttons are contents too. Three of them can out-measure every line of text, and a
        // panel sized to the text alone would put the last one over its own edge.
        longest = Math.max(longest, buttonRowWidth());
        return Math.min(this.width - 40, longest + PAD * 2);
    }

    private int panelHeight() {
        return PAD + contentHeight() + GAP + BTN_H + PAD;
    }

    @Override
    protected void init() {
        int w = panelWidth();
        int left = (this.width - w) / 2;
        int top = (this.height - panelHeight()) / 2;
        int btnY = top + PAD + contentHeight() + GAP;

        // Each sized to its own label so none crowds the next, and the row centred as a whole.
        int chatW = buttonWidth(BTN_CHAT);
        int openW = buttonWidth(BTN_SETTINGS);
        int skipW = buttonWidth(BTN_SKIP);
        int x = left + (w - buttonRowWidth()) / 2;

        // First, because it is the route that does not involve copying a 171-character token by
        // hand. Cancelling its prompt comes back here rather than dropping the player into the
        // world, so the panel is not spent by looking at the offer and declining it.
        this.addRenderableWidget(Button.builder(Component.literal(BTN_CHAT),
                        b -> {
                            net.townymap.TownyMapMod.markApiKeyNoticeSeen();
                            ApiKeyCaptureFlow.start(this);
                        })
                .bounds(x, btnY, chatW, BTN_H).build());
        x += chatW + BTN_GAP;
        this.addRenderableWidget(Button.builder(Component.literal(BTN_SETTINGS),
                        b -> {
                            net.townymap.TownyMapMod.markApiKeyNoticeSeen();
                            this.minecraft.setScreen(new TownyMapConfigScreen(parent));
                        })
                .bounds(x, btnY, openW, BTN_H).build());
        x += openW + BTN_GAP;
        this.addRenderableWidget(Button.builder(Component.literal(BTN_SKIP),
                        b -> this.onClose())
                .bounds(x, btnY, skipW, BTN_H).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        int w = panelWidth();
        int h = panelHeight();
        int left = (this.width - w) / 2;
        int top = (this.height - h) / 2;

        ctx.fill(left - 1, top - 1, left + w + 1, top + h + 1, PANEL_BORDER);
        ctx.fill(left, top, left + w, top + h, PANEL_BG);
        ctx.fill(left, top, left + w, top + 2, ACCENT);

        int y = top + PAD;
        for (Row r : rows) {
            y += r.gapBefore();
            int x = left + PAD + r.indent();
            if (r.indent() > 0) ctx.text(this.font, "-", left + PAD, y, BULLET, false);
            ctx.text(this.font, r.text(), x, y, r.color(), false);
            if (!r.tail().isEmpty()) {
                ctx.text(this.font, r.tail(), x + this.font.width(r.text()), y, r.tailColor(), false);
            }
            y += LINE;
        }

        // Widgets last, so the panel sits under the buttons rather than over them.
        //
        // Drawn first, they were painted out by a background that is 94% opaque: present, hovering,
        // clickable, and all but invisible. That is how this screen shipped, and why its two buttons
        // were never found -- not clipped, not mispositioned, just buried. TownyMapConfigScreen
        // draws in this order for the same reason, and says so above its row highlight.
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    /**
     * Esc dismisses it like the button; this is a notice, not a wall.
     *
     * <p>Dismissing is also what marks it read. The flag is deliberately not written when the panel
     * is posted: a notice cleared off the screen by a join that had not finished settling was never
     * read, and writing the flag then spent the only showing there would ever be. Both ways out of
     * this screen are a player's own action, so both are safe places to record it.
     */
    @Override
    public void onClose() {
        net.townymap.TownyMapMod.markApiKeyNoticeSeen();
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
