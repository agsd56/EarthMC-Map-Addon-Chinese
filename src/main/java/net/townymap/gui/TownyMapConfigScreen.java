package net.townymap.gui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.townymap.TownyMapConfig;
import net.townymap.TownyMapMod;
import net.townymap.mixin.CyclingButtonWidgetAccessor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

/**
 * Cloth-Config-style settings: a searchable, scrolling list of rows grouped under section headers.
 * Each option row shows its label on the left, its control on the right, and a per-row Reset button.
 * Right-clicking a cycling control steps it backward.
 */
public class TownyMapConfigScreen extends Screen {


    // ── Layout metrics ────────────────────────────────────────────────────────
    private static final int ROW_H = 24;
    private static final int SECTION_HEADER_H = 18;
    private static final int SECTION_GAP = 10;
    private static final int CTRL_W = 120;       // right-side control width
    private static final int RESET_W = 46;
    private static final int COL_GAP = 6;
    private static final int PANEL_PAD = 14;
    private static final int SCROLLBAR_W = 6;
    private static final int SIDEBAR_W = 96;     // category rail down the left of the body
    private static final int DESC_H = 28;        // help strip above the footer
    private static final int FOOTER_HEIGHT = 40;
    private static final int PANEL_TOP = 32;
    private static final int SEARCH_Y = 38;
    private static final int SEARCH_H = 16;
    private static final int BODY_TOP = 60;

    // 0xB8 (72%) rather than the old 0xE8 (91%): the map stays legible behind the panel, which is what
    // makes the screen feel like it belongs on top of the map instead of covering it.
    private static final int PANEL_BG = 0xB80E0F12;
    private static final int SIDEBAR_BG = 0x66080A0C;
    private static final int ROW_HOVER = 0x22FFFFFF;
    private static final int DESC_COLOR = 0xFF9CA3AF;
    private static final int PANEL_BORDER = 0xCC3A3D42;
    private static final int PANEL_ACCENT = 0xFF4FA37A;
    private static final int LABEL_COLOR = 0xFFE5E7EB;

    private static final Text YES = Text.literal("是").formatted(Formatting.GREEN);
    private static final Text NO = Text.literal("否").formatted(Formatting.RED);

    /** Help text for the settings whose label does not explain them on its own. Options not listed here
     *  are self-describing, so the strip simply stays empty rather than repeating the label back. */
    private static final Map<String, String> DESCRIPTIONS = Map.ofEntries(
            Map.entry("仅 EarthMC",
                    "仅在 EarthMC 上运行。关闭后可在任意服务器使用叠加层。"),
            Map.entry("下界中的 EarthMC 地图",
                    "地图只覆盖主世界。选择在下界中隐藏它，还是转换坐标。"),
            Map.entry("平滑城镇轮廓",
                    "平滑的 在线地图 风格边界。关闭后使用原本的方块状区块边缘。"),
            Map.entry("远缩放城镇圆点",
                    "远距离缩小时把小城镇收拢为一个点。关闭则保留其真实形状。"),
            Map.entry("真实国界",
                    "在城镇下方绘制来自 Natural Earth 数据的真实国家与州、省边界。"),
            Map.entry("在线地图 背景",
                    "在叠加层后方显示 map.earthmc.net 的影像，而不是 Xaero 自带的地图瓦片。"
                    + "可选择世界地图、小地图、两者或关闭。"),
            Map.entry("地图变暗",
                    "压暗地图影像，让城镇边界和玩家光点更醒目。"),
            Map.entry("世界地图全览",
                    "允许超出 Xaero 的常规缩放上限，以查看整张 EarthMC 地图。"),
            Map.entry("深色按钮",
                    "地图上的按钮和本界面使用扁平深色样式，替代原版材质。"),
            Map.entry("荒野玩家警报",
                    "当本国以外的玩家靠近时，在小地图上闪烁警告。"),
            Map.entry("玩家名称范围",
                    "玩家名称在屏幕上保留的距离，超出后逐渐淡出。"),
            Map.entry("城镇/国家范围",
                    "绘制城镇和国家名称的缩放范围。"),
            Map.entry("地图模式 RGB",
                    "所有城镇使用同一种颜色，而不是各自国家的颜色。"),
            Map.entry("自定义叠加层",
                    "从配置文件夹加载你自己的 GeoJSON 叠加层。"),
            Map.entry("玩家指示器",
                    "哪个小地图背景显示标记你的箭头：在 EMC 叠加层上、在 Xaero 自己的地图上、"
                    + "两者，或关闭。关闭也会在 EMC 叠加层下隐藏它（该层会遮住 "
                    + "Xaero 自己的箭头）。"),
            Map.entry("区块网格",
                    "在小地图上绘制区块边界。"),
            Map.entry("玩家头像",
                    "在地图上的玩家光点处显示皮肤头像。可选择世界地图、小地图、两者或关闭。"),
            Map.entry("头像范围",
                    "头像出现所需的缩放程度。近 = 只有很近时显示，远 = 更远时也显示。"),
            Map.entry("最后出现位置",
                    "玩家离线或隐藏后，用红色在其最后位置保留标记，每隔几秒重新检查。"),
            Map.entry("国家首都星标",
                    "在世界地图上用星标标出每个国家的首都。"),
            Map.entry("国家加入范围",
                    "选中某个国家时，用阴影显示城镇可以加入它的范围 — 首都周围 5k，加上每个城镇周围 1.5k。"),
            Map.entry("界面缩放",
                    "把本模组的所有界面（按钮、面板、本设置界面）整体缩小。100% 保持当前尺寸；"
                    + "数值越小，文字和间距越小，与 Minecraft 的界面缩放无关。"),
            Map.entry("截图包含玩家",
                    "地图截图中包含实时玩家光点。关闭则只截取地图本身。"),
            Map.entry("截图包含国家星标",
                    "地图截图中保留国家首都星标。"),
            Map.entry("截图隐藏变暗城镇",
                    "在筛选或联盟图层下，把被涂黑的城镇完全排除在截图之外，"
                    + "而不是截成黑色色块。"),
            Map.entry("地图截图快捷键",
                    "保存一张干净世界地图截图的按键 — 不含按钮、搜索栏或面板。"
                    + "与“选项 > 控制”中的绑定相同，任一处修改都会同步。"),
            Map.entry("数据新鲜度提示",
                    "在世界地图坐标下方显示当前领地数据的时效，"
                    + "并带一个重新加载按钮。数据过期时变琥珀色，刷新失败时变红色。"),
            Map.entry("重新加载领地",
                    "立即从 在线地图 获取城镇和领地，"
                    + "而不等待每 60 秒一次的自动刷新。"),
            Map.entry("打开信息面板",
                    "城镇与国家排行榜，基于已加载的领地数据构建，因此打开即可用，"
                    + "EarthMC API 宕机时也能工作。每个名称都可点击。"),
            Map.entry("信息面板快捷键",
                    "无需先打开地图即可打开信息面板的按键。默认未绑定。"
                    + "与“选项 > 控制”中的绑定相同，任一处修改都会同步。"),
            Map.entry("重载领地快捷键",
                    "无需打开地图即可重新加载城镇与领地的按键。默认未绑定。"
                    + "与“选项 > 控制”中的绑定相同，任一处修改都会同步。"),
            Map.entry("查看存档",
                    "输入日期（dd/mm/yyyy）并按回车，查看那天的历史地图。"
                    + "也可以在世界地图搜索栏中操作，使用 . , 或 / 分隔（例如 17/4/2026）。"));

    /** Field-initialised defaults, used to drive the per-row Reset buttons. */
    private static final TownyMapConfig DEFAULTS = new TownyMapConfig();

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private TownyMapConfig cfg;
    private TextFieldWidget searchField;
    private TextFieldWidget archiveField;   // Advanced → type a dd/mm/yyyy date + Enter to open the archive
    private ButtonWidget screenshotKeyButton;
    private ButtonWidget refreshKeyButton;
    private ButtonWidget statsKeyButton;   // Advanced → rebind the clean-map-screenshot key
    private boolean awaitingScreenshotKey;
    private boolean awaitingRefreshKey;
    private boolean awaitingStatsKey;
    private String searchQuery = "";
    private int scrollOffset;
    private int contentHeight;
    private final List<String> categories = new ArrayList<>();
    private String activeCategory;
    private String currentCategory;          // section being populated during init()
    private String hoveredDescription;

    // Geometry, recomputed each init() (handles resize).
    private int panelLeft, panelWidth, innerRight, contentLeft, contentRight, sidebarLeft;
    private int labelX, labelMaxW, ctrlX, resetX;

    public TownyMapConfigScreen(Screen parent) {
        super(Text.literal("EarthMC 地图附加组件设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        cfg = TownyMapMod.getConfig();
        rows.clear();

        panelWidth = Math.max(400, Math.min(this.width - 40, 560));
        panelLeft = (this.width - panelWidth) / 2;
        innerRight = panelLeft + panelWidth - PANEL_PAD;
        sidebarLeft = panelLeft;
        contentLeft = panelLeft + SIDEBAR_W + PANEL_PAD;
        contentRight = innerRight - SCROLLBAR_W;
        resetX = contentRight - RESET_W;
        ctrlX = resetX - COL_GAP - CTRL_W;
        labelX = contentLeft;
        labelMaxW = Math.max(40, ctrlX - COL_GAP - labelX);

        searchField = new TextFieldWidget(this.textRenderer, panelLeft + PANEL_PAD, SEARCH_Y,
                innerRight - (panelLeft + PANEL_PAD), SEARCH_H, Text.literal("搜索"));
        searchField.setMaxLength(64);
        searchField.setPlaceholder(Text.literal("搜索…").formatted(Formatting.DARK_GRAY));
        searchField.setText(searchQuery);
        searchField.setChangedListener(q -> { searchQuery = q; relayout(); });
        this.addDrawableChild(searchField);

        section("通用");
        option("在线地图 背景", cycle(cfg.squaremapBackgroundMode, new int[]{0, 1, 2, 3},
                        TownyMapConfigScreen::squaremapBackgroundModeText,
                        v -> cfg.squaremapBackgroundMode = v),
                () -> cfg.squaremapBackgroundMode == DEFAULTS.squaremapBackgroundMode,
                () -> cfg.squaremapBackgroundMode = DEFAULTS.squaremapBackgroundMode);
        option("深色按钮", onOff(cfg.darkButtons, v -> cfg.darkButtons = v),
                () -> cfg.darkButtons == DEFAULTS.darkButtons,
                () -> cfg.darkButtons = DEFAULTS.darkButtons);
        option("地图变暗", cycle(cfg.squaremapDarken, new int[]{0, 1, 2, 3},
                        TownyMapConfigScreen::darkenText, v -> cfg.squaremapDarken = v),
                () -> cfg.squaremapDarken == DEFAULTS.squaremapDarken,
                () -> cfg.squaremapDarken = DEFAULTS.squaremapDarken);
        option("仅 EarthMC", onOff(cfg.earthmcOnly, v -> cfg.earthmcOnly = v),
                () -> cfg.earthmcOnly == DEFAULTS.earthmcOnly,
                () -> cfg.earthmcOnly = DEFAULTS.earthmcOnly);
        option("下界中的 EarthMC 地图", cycle(cfg.netherMode == 2 ? 2 : 1, new int[]{1, 2},
                        TownyMapConfigScreen::netherModeText, v -> cfg.netherMode = v),
                () -> cfg.netherMode == DEFAULTS.netherMode,
                () -> cfg.netherMode = DEFAULTS.netherMode);

        section("小地图");
        option("城镇名称", cycle(cfg.minimapTownNameMode, new int[]{0, 1, 2, 3},
                        TownyMapConfigScreen::minimapTownNameModeText,
                        v -> { cfg.minimapTownNameMode = v; cfg.minimapTownNamesEnabled = v != 0; }),
                () -> cfg.minimapTownNameMode == DEFAULTS.minimapTownNameMode,
                () -> { cfg.minimapTownNameMode = DEFAULTS.minimapTownNameMode;
                        cfg.minimapTownNamesEnabled = DEFAULTS.minimapTownNameMode != 0; });
        option("玩家指示器", cycle(cfg.minimapIndicatorMode, new int[]{0, 1, 2, 3},
                        TownyMapConfigScreen::minimapIndicatorModeText,
                        v -> cfg.minimapIndicatorMode = v),
                () -> cfg.minimapIndicatorMode == DEFAULTS.minimapIndicatorMode,
                () -> cfg.minimapIndicatorMode = DEFAULTS.minimapIndicatorMode);
        option("小地图上的城镇领地",
                onOff(cfg.minimapTownClaimsEnabled, v -> cfg.minimapTownClaimsEnabled = v),
                () -> cfg.minimapTownClaimsEnabled == DEFAULTS.minimapTownClaimsEnabled,
                () -> cfg.minimapTownClaimsEnabled = DEFAULTS.minimapTownClaimsEnabled);
        option("小地图上的玩家", onOff(cfg.minimapPlayersEnabled, v -> cfg.minimapPlayersEnabled = v),
                () -> cfg.minimapPlayersEnabled == DEFAULTS.minimapPlayersEnabled,
                () -> cfg.minimapPlayersEnabled = DEFAULTS.minimapPlayersEnabled);
        option("区块网格", cycle(cfg.minimapChunkGridMode, new int[]{0, 1, 2},
                        TownyMapConfigScreen::minimapChunkGridModeText, v -> cfg.minimapChunkGridMode = v),
                () -> cfg.minimapChunkGridMode == DEFAULTS.minimapChunkGridMode,
                () -> cfg.minimapChunkGridMode = DEFAULTS.minimapChunkGridMode);
        option("荒野玩家警报", onOff(cfg.minimapNationAlertEnabled, v -> cfg.minimapNationAlertEnabled = v),
                () -> cfg.minimapNationAlertEnabled == DEFAULTS.minimapNationAlertEnabled,
                () -> cfg.minimapNationAlertEnabled = DEFAULTS.minimapNationAlertEnabled);
        option("下界中隐藏小地图", onOff(cfg.hideMinimapInNether, v -> cfg.hideMinimapInNether = v),
                () -> cfg.hideMinimapInNether == DEFAULTS.hideMinimapInNether,
                () -> cfg.hideMinimapInNether = DEFAULTS.hideMinimapInNether);

        section("世界地图");
        option("城镇边界", onOff(cfg.townsEnabled, v -> cfg.townsEnabled = v),
                () -> cfg.townsEnabled == DEFAULTS.townsEnabled,
                () -> cfg.townsEnabled = DEFAULTS.townsEnabled);
        option("地图世界", cycle(cfg.mapWorldMode, new int[]{0, 1, 2},
                        TownyMapConfigScreen::mapWorldModeText,
                        v -> cfg.mapWorldMode = v),
                () -> cfg.mapWorldMode == DEFAULTS.mapWorldMode,
                () -> cfg.mapWorldMode = DEFAULTS.mapWorldMode);
        option("数据新鲜度提示", onOff(cfg.dataStatusEnabled, v -> cfg.dataStatusEnabled = v),
                () -> cfg.dataStatusEnabled == DEFAULTS.dataStatusEnabled,
                () -> cfg.dataStatusEnabled = DEFAULTS.dataStatusEnabled);
        option("刷新时重建全部",
                onOff(cfg.refreshEntireMap, v -> cfg.refreshEntireMap = v),
                () -> cfg.refreshEntireMap == DEFAULTS.refreshEntireMap,
                () -> cfg.refreshEntireMap = DEFAULTS.refreshEntireMap);
        option("搜索定位自动缩放", onOff(cfg.searchZoomToFit, v -> cfg.searchZoomToFit = v),
                () -> cfg.searchZoomToFit == DEFAULTS.searchZoomToFit,
                () -> cfg.searchZoomToFit = DEFAULTS.searchZoomToFit);
        option("搜索结果闪烁", onOff(cfg.searchFlash, v -> cfg.searchFlash = v),
                () -> cfg.searchFlash == DEFAULTS.searchFlash,
                () -> cfg.searchFlash = DEFAULTS.searchFlash);
        action("重新加载领地", TownyMapMod::refreshTownClaimsFromSettings);
        refreshKeyButton = ButtonWidget.builder(refreshKeyLabel(), b -> {
            awaitingRefreshKey = true;
            b.setMessage(Text.literal("> 请按键 <"));
        }).dimensions(ctrlX, 0, CTRL_W, 20).build();
        statsKeyButton = ButtonWidget.builder(statsKeyLabel(), b -> {
            awaitingStatsKey = true;
            b.setMessage(Text.literal("> 请按键 <"));
        }).dimensions(ctrlX, 0, CTRL_W, 20).build();
        option("信息面板快捷键", statsKeyButton,
                () -> "未绑定".equals(net.townymap.input.TownyMapKeybinds.openStatsKeyName()),
                () -> {
                    net.townymap.input.TownyMapKeybinds.setOpenStatsKey(GLFW.GLFW_KEY_UNKNOWN);
                    statsKeyButton.setMessage(statsKeyLabel());
                });
        action("打开信息面板", TownyMapMod::openStatsPanel);
        option("重载领地快捷键", refreshKeyButton,
                () -> "未绑定".equals(net.townymap.input.TownyMapKeybinds.refreshTownsKeyName()),
                () -> {
                    net.townymap.input.TownyMapKeybinds.setRefreshTownsKey(GLFW.GLFW_KEY_UNKNOWN);
                    refreshKeyButton.setMessage(refreshKeyLabel());
                });
        option("世界地图全览", onOff(cfg.worldMapOverview, v -> cfg.worldMapOverview = v),
                () -> cfg.worldMapOverview == DEFAULTS.worldMapOverview,
                () -> cfg.worldMapOverview = DEFAULTS.worldMapOverview);
        option("国家首都星标", onOff(cfg.nationStarsEnabled, v -> cfg.nationStarsEnabled = v),
                () -> cfg.nationStarsEnabled == DEFAULTS.nationStarsEnabled,
                () -> cfg.nationStarsEnabled = DEFAULTS.nationStarsEnabled);
        option("国家加入范围", onOff(cfg.nationRangeEnabled, v -> cfg.nationRangeEnabled = v),
                () -> cfg.nationRangeEnabled == DEFAULTS.nationRangeEnabled,
                () -> cfg.nationRangeEnabled = DEFAULTS.nationRangeEnabled);
        // ON = smooth squaremap-style diagonals, OFF = the original blocky chunk-aligned borders.
        option("平滑城镇轮廓", onOff(cfg.smoothTownOutlines, v -> cfg.smoothTownOutlines = v),
                () -> cfg.smoothTownOutlines == DEFAULTS.smoothTownOutlines,
                () -> cfg.smoothTownOutlines = DEFAULTS.smoothTownOutlines);
        // OFF = towns keep their outline at far zoom, ON = small towns collapse to a dot.
        option("远缩放城镇圆点", onOff(cfg.farZoomTownDots, v -> cfg.farZoomTownDots = v),
                () -> cfg.farZoomTownDots == DEFAULTS.farZoomTownDots,
                () -> cfg.farZoomTownDots = DEFAULTS.farZoomTownDots);
        option("真实国界", cycle(cfg.borderOverlayMode, new int[]{0, 1, 2},
                        TownyMapConfigScreen::borderModeText, v -> cfg.borderOverlayMode = v),
                () -> cfg.borderOverlayMode == DEFAULTS.borderOverlayMode,
                () -> cfg.borderOverlayMode = DEFAULTS.borderOverlayMode);
        option("边界粗细", presets(cfg.borderThicknessMultiplier, THICKNESSES, THIN_MED_THICK,
                        v -> cfg.borderThicknessMultiplier = (float) v),
                () -> cfg.borderThicknessMultiplier == DEFAULTS.borderThicknessMultiplier,
                () -> cfg.borderThicknessMultiplier = DEFAULTS.borderThicknessMultiplier);
        option("地图模式 RGB", onOff(cfg.statusHighlightRainbow, v -> cfg.statusHighlightRainbow = v),
                () -> cfg.statusHighlightRainbow == DEFAULTS.statusHighlightRainbow,
                () -> cfg.statusHighlightRainbow = DEFAULTS.statusHighlightRainbow);
        option("地图模式颜色", new StatusHighlightHueSlider(ctrlX, 0, CTRL_W, 20, cfg),
                () -> cfg.statusHighlightColor == DEFAULTS.statusHighlightColor,
                () -> cfg.statusHighlightColor = DEFAULTS.statusHighlightColor);

        section("玩家");
        option("在线玩家", onOff(cfg.playersEnabled, v -> cfg.playersEnabled = v),
                () -> cfg.playersEnabled == DEFAULTS.playersEnabled,
                () -> cfg.playersEnabled = DEFAULTS.playersEnabled);
        option("玩家头像", cycle(cfg.playerHeadMode, new int[]{0, 1, 2, 3},
                        TownyMapConfigScreen::playerHeadModeText, v -> cfg.playerHeadMode = v),
                () -> cfg.playerHeadMode == DEFAULTS.playerHeadMode,
                () -> cfg.playerHeadMode = DEFAULTS.playerHeadMode);
        option("头像范围", presets(cfg.playerHeadMinScale, HEAD_RANGES, NEAR_MED_FAR,
                        v -> cfg.playerHeadMinScale = v),
                () -> cfg.playerHeadMinScale == DEFAULTS.playerHeadMinScale,
                () -> cfg.playerHeadMinScale = DEFAULTS.playerHeadMinScale);
        option("最后出现位置", onOff(cfg.playerLastSeen, v -> cfg.playerLastSeen = v),
                () -> cfg.playerLastSeen == DEFAULTS.playerLastSeen,
                () -> cfg.playerLastSeen = DEFAULTS.playerLastSeen);
        option("玩家名称颜色", new ColorHueSlider(ctrlX, 0, CTRL_W, 20, cfg,
                        () -> cfg.playerLabelColor, v -> cfg.playerLabelColor = v),
                () -> cfg.playerLabelColor == DEFAULTS.playerLabelColor,
                () -> cfg.playerLabelColor = DEFAULTS.playerLabelColor);
        option("玩家名称", onOff(cfg.showPlayerNames, v -> cfg.showPlayerNames = v),
                () -> cfg.showPlayerNames == DEFAULTS.showPlayerNames,
                () -> cfg.showPlayerNames = DEFAULTS.showPlayerNames);
        option("玩家名称范围", presets(cfg.playerNameMinScale, NAME_RANGES, NEAR_MED_FAR,
                        v -> cfg.playerNameMinScale = v),
                () -> cfg.playerNameMinScale == DEFAULTS.playerNameMinScale,
                () -> cfg.playerNameMinScale = DEFAULTS.playerNameMinScale);
        option("城镇/国家范围", presets(cfg.playerAffiliationMinScale, AFFIL_RANGES, NEAR_MED_FAR,
                        v -> cfg.playerAffiliationMinScale = v),
                () -> cfg.playerAffiliationMinScale == DEFAULTS.playerAffiliationMinScale,
                () -> cfg.playerAffiliationMinScale = DEFAULTS.playerAffiliationMinScale);

        section("信息显示");
        option("当前城镇与国家", onOff(cfg.infoDisplayTownEnabled, v -> cfg.infoDisplayTownEnabled = v),
                () -> cfg.infoDisplayTownEnabled == DEFAULTS.infoDisplayTownEnabled,
                () -> cfg.infoDisplayTownEnabled = DEFAULTS.infoDisplayTownEnabled);
        option("附近玩家", onOff(cfg.infoDisplayNearbyPlayersEnabled, v -> cfg.infoDisplayNearbyPlayersEnabled = v),
                () -> cfg.infoDisplayNearbyPlayersEnabled == DEFAULTS.infoDisplayNearbyPlayersEnabled,
                () -> cfg.infoDisplayNearbyPlayersEnabled = DEFAULTS.infoDisplayNearbyPlayersEnabled);
        option("最近城镇（荒野）", onOff(cfg.infoDisplayNearestTownEnabled, v -> cfg.infoDisplayNearestTownEnabled = v),
                () -> cfg.infoDisplayNearestTownEnabled == DEFAULTS.infoDisplayNearestTownEnabled,
                () -> cfg.infoDisplayNearestTownEnabled = DEFAULTS.infoDisplayNearestTownEnabled);

        section("截图");
        option("截图包含玩家", onOff(cfg.screenshotPlayers, v -> cfg.screenshotPlayers = v),
                () -> cfg.screenshotPlayers == DEFAULTS.screenshotPlayers,
                () -> cfg.screenshotPlayers = DEFAULTS.screenshotPlayers);
        option("截图包含国家星标",
                onOff(cfg.screenshotNationStars, v -> cfg.screenshotNationStars = v),
                () -> cfg.screenshotNationStars == DEFAULTS.screenshotNationStars,
                () -> cfg.screenshotNationStars = DEFAULTS.screenshotNationStars);
        option("截图隐藏变暗城镇",
                onOff(cfg.screenshotHideDimmedTowns, v -> cfg.screenshotHideDimmedTowns = v),
                () -> cfg.screenshotHideDimmedTowns == DEFAULTS.screenshotHideDimmedTowns,
                () -> cfg.screenshotHideDimmedTowns = DEFAULTS.screenshotHideDimmedTowns);

        // The screenshot bind, editable here as well as in vanilla Controls — both write the same KeyBinding.
        screenshotKeyButton = ButtonWidget.builder(screenshotKeyLabel(), b -> {
            awaitingScreenshotKey = true;
            b.setMessage(Text.literal("> 请按键 <"));
        }).dimensions(ctrlX, 0, CTRL_W, 20).build();
        option("地图截图快捷键", screenshotKeyButton,
                () -> "P".equalsIgnoreCase(net.townymap.input.TownyMapKeybinds.mapScreenshotKeyName()),
                () -> {
                    net.townymap.input.TownyMapKeybinds.setMapScreenshotKey(GLFW.GLFW_KEY_P);
                    screenshotKeyButton.setMessage(screenshotKeyLabel());
                });

        section("高级");
        option("界面缩放", new PanelScaleSlider(ctrlX, 0, CTRL_W, 20, cfg),
                () -> cfg.infoPanelScale == DEFAULTS.infoPanelScale,
                () -> cfg.infoPanelScale = DEFAULTS.infoPanelScale);
        option("自定义叠加层", onOff(cfg.customOverlaysEnabled, v -> {
                    cfg.customOverlaysEnabled = v;
                    if (v) net.townymap.integration.CustomOverlayManager.reload();
                }),
                () -> cfg.customOverlaysEnabled == DEFAULTS.customOverlaysEnabled,
                () -> cfg.customOverlaysEnabled = DEFAULTS.customOverlaysEnabled);
        option("叠加层标签", onOff(cfg.customOverlayLabels, v -> cfg.customOverlayLabels = v),
                () -> cfg.customOverlayLabels == DEFAULTS.customOverlayLabels,
                () -> cfg.customOverlayLabels = DEFAULTS.customOverlayLabels);
        action("打开叠加层文件夹", () -> net.townymap.integration.CustomOverlayManager.openFolder());
        option("商店路径点", onOff(cfg.shopWaypointsEnabled, v -> {
                    cfg.shopWaypointsEnabled = v;
                    if (!v) net.townymap.integration.ShopWaypoints.clearAll();
                }),
                () -> cfg.shopWaypointsEnabled == DEFAULTS.shopWaypointsEnabled,
                () -> cfg.shopWaypointsEnabled = DEFAULTS.shopWaypointsEnabled);
        option("商店路径点范围", cycle(nearestPreset(cfg.shopWaypointRange, SHOP_RANGES),
                        new int[]{0, 1, 2}, i -> Text.literal(SHOP_RANGE_LABELS[i]),
                        i -> cfg.shopWaypointRange = (int) SHOP_RANGES[i]),
                () -> cfg.shopWaypointRange == DEFAULTS.shopWaypointRange,
                () -> cfg.shopWaypointRange = DEFAULTS.shopWaypointRange);
        action("重新加载叠加层", () -> net.townymap.integration.CustomOverlayManager.reload());

        archiveField = new TextFieldWidget(this.textRenderer, ctrlX, 0, CTRL_W, 20, Text.literal("dd/mm/yyyy"));
        archiveField.setPlaceholder(Text.literal("dd/mm/yyyy"));
        archiveField.setMaxLength(14);
        inputRow("查看存档", archiveField);


        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("全部重置"), b -> {
                    TownyMapConfig d = new TownyMapConfig();
                    d.copyInto(cfg);
                    cfg.save();
                    this.clearAndInit();
                }).dimensions(panelLeft + PANEL_PAD, this.height - 30, 80, 20).build());
        this.addDrawableChild(
                ButtonWidget.builder(ScreenTexts.DONE, b -> this.close())
                        .dimensions(innerRight - 100, this.height - 30, 100, 20).build());

        relayout();
    }

    // ── Row building ──────────────────────────────────────────────────────────

    private void section(String label) {
        currentCategory = label;
        if (!categories.contains(label)) categories.add(label);
        rows.add(new Row(label, null, null, false, null, label));
    }

    private void option(String label, ClickableWidget control, BooleanSupplier isDefault, Runnable resetAction) {
        ButtonWidget reset = ButtonWidget.builder(Text.literal("重置"), b -> {
            resetAction.run();
            cfg.save();
            this.clearAndInit();
        }).dimensions(resetX, 0, RESET_W, 20).build();
        rows.add(new Row(label, control, reset, false, isDefault, currentCategory));
        this.addDrawableChild(control);
        this.addDrawableChild(reset);
    }

    /** A label + input control row with no Reset button (for the archive-date field). */
    private void inputRow(String label, ClickableWidget control) {
        rows.add(new Row(label, control, null, false, null, currentCategory));
        this.addDrawableChild(control);
    }

    private void action(String label, Runnable onClick) {
        ButtonWidget b = ButtonWidget.builder(Text.literal(label), x -> onClick.run())
                .dimensions(contentLeft, 0, Math.max(40, contentRight - contentLeft), 20).build();
        rows.add(new Row(label, b, null, true, null, currentCategory));
        this.addDrawableChild(b);
    }

    private CyclingButtonWidget<Boolean> onOff(boolean value, Consumer<Boolean> setter) {
        return CyclingButtonWidget.onOffBuilder(YES, NO, value).omitKeyText()
                .build(ctrlX, 0, CTRL_W, 20, Text.empty(), (btn, val) -> { setter.accept(val); cfg.save(); });
    }

    private CyclingButtonWidget<Integer> cycle(int value, int[] values, Function<Integer, Text> toText, IntConsumer setter) {
        Integer[] boxed = new Integer[values.length];
        for (int i = 0; i < values.length; i++) boxed[i] = values[i];
        return CyclingButtonWidget.builder(toText, value).values(boxed).omitKeyText()
                .build(ctrlX, 0, CTRL_W, 20, Text.empty(), (btn, val) -> { setter.accept(val); cfg.save(); });
    }

    // ── Preset "adjusters": a few labelled steps instead of a slider ──────────────
    private static final String[] NEAR_MED_FAR = {"近", "中", "远"};
    // How far you can wander from a /qs find shop before its waypoint is dropped.
    private static final double[] SHOP_RANGES = {120, 250, 500};
    private static final String[] SHOP_RANGE_LABELS = {"120m", "250m", "500m"};
    // Range presets are world-map block-scale thresholds: bigger = must be more zoomed in (Near),
    // smaller = shows from further out (Far).
    static final double[] NAME_RANGES = {0.15, 0.08, 0.035};
    static final double[] AFFIL_RANGES = {0.18, 0.108, 0.05};
    static final double[] HEAD_RANGES = {0.12, 0.06, 0.025};
    private static final String[] THIN_MED_THICK = {"细", "中", "粗"};
    static final double[] THICKNESSES = {0.5f, 1.5f, 3.0f};

    /** A cycling button over a few preset values, so an adjuster is a few clear steps rather than a slider.
     *  The shown step is whichever preset the stored value is closest to. */
    private CyclingButtonWidget<Integer> presets(double current, double[] values, String[] labels,
                                                 java.util.function.DoubleConsumer setter) {
        int[] idx = new int[values.length];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        return cycle(nearestPreset(current, values), idx, i -> Text.literal(labels[i]),
                i -> setter.accept(values[i]));
    }

    private static int nearestPreset(double current, double[] values) {
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < values.length; i++) {
            double d = Math.abs(current - values[i]);
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }

    // ── Layout / filtering ──────────────────────────────────────────────────────

    /** Assign content-space Y to each visible row (filtered by the search query), then position widgets. */
    private void relayout() {
        boolean searching = !searchQuery.isBlank();
        String needle = searchQuery.toLowerCase(Locale.ROOT);
        if (activeCategory == null && !categories.isEmpty()) activeCategory = categories.get(0);
        int y = 0;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (r.isSection()) {
                // Categories are the left rail now, so the in-list header only appears while searching,
                // where results span categories and need to say which one they came from.
                boolean any = searching && sectionHasMatch(i, needle);
                r.visible = any;
                if (any) {
                    if (y > 0) y += SECTION_GAP;
                    r.contentY = y;
                    y += SECTION_HEADER_H;
                }
            } else {
                boolean match = searching ? matches(r, needle)
                                          : java.util.Objects.equals(r.category, activeCategory);
                r.visible = match;
                if (match) {
                    r.contentY = y;
                    y += ROW_H;
                }
            }
        }
        contentHeight = y + 8;
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll()));
        applyWidgetLayout();
    }

    private boolean sectionHasMatch(int sectionIndex, String needle) {
        for (int j = sectionIndex + 1; j < rows.size(); j++) {
            Row r = rows.get(j);
            if (r.isSection()) break;
            if (matches(r, needle)) return true;
        }
        return false;
    }

    private boolean matches(Row r, String needle) {
        return r.label != null && r.label.toLowerCase(Locale.ROOT).contains(needle);
    }

    /** Apply scroll offset to widget Y and hide rows outside the body / filtered out. */
    private void applyWidgetLayout() {
        int top = bodyTop();
        int bottom = bodyBottom();
        for (Row r : rows) {
            if (r.isSection()) continue;
            int rowY = top + r.contentY - scrollOffset;
            boolean show = r.visible && rowY + 20 >= top + 2 && rowY <= bottom - 2;
            if (r.control != null) {
                r.control.setY(rowY);
                r.control.visible = show;
            }
            if (r.reset != null) {
                r.reset.setY(rowY);
                r.reset.visible = show;
            }
        }
    }

    private void refreshResetStates() {
        for (Row r : rows) {
            if (r.reset != null && r.isDefault != null) {
                r.reset.active = !r.isDefault.getAsBoolean();
            }
        }
    }

    // ── Input ───────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        // Un-scale the click so it matches the UI-Scale-shrunk layout.
        if (UiScale.active()) click = UiScale.unscaleClick(click, this.width / 2.0, this.height / 2.0);
        // Right-click a cycling control to step it backward (mirrors the in-game map buttons).
        // Category rail
        if (click.button() == 0 && searchQuery.isBlank()
                && click.x() >= sidebarLeft && click.x() < sidebarLeft + SIDEBAR_W
                && click.y() >= bodyTop() && click.y() < bodyBottom()) {
            int idx = (int) ((click.y() - (bodyTop() + 4)) / 18);
            if (idx >= 0 && idx < categories.size()) {
                activeCategory = categories.get(idx);
                scrollOffset = 0;
                relayout();
                if (this.client != null) {
                    this.client.getSoundManager().play(
                            PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
                return true;
            }
        }
        if (click.button() == 1) {
            double mx = click.x();
            double my = click.y();
            for (Row r : rows) {
                if (r.control instanceof CyclingButtonWidget<?> cycling
                        && r.control.visible && r.control.isMouseOver(mx, my)) {
                    ((CyclingButtonWidgetAccessor) cycling).townymap$cycle(-1);
                    if (this.client != null) {
                        this.client.getSoundManager().play(
                                PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (UiScale.active()) {
            mouseX = UiScale.unscale(mouseX, this.width / 2.0);
            mouseY = UiScale.unscale(mouseY, this.height / 2.0);
        }
        int maxScroll = maxScroll();
        if (maxScroll <= 0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.round(verticalAmount * 24.0)));
        applyWidgetLayout();
        return true;
    }

    private Text statsKeyLabel() {
        return Text.literal(net.townymap.input.TownyMapKeybinds.openStatsKeyName());
    }

    private Text refreshKeyLabel() {
        return Text.literal(net.townymap.input.TownyMapKeybinds.refreshTownsKeyName());
    }

    private Text screenshotKeyLabel() {
        return Text.literal(net.townymap.input.TownyMapKeybinds.mapScreenshotKeyName());
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        // Rebinding: the next key becomes the screenshot bind (Escape clears it, as vanilla Controls does).
        if (awaitingStatsKey) {
            awaitingStatsKey = false;
            int key = input.key() == GLFW.GLFW_KEY_ESCAPE ? GLFW.GLFW_KEY_UNKNOWN : input.key();
            net.townymap.input.TownyMapKeybinds.setOpenStatsKey(key);
            if (statsKeyButton != null) statsKeyButton.setMessage(statsKeyLabel());
            return true;
        }
        if (awaitingRefreshKey) {
            awaitingRefreshKey = false;
            int key = input.key() == GLFW.GLFW_KEY_ESCAPE ? GLFW.GLFW_KEY_UNKNOWN : input.key();
            net.townymap.input.TownyMapKeybinds.setRefreshTownsKey(key);
            if (refreshKeyButton != null) refreshKeyButton.setMessage(refreshKeyLabel());
            return true;
        }
        if (awaitingScreenshotKey) {
            awaitingScreenshotKey = false;
            int key = input.key() == GLFW.GLFW_KEY_ESCAPE ? GLFW.GLFW_KEY_UNKNOWN : input.key();
            net.townymap.input.TownyMapKeybinds.setMapScreenshotKey(key);
            if (screenshotKeyButton != null) screenshotKeyButton.setMessage(screenshotKeyLabel());
            return true;
        }
        // Enter in the archive-date field opens that day's archive and closes settings.
        if (archiveField != null && archiveField.isFocused()
                && (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER)) {
            submitArchive();
            return true;
        }
        return super.keyPressed(input);
    }

    private void submitArchive() {
        int date = TownSearchOverlay.parseArchiveDate(archiveField.getText());
        if (date > 0) {
            TownyMapMod.enterArchive(date);
            this.close();
        } else {
            archiveField.setText("");   // reject malformed / pre-17-Apr-2026 input; placeholder re-guides
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // UI Scale: shrink the whole screen around its centre; the mouse is un-scaled to match so hover/clicks
        // still line up (mouseClicked/mouseScrolled un-scale the same way).
        if (!UiScale.active()) { drawContent(ctx, mouseX, mouseY, delta); return; }
        float cx = this.width / 2f, cy = this.height / 2f;
        int mx = (int) Math.round(UiScale.unscale(mouseX, cx));
        int my = (int) Math.round(UiScale.unscale(mouseY, cy));
        UiScale.push(ctx, cx, cy);
        try { drawContent(ctx, mx, my, delta); }
        finally { UiScale.pop(ctx); }
    }

    private void drawContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderPanel(ctx);
        refreshResetStates();
        // Highlight before the widgets so the tint sits under the controls, not over them.
        drawRowHighlight(ctx, mouseX, mouseY);
        super.render(ctx, mouseX, mouseY, delta);
        if (DarkButtons.enabled()) drawDarkWidgetOverlay(ctx, mouseX, mouseY);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 14, 0xFFFFFFFF);
        drawSidebar(ctx, mouseX, mouseY);
        drawSectionsAndLabels(ctx);
        drawDescription(ctx);
        drawScrollbar(ctx);
        drawScrollFades(ctx);
    }

    /** With "Dark Buttons" on, re-skin the vanilla button/cycle widgets (which self-render as the light
     *  textured style) with the flat dark style, so the settings screen matches the on-map buttons. The real
     *  widgets stay underneath and keep handling clicks; we just paint over their visible ones. */
    private void drawDarkWidgetOverlay(DrawContext ctx, int mouseX, int mouseY) {
        for (var child : this.children()) {
            if (child instanceof ClickableWidget w && w.visible
                    && (w instanceof ButtonWidget || w instanceof CyclingButtonWidget<?>)) {
                DarkButtons.draw(ctx, w.getX(), w.getY(), w.getWidth(), w.getHeight(),
                        w.getMessage().getString(), w.active, 0xFFFFFFFF, mouseX, mouseY);
            }
        }
    }

    private void renderPanel(DrawContext ctx) {
        int panelRight = panelLeft + panelWidth;
        int bottom = this.height - 8;
        ctx.fill(panelLeft - 4, PANEL_TOP + 4, panelRight + 4, bottom + 4, 0x66000000);
        ctx.fill(panelLeft - 1, PANEL_TOP - 1, panelRight + 1, bottom + 1, PANEL_BORDER);
        ctx.fill(panelLeft, PANEL_TOP, panelRight, bottom, PANEL_BG);
        ctx.fill(panelLeft, PANEL_TOP, panelRight, PANEL_TOP + 3, PANEL_ACCENT);
        ctx.fill(panelLeft, bodyTop() - 1, panelRight, bodyTop(), 0x663A3D42);

        // Category rail: only while not searching, since search results span every category.
        if (searchQuery.isBlank()) {
            ctx.fill(sidebarLeft, bodyTop(), sidebarLeft + SIDEBAR_W, bodyBottom(), SIDEBAR_BG);
            ctx.fill(sidebarLeft + SIDEBAR_W, bodyTop(), sidebarLeft + SIDEBAR_W + 1, bodyBottom(), 0x663A3D42);
        }

        int descTop = bodyBottom();
        ctx.fill(panelLeft, descTop, panelRight, descTop + 1, 0x663A3D42);
        ctx.fill(panelLeft, descTop + 1, panelRight, descTop + DESC_H, 0x5514161A);
        ctx.fill(panelLeft, descTop + DESC_H, panelRight, descTop + DESC_H + 1, 0x663A3D42);
        ctx.fill(panelLeft, descTop + DESC_H + 1, panelRight, bottom, 0xAA14161A);
    }

    private void drawSidebar(DrawContext ctx, int mouseX, int mouseY) {
        if (!searchQuery.isBlank()) return;
        int y = bodyTop() + 4;
        for (String cat : categories) {
            boolean active = cat.equals(activeCategory);
            boolean hover = mouseX >= sidebarLeft && mouseX < sidebarLeft + SIDEBAR_W
                    && mouseY >= y && mouseY < y + 18;
            if (active) {
                ctx.fill(sidebarLeft, y, sidebarLeft + SIDEBAR_W, y + 18, 0x554FA37A);
                ctx.fill(sidebarLeft, y, sidebarLeft + 3, y + 18, PANEL_ACCENT);
            } else if (hover) {
                ctx.fill(sidebarLeft, y, sidebarLeft + SIDEBAR_W, y + 18, ROW_HOVER);
            }
            String label = this.textRenderer.trimToWidth(cat, SIDEBAR_W - 16);
            ctx.drawText(this.textRenderer, label, sidebarLeft + 9,
                    y + (18 - this.textRenderer.fontHeight) / 2,
                    active ? 0xFFFFFFFF : 0xFFB9BFC7, false);
            y += 18;
        }
    }

    /** Highlights the row under the cursor and remembers its help text for the strip below. */
    private void drawRowHighlight(DrawContext ctx, int mouseX, int mouseY) {
        hoveredDescription = null;
        int top = bodyTop(), bottom = bodyBottom();
        if (mouseX < contentLeft - PANEL_PAD || mouseX > innerRight) return;
        for (Row r : rows) {
            if (!r.visible || r.isSection()) continue;
            int rowY = top + r.contentY - scrollOffset;
            if (rowY < top || rowY + 20 > bottom) continue;
            if (mouseY >= rowY && mouseY < rowY + 20) {
                ctx.fill(contentLeft - 6, rowY, contentRight + 2, rowY + 20, ROW_HOVER);
                ctx.fill(contentLeft - 6, rowY, contentLeft - 4, rowY + 20, PANEL_ACCENT);
                hoveredDescription = DESCRIPTIONS.get(r.label);
                return;
            }
        }
    }

    private void drawDescription(DrawContext ctx) {
        if (hoveredDescription == null) return;
        int descTop = bodyBottom();
        int maxW = innerRight - (panelLeft + PANEL_PAD);
        List<net.minecraft.text.OrderedText> wrapped =
                this.textRenderer.wrapLines(Text.literal(hoveredDescription), maxW);
        int y = descTop + 6;
        for (int i = 0; i < Math.min(2, wrapped.size()); i++) {
            ctx.drawText(this.textRenderer, wrapped.get(i), panelLeft + PANEL_PAD, y, DESC_COLOR, false);
            y += 10;
        }
    }

    private void drawSectionsAndLabels(DrawContext ctx) {
        int top = bodyTop();
        int bottom = bodyBottom();
        for (Row r : rows) {
            if (!r.visible) continue;
            int rowY = top + r.contentY - scrollOffset;
            if (r.isSection()) {
                if (rowY < top || rowY > bottom - 10) continue;
                ctx.fill(contentLeft, rowY + 1, contentLeft + 3, rowY + 10, PANEL_ACCENT);
                ctx.drawText(this.textRenderer, r.label, contentLeft + 8, rowY, LABEL_COLOR, true);
                int lineY = rowY + 12;
                ctx.fill(contentLeft, lineY, innerRight, lineY + 1, 0x553A3D42);
            } else if (!r.fullWidth) {
                if (rowY < top - 2 || rowY > bottom - 12) continue;
                int textY = rowY + (20 - this.textRenderer.fontHeight) / 2;
                String label = this.textRenderer.trimToWidth(r.label, labelMaxW);
                ctx.drawText(this.textRenderer, label, labelX, textY, LABEL_COLOR, false);
            }
        }
    }

    private void drawScrollbar(DrawContext ctx) {
        int maxScroll = maxScroll();
        if (maxScroll <= 0) return;
        int trackTop = bodyTop();
        int trackBottom = bodyBottom();
        int trackHeight = trackBottom - trackTop;
        int thumbHeight = Math.max(24, trackHeight * trackHeight / Math.max(1, contentHeight));
        int thumbY = trackTop + (trackHeight - thumbHeight) * scrollOffset / maxScroll;
        int x = contentRight + 2;
        ctx.fill(x, trackTop + 4, x + 2, trackBottom - 4, 0x663A3D42);
        ctx.fill(x - 1, thumbY, x + 3, thumbY + thumbHeight, 0xFF9CA3AF);
    }

    private void drawScrollFades(DrawContext ctx) {
        int panelRight = panelLeft + panelWidth;
        if (scrollOffset > 0) {
            ctx.fill(panelLeft + 1, bodyTop(), panelRight - 1, bodyTop() + 10, 0xAA0E0F12);
        }
        if (scrollOffset < maxScroll()) {
            ctx.fill(panelLeft + 1, bodyBottom() - 10, panelRight - 1, bodyBottom(), 0xAA0E0F12);
        }
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    private int bodyTop() {
        return BODY_TOP;
    }

    private int bodyBottom() {
        return Math.max(BODY_TOP + 60, this.height - FOOTER_HEIGHT - DESC_H);
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - (bodyBottom() - bodyTop()));
    }

    // ── Value → text for cycling controls ──────────────────────────────────────────

    private static Text borderModeText(Integer mode) {
        return Text.literal(switch (mode) {
            case 1 -> "国家";
            case 2 -> "州、省+国家";
            default -> "关闭";
        });
    }

    /** Auto follows the dimension you are in; the others pin the map to one squaremap world. */
    private static Text mapWorldModeText(Integer mode) {
        return Text.literal(switch (mode) {
            case 1 -> "Terra Nostra";
            case 2 -> "月球";
            default -> "自动";
        });
    }

    private static Text squaremapBackgroundModeText(Integer mode) {
        return Text.literal(switch (mode) {
            case 1 -> "世界地图";
            case 2 -> "小地图";
            case 3 -> "两者";
            default -> "关闭";
        });
    }

    private static Text minimapIndicatorModeText(Integer mode) {
        return Text.literal(switch (mode) {
            case 0 -> "关闭";
            case 1 -> "EMC";
            case 2 -> "Xaero";
            default -> "两者";
        });
    }

    private static Text minimapTownNameModeText(Integer mode) {
        return Text.literal(switch (mode) {
            case 1 -> "附近";
            case 2 -> "主要";
            case 3 -> "全部";
            default -> "关闭";
        });
    }

    private static Text playerHeadModeText(Integer mode) {
        return Text.literal(switch (mode) {
            case 1 -> "世界地图";
            case 2 -> "小地图";
            case 3 -> "两者";
            default -> "关闭";
        });
    }

    private static Text netherModeText(Integer mode) {
        return Text.literal(mode == 2 ? "主世界坐标" : "隐藏");
    }

    private static Text darkenText(Integer level) {
        return Text.literal(switch (level) {
            case 1 -> "浅";
            case 2 -> "中";
            case 3 -> "深";
            default -> "关闭";
        });
    }

    private static Text minimapChunkGridModeText(Integer mode) {
        return Text.literal(switch (mode) {
            case 1 -> "始终";
            case 2 -> "仅放大时";
            default -> "关闭";
        });
    }

    private static String hexColor(int rgb) {
        return String.format("#%06X", rgb & 0x00FFFFFF);
    }

    /** One settings row: a section header (control == null) or an option (label + control + optional reset). */
    private static final class Row {
        final String label;
        final ClickableWidget control;
        final ButtonWidget reset;
        final boolean fullWidth;
        final BooleanSupplier isDefault;
        final String category;
        int contentY;
        boolean visible = true;

        Row(String label, ClickableWidget control, ButtonWidget reset, boolean fullWidth,
            BooleanSupplier isDefault, String category) {
            this.category = category;
            this.label = label;
            this.control = control;
            this.reset = reset;
            this.fullWidth = fullWidth;
            this.isDefault = isDefault;
        }

        boolean isSection() {
            return control == null;
        }
    }

    /** Info-panel scale slider: maps the widget's 0–1 value onto 50–100%. */
    private static final class PanelScaleSlider extends SliderWidget {
        private static final float MIN = 0.7f, MAX = 1.0f;   // below 70% the GUIs get unreadable
        private final TownyMapConfig config;

        private PanelScaleSlider(int x, int y, int width, int height, TownyMapConfig config) {
            super(x, y, width, height, Text.empty(), (config.infoPanelScale - MIN) / (MAX - MIN));
            this.config = config;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(Math.round(config.infoPanelScale * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            config.infoPanelScale = MIN + (float) value * (MAX - MIN);
            config.save();
        }
    }

    /**
     * Hue slider over any ARGB config colour. The status-highlight slider stays separate because it also
     * carries the RGB-cycling flag; this one just moves a single colour's hue and keeps it fully opaque.
     */
    private static final class ColorHueSlider extends SliderWidget {
        private final TownyMapConfig config;
        private final java.util.function.IntSupplier getter;
        private final java.util.function.IntConsumer setter;

        private ColorHueSlider(int x, int y, int width, int height, TownyMapConfig config,
                               java.util.function.IntSupplier getter,
                               java.util.function.IntConsumer setter) {
            super(x, y, width, height, Text.empty(),
                    StatusHighlightHueSlider.hueFromRgb(getter.getAsInt() & 0xFFFFFF));
            this.config = config;
            this.getter = getter;
            this.setter = setter;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(hexColor(getter.getAsInt() & 0xFFFFFF)));
        }

        @Override
        protected void applyValue() {
            setter.accept(0xFF000000 | StatusHighlightHueSlider.hsvToRgb(value, 0.78, 1.0));
            config.save();
        }
    }

    private static final class StatusHighlightHueSlider extends SliderWidget {
        private final TownyMapConfig config;

        private StatusHighlightHueSlider(int x, int y, int width, int height, TownyMapConfig config) {
            super(x, y, width, height, Text.empty(), hueFromRgb(config.statusHighlightColor));
            this.config = config;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(config.statusHighlightRainbow ? "RGB" : hexColor(config.statusHighlightColor)));
        }

        @Override
        protected void applyValue() {
            config.statusHighlightColor = hsvToRgb(value, 0.78, 1.0);
            config.save();
        }

        private static double hueFromRgb(int rgb) {
            double r = ((rgb >> 16) & 0xFF) / 255.0;
            double g = ((rgb >> 8) & 0xFF) / 255.0;
            double b = (rgb & 0xFF) / 255.0;
            double max = Math.max(r, Math.max(g, b));
            double min = Math.min(r, Math.min(g, b));
            double delta = max - min;
            if (delta <= 0.00001) return 0.78;
            double hue;
            if (max == r) {
                hue = ((g - b) / delta) % 6.0;
            } else if (max == g) {
                hue = (b - r) / delta + 2.0;
            } else {
                hue = (r - g) / delta + 4.0;
            }
            hue /= 6.0;
            return hue < 0 ? hue + 1.0 : hue;
        }

        private static int hsvToRgb(double hue, double saturation, double value) {
            double h = (hue - Math.floor(hue)) * 6.0;
            int sector = (int) Math.floor(h);
            double fraction = h - sector;
            double p = value * (1.0 - saturation);
            double q = value * (1.0 - fraction * saturation);
            double t = value * (1.0 - (1.0 - fraction) * saturation);
            double r, g, b;
            switch (sector) {
                case 0 -> { r = value; g = t; b = p; }
                case 1 -> { r = q; g = value; b = p; }
                case 2 -> { r = p; g = value; b = t; }
                case 3 -> { r = p; g = q; b = value; }
                case 4 -> { r = t; g = p; b = value; }
                default -> { r = value; g = p; b = q; }
            }
            return ((int) Math.round(r * 255.0) << 16)
                    | ((int) Math.round(g * 255.0) << 8)
                    | (int) Math.round(b * 255.0);
        }
    }

    /** Slider for border line thickness — range 0.1× to 3.0×, snaps to 0.05 steps. */
}
