package net.townymap.gui;

import net.townymap.gui.DetailScreen.Chips;
import net.townymap.gui.DetailScreen.Col;
import net.townymap.gui.DetailScreen.Cols;
import net.townymap.gui.DetailScreen.Kind;
import net.townymap.gui.DetailScreen.LineList;
import net.townymap.gui.DetailScreen.NameList;
import net.townymap.gui.DetailScreen.Page;
import net.townymap.gui.DetailScreen.Ref;
import net.townymap.gui.DetailScreen.Rule;
import net.townymap.api.ArchiveClient;
import net.townymap.model.EarthMcNationData;
import net.townymap.model.NationFullData;
import net.townymap.model.PlayerFullData;
import net.townymap.model.TownData;
import net.townymap.model.TownFullData;
import net.townymap.TownyMapMod;
import net.townymap.util.DiscordUrl;

import java.util.Locale;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Builds the block list for each kind of expanded panel. Layout lives in {@link DetailScreen}. */
public final class DetailPages {

    private DetailPages() {}

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    private static String date(long ms) {
        return ms > 0 ? DATE.format(Instant.ofEpochMilli(ms)) : "—";
    }

    /** "3 months ago" / "4 minutes ago" — the relative form the Discord embeds use. */
    private static String ago(long ms) {
        if (ms <= 0) return "—";
        Duration d = Duration.between(Instant.ofEpochMilli(ms), Instant.now());
        long days = d.toDays();
        if (days >= 365) { long y = days / 365; return y + (y == 1 ? " 年前" : " 年前"); }
        if (days >= 30)  { long m = days / 30;  return m + (m == 1 ? " 个月前" : " 个月前"); }
        if (days >= 1)   return days + (days == 1 ? " 天前" : " 天前");
        long h = d.toHours();
        if (h >= 1) return h + (h == 1 ? " 小时前" : " 小时前");
        long m = Math.max(1, d.toMinutes());
        return m + (m == 1 ? " 分钟前" : " 分钟前");
    }

    /** Rough human duration for a pact term. */
    private static String humanDuration(long ms) {
        long days = Duration.ofMillis(ms).toDays();
        if (days >= 365) { long y = days / 365; return y + (y == 1 ? " 年" : " 年"); }
        if (days >= 30)  { long m = days / 30;  return m + (m == 1 ? " 个月" : " 个月"); }
        if (days >= 1)   return days + (days == 1 ? " 天" : " 天");
        long h = Math.max(1, Duration.ofMillis(ms).toHours());
        return h + (h == 1 ? " 小时" : " 小时");
    }

    private static String money(double v) {
        return (v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.2f", v)) + "g";
    }

    private static List<Ref> refs(List<String> names, Kind kind) {
        List<Ref> out = new ArrayList<>(names.size());
        for (String n : names) out.add(new Ref(kind, n));
        return out;
    }

    private static void addNames(List<DetailScreen.Block> blocks, String title, List<String> names, Kind kind) {
        if (names != null && !names.isEmpty()) blocks.add(new NameList(title, refs(names, kind)));
    }

    /**
     * Adds "Meganation" / "Alliances" rows for a nation, if it belongs to any (from the alliance roster).
     * Each bloc is a link through to its own panel, so a nation's memberships are explorable rather than
     * being a dead end of text.
     */
    private static void addAllianceBlocks(List<DetailScreen.Block> b, String nation) {
        if (nation == null || nation.isBlank()) return;
        addBlocRefs(b, TownyMapMod.meganationsForNation(nation), "大联盟", "大联盟");
        addBlocRefs(b, TownyMapMod.alliancesForNation(nation), "联盟", "联盟");
    }

    private static void addBlocRefs(List<DetailScreen.Block> b, List<String> names,
                                    String singular, String plural) {
        if (names.isEmpty()) return;
        List<DetailScreen.RefLine> lines = new ArrayList<>(names.size());
        for (String name : names) {
            net.townymap.api.AllianceClient.Alliance a = TownyMapMod.allianceByName(name);
            String suffix = a == null ? "" : "  " + a.nations().size() + " 个国家";
            lines.add(new DetailScreen.RefLine(new Ref(Kind.ALLIANCE, name), suffix));
        }
        b.add(new DetailScreen.RefLineList(names.size() > 1 ? plural : singular, lines));
    }

    // ── Alliance / meganation ─────────────────────────────────────────────────

    /**
     * The Expand panel for an alliance or meganation. Everything here comes from data already in memory —
     * the BreakTheBot roster plus the town list the map is drawing — so opening it costs no requests.
     */
    public static Page alliance(net.townymap.api.AllianceClient.Alliance a) {
        List<DetailScreen.Block> b = new ArrayList<>();
        List<String> nations = a.nations();

        // Roll the bloc's totals up from the towns already on the map, so they're complete even when the
        // per-nation detail fetches haven't caught up.
        int towns = 0;
        int chunks = 0;
        for (TownData t : TownyMapMod.currentTowns()) {
            String n = TownyMapMod.townNationOf(t.key());
            if (n == null || !a.nationsLower().contains(n.toLowerCase(Locale.ROOT))) continue;
            towns++;
            chunks += t.approximateChunks();
        }
        // Residents only exist in the nation records, which warm in the background; sum what has arrived.
        int residents = 0;
        boolean partial = false;
        for (String n : nations) {
            EarthMcNationData nd = TownyMapMod.nationDetails(n);
            if (nd == null) { partial = true; continue; }
            residents += nd.residentCount();
        }

        b.add(new Cols(List.of(
                new Col("类型", a.mega() ? "大联盟" : "联盟", null),
                new Col("标签", orDash(a.identifier()), null),
                new Col("国家", String.valueOf(nations.size()), null))));
        b.add(new Cols(List.of(
                new Col("城镇", towns > 0 ? String.valueOf(towns) : "—", null),
                new Col("区块", chunks > 0 ? String.format("%,d", chunks) : "—", null),
                new Col("居民", residents > 0 ? String.format("%,d", residents)
                        + (partial ? "+" : "") : "—", null))));
        b.add(new DetailScreen.Rule());

        List<DetailScreen.RefLine> lines = new ArrayList<>(nations.size());
        for (String n : nations) {
            EarthMcNationData nd = TownyMapMod.nationDetails(n);
            String suffix = nd == null ? "" : "  " + nd.townCount() + " 个城镇";
            lines.add(new DetailScreen.RefLine(new Ref(Kind.NATION, n), suffix));
        }
        b.add(new DetailScreen.RefLineList("成员", lines));

        return new Page(Kind.ALLIANCE, a.label() == null || a.label().isBlank() ? a.identifier() : a.label(),
                (a.mega() ? "大联盟" : "联盟") + " · " + nations.size() + " 个国家",
                b, "", "");
    }

    // ── Town ──────────────────────────────────────────────────────────────────

    /**
     * Leaderboards built entirely from the claim data already in memory -- no EarthMC API calls, so this
     * opens instantly, works while the API is down, and reflects an archive snapshot when one is loaded
     * (getTowns() returns the archive list in that case, which is the behaviour these views want).
     *
     * <p>Every entry goes through addNames, so the names stay clickable and lead to the normal town or
     * nation page. That is the whole point of putting stats in this panel rather than a separate screen.
     */
    /** The info panel's landing tab: a short overview, with the detail left to the Statistics tab. */
    public static Page dashboard() {
        List<DetailScreen.Block> b = new ArrayList<>();
        net.townymap.api.SquaremapApiClient api = TownyMapMod.getApiClient();
        List<net.townymap.model.TownData> towns = api == null ? List.of() : api.getTowns();

        int residents = 0;
        Map<String, Integer> nations = new java.util.HashMap<>();
        for (net.townymap.model.TownData t : towns) {
            residents += api.getTownResidents(t.key());
            String n = api.getTownNation(t.key());
            if (n != null && !n.isBlank()) nations.merge(n, 1, Integer::sum);
        }

        // Your own town and nation first -- the one thing here nobody else's dashboard would show.
        net.townymap.model.EarthMcPlayerData self = TownyMapMod.selfPlayer();
        if (self != null && self.townName() != null && !self.townName().isBlank()) {
            String myTown = self.townName();
            net.townymap.model.TownData mine = null;
            for (net.townymap.model.TownData t : towns) {
                if (t.name().equalsIgnoreCase(myTown)) { mine = t; break; }
            }
            b.add(new Cols(List.of(
                    new Col("你的城镇", myTown, new Ref(Kind.TOWN, myTown)),
                    new Col("国家", self.nationName() == null || self.nationName().isBlank()
                            ? "-" : self.nationName(),
                            self.nationName() == null || self.nationName().isBlank()
                                    ? null : new Ref(Kind.NATION, self.nationName())),
                    new Col("你的余额", money(self.balance())))));
            net.townymap.model.TownFullData full = TownyMapMod.selfTownFull();
            if (full != null) {
                // Identical to the right-click town page (DetailPages.town): EarthMC's own claimed/max,
                // which already accounts for the nation bonus and any server-side overrides. Deriving it
                // here from residents x 12 gave a different number to the rest of the mod.
                b.add(new Cols(List.of(
                        new Col("居民", String.valueOf(full.residents() == null ? 0 : full.residents().size())),
                        new Col("区块", full.numTownBlocks() + " / "
                                + (full.maxTownBlocks() >= 0 ? full.maxTownBlocks() : "?")),
                        new Col("还可占领", full.maxTownBlocks() >= 0
                                ? String.valueOf(Math.max(0, full.maxTownBlocks() - full.numTownBlocks()))
                                : "?"))));
            }
            b.add(new Rule());
        }

        b.add(new Cols(List.of(
                new Col("城镇", String.valueOf(towns.size())),
                new Col("国家", String.valueOf(nations.size())),
                new Col("居民", String.valueOf(residents)))));
        // Live so the age keeps counting up while the panel stays open.
        b.add(new Cols(List.of(Col.live("领地数据", () -> TownyMapMod.mapDataStatus().text()))));
        b.add(new Rule());

        // favoriteTownKeys() holds lower-cased keys; resolve them back to the town's real casing so the
        // list reads like the rest of the panel. Falls back to the key if the town is not loaded.
        java.util.Set<String> favKeys = TownyMapMod.favoriteTownKeys();
        List<String> favourites = new ArrayList<>();
        if (!favKeys.isEmpty()) {
            Map<String, String> byKey = new java.util.HashMap<>();
            for (net.townymap.model.TownData t : towns) byKey.put(t.key(), t.name());
            for (String k : favKeys) favourites.add(byKey.getOrDefault(k, k));
            favourites.sort(String.CASE_INSENSITIVE_ORDER);
        }
        addNames(b, "收藏", favourites, Kind.TOWN);

        String subtitle = api != null && api.isArchiveActive()
                ? "来自已加载的存档快照" : "在线地图 实时数据";
        return new Page(Kind.STATS, "信息面板", subtitle, b, null, null);
    }

    /** Chunks a town claims: shoelace area of its rings (first = outer, rest = holes) over 16x16. */
    private static int chunkCount(net.townymap.model.TownData t) {
        double area = 0;
        List<int[][]> rings = t.polygonRings();
        for (int r = 0; r < rings.size(); r++) {
            int[][] ring = rings.get(r);
            double a = 0;
            for (int i = 0, n = ring.length; i < n; i++) {
                int[] p1 = ring[i], p2 = ring[(i + 1) % n];
                if (p1.length < 2 || p2.length < 2) continue;
                a += (double) p1[0] * p2[1] - (double) p2[0] * p1[1];
            }
            a = Math.abs(a) / 2.0;
            area += (r == 0) ? a : -a;   // rings after the first are unclaimed pockets
        }
        return (int) Math.round(Math.max(0, area) / 256.0);
    }

    public static Page stats() { return stats(0, 0); }
    public static Page stats(int sub) { return stats(sub, 0); }

    /** Filter labels per sub-tab, mirrored by DetailScreen so the strip and the data agree. */
    public static String[] filtersFor(int sub) {
        return switch (sub) {
            case 1 -> new String[]{ "城镇", "居民", "区块", "Gold", "放逐", "建立" };
            case 2 -> new String[]{ "Gold", "加入时间", "好友", "放逐", "信任" };
            default -> new String[]{ "居民", "区块", "Gold", "放逐", "建立" };
        };
    }

    /** How deep the ranked lists go. The panel scrolls, so this is about usefulness, not fitting. */
    private static final int RANK_DEPTH = 100;

    /**
     * A ranked list: one row per entry, "12. Name" with the value on the right, name clickable.
     * RefLineList renders a flat list rather than the collapsible dropdown addNames produces, which is
     * what makes this read as a leaderboard instead of a folded-up roster.
     */
    private static void rankList(List<DetailScreen.Block> blocks, String title,
                                 List<String[]> rows, String unit, Kind kind) {
        List<DetailScreen.RefLine> lines = new ArrayList<>();
        for (int i = 0; i < rows.size() && i < RANK_DEPTH; i++) {
            String[] row = rows.get(i);
            // Rank is drawn by the renderer from the row index; the suffix carries only the value, so
            // the numbers right-align into a readable column.
            lines.add(new DetailScreen.RefLine(new Ref(kind, row[0]),
                    row[1] + (unit.isEmpty() || row[1].isEmpty() ? "" : " " + unit)));
        }
        if (!lines.isEmpty()) blocks.add(new DetailScreen.RefLineList(title, lines, true));
    }

    /** Map to descending {name, value} rows. */
    private static List<String[]> rowsOf(Map<String, Integer> counts) {
        List<Map.Entry<String, Integer>> e = new ArrayList<>(counts.entrySet());
        e.sort((x, y) -> y.getValue() - x.getValue());
        List<String[]> out = new ArrayList<>();
        for (Map.Entry<String, Integer> en : e) out.add(new String[]{en.getKey(), String.valueOf(en.getValue())});
        return out;
    }

    /** sub: 0 = towns, 1 = nations, 2 = players. filter indexes filtersFor(sub). */
    public static Page stats(int sub, int filter) {
        List<DetailScreen.Block> b = new ArrayList<>();
        net.townymap.api.SquaremapApiClient api = TownyMapMod.getApiClient();
        List<net.townymap.model.TownData> towns = api == null ? List.of() : api.getTowns();
        // getTowns() only ever holds the world being shown, so off Earth these leaderboards silently rank
        // outposts alone. Say so rather than passing Moon-only totals off as server-wide ones.
        if (!TownyMapMod.viewingEarth()) {
            b.add(new DetailScreen.Wide("范围", TownyMapMod.activeWorldName()
                    + " 仅限 - 只有前哨站领地，不含整个城镇"));
        }

        if (sub == 1) {
            Map<String, Integer> townCount = new java.util.HashMap<>();
            Map<String, Integer> residents = new java.util.HashMap<>();
            Map<String, Integer> chunks = new java.util.HashMap<>();
            for (net.townymap.model.TownData t : towns) {
                String n = api.getTownNation(t.key());
                if (n == null || n.isBlank()) continue;
                townCount.merge(n, 1, Integer::sum);
                residents.merge(n, api.getTownResidents(t.key()), Integer::sum);
                chunks.merge(n, chunkCount(t), Integer::sum);
            }
            b.add(new Cols(List.of(new Col("国家", String.valueOf(townCount.size())))));
            b.add(new Rule());
            if (filter == 5) {
                // Oldest first. The index carries a formatted date string only, so sorting on it would
                // order alphabetically; foundedMs is the raw registration time the parser now keeps.
                List<net.townymap.model.EarthMcNationData> idx =
                        new ArrayList<>(TownyMapMod.nationStats().values());
                idx.removeIf(nd -> nd.foundedMs() <= 0);
                if (idx.isEmpty()) {
                    b.add(new Cols(List.of(new Col("国家", "加载中..."))));
                    return new Page(Kind.STATS, "信息面板", "正在获取国家索引", b, null, null);
                }
                idx.sort((x, y) -> Long.compare(x.foundedMs(), y.foundedMs()));
                List<String[]> rows = new ArrayList<>();
                for (var nd : idx) {
                    if (rows.size() >= RANK_DEPTH) break;
                    rows.add(new String[]{nd.name(), date(nd.foundedMs())});
                }
                b.add(new Cols(List.of(new Col("国家", String.valueOf(idx.size())))));
                rankList(b, "最古老的国家", rows, "", Kind.NATION);
                return new Page(Kind.STATS, "信息面板", "按建立时间排序", b, null, null);
            }
            if (filter >= 3) {
                // Gold and outlaw counts come from the nation index -- one request for every nation,
                // already cached for the search bar, and archive-aware like everything else here.
                List<net.townymap.model.EarthMcNationData> idx =
                        new ArrayList<>(TownyMapMod.nationStats().values());
                if (idx.isEmpty()) {
                    b.add(new Cols(List.of(new Col("国家", "加载中..."))));
                    return new Page(Kind.STATS, "信息面板", "正在获取国家索引", b, null, null);
                }
                boolean gold = filter == 3;
                List<net.townymap.model.EarthMcNationData> sorted = new ArrayList<>(idx);
                sorted.sort(gold ? (x, y) -> Double.compare(y.balance(), x.balance())
                                 : (x, y) -> Integer.compare(y.outlawCount(), x.outlawCount()));
                List<String[]> rows = new ArrayList<>();
                for (net.townymap.model.EarthMcNationData nd : sorted) {
                    if (rows.size() >= RANK_DEPTH) break;
                    rows.add(new String[]{nd.name(),
                            gold ? money(nd.balance()) : String.valueOf(nd.outlawCount())});
                }
                b.add(new Cols(List.of(new Col("国家", String.valueOf(idx.size())))));
                rankList(b, gold ? "按Gold排名的国家" : "按放逐者数量排名的国家",
                        rows, gold ? "" : "放逐", Kind.NATION);
                return new Page(Kind.STATS, "信息面板", idx.size() + " 个国家", b, null, null);
            }
            Map<String, Integer> src = filter == 1 ? residents : filter == 2 ? chunks : townCount;
            String unit = filter == 1 ? "居民" : filter == 2 ? "区块" : "城镇";
            rankList(b, "国家排名：" + unit, rowsOf(src), unit, Kind.NATION);
            return new Page(Kind.STATS, "信息面板", townCount.size() + " 个国家", b, null, null);
        }

        if (sub == 2) {
            if (filter >= 3) {
                boolean outlawed = filter == 3;
                Map<String, int[]> counts = TownyMapMod.outlawTrustedCounts();
                if (counts.isEmpty()) {
                    b.add(new Cols(List.of(new Col("玩家", "正在扫描城镇..."))));
                    return new Page(Kind.STATS, "信息面板",
                            "正在读取每个城镇的居民名单，请稍候", b, null, null);
                }
                List<String[]> rows = new ArrayList<>();
                List<Map.Entry<String, int[]>> es = new ArrayList<>(counts.entrySet());
                int slot = outlawed ? 0 : 1;
                es.sort((x, y) -> y.getValue()[slot] - x.getValue()[slot]);
                for (Map.Entry<String, int[]> e : es) {
                    if (rows.size() >= RANK_DEPTH) break;
                    if (e.getValue()[slot] <= 0) break;
                    rows.add(new String[]{e.getKey(), String.valueOf(e.getValue()[slot])});
                }
                b.add(new Cols(List.of(new Col("玩家", String.valueOf(counts.size())))));
                rankList(b, outlawed ? "被最多城镇放逐" : "在最多城镇被信任",
                        rows, "城镇", Kind.PLAYER);
                return new Page(Kind.STATS, "信息面板",
                        counts.size() + " 位玩家（来自城镇名单）", b, null, null);
            }
            java.util.Map<String, net.townymap.model.EarthMcPlayerData> stats =
                    TownyMapMod.allPlayerStats();
            if (stats.isEmpty()) {
                b.add(new Cols(List.of(new Col("玩家", "加载中..."))));
                return new Page(Kind.STATS, "信息面板", "正在获取在线玩家数据", b, null, null);
            }
            List<net.townymap.model.EarthMcPlayerData> sorted = new ArrayList<>(stats.values());
            if (filter == 1) sorted.sort((x, y) -> Long.compare(x.registeredMs(), y.registeredMs()));
            else if (filter == 2) sorted.sort((x, y) -> Integer.compare(y.friendCount(), x.friendCount()));
            else sorted.sort((x, y) -> Double.compare(y.balance(), x.balance()));

            List<String[]> rows = new ArrayList<>();
            for (net.townymap.model.EarthMcPlayerData pd : sorted) {
                if (rows.size() >= RANK_DEPTH) break;
                if (pd.name() == null || pd.name().isBlank() || pd.npc()) continue;
                // Drop players outside EarthMC's 42-day activity window from the value rankings: they
                // clutter the top of the gold list with balances nobody can move. Join date is a
                // historical fact, so that list keeps everyone.
                if (filter != 1 && !TownyMapMod.isRecentlyActive(pd)) continue;
                rows.add(new String[]{pd.name(), filter == 1 ? date(pd.registeredMs())
                        : filter == 2 ? String.valueOf(pd.friendCount())
                        : money(pd.balance())});
            }
            boolean full = TownyMapMod.playerSweepComplete();
            b.add(new Cols(List.of(new Col("玩家", String.valueOf(stats.size())))));
            rankList(b, "玩家排名：" + (filter == 1 ? "加入日期" : filter == 2 ? "好友" : "Gold"),
                    rows, filter == 2 ? "好友" : "", Kind.PLAYER);
            return new Page(Kind.STATS, "信息面板",
                    full ? stats.size() + " 位玩家" : "已获取部分在线玩家，正在完整扫描",
                    b, null, null);
        }

        int totalResidents = 0, totalChunks = 0;
        List<String[]> byResidents = new ArrayList<>();
        List<String[]> byChunks = new ArrayList<>();
        for (net.townymap.model.TownData t : towns) {
            int res = api.getTownResidents(t.key());
            int ch = chunkCount(t);
            totalResidents += res;
            totalChunks += ch;
            byResidents.add(new String[]{t.name(), String.valueOf(res)});
            byChunks.add(new String[]{t.name(), String.valueOf(ch)});
        }
        b.add(new Cols(List.of(
                new Col("城镇", String.valueOf(towns.size())),
                new Col("居民", String.valueOf(totalResidents)),
                new Col("区块", String.valueOf(totalChunks)))));
        b.add(new Rule());
        byResidents.sort((x, y) -> Integer.parseInt(y[1]) - Integer.parseInt(x[1]));
        byChunks.sort((x, y) -> Integer.parseInt(y[1]) - Integer.parseInt(x[1]));
        if (filter >= 2) {
            // These three are the only leaderboards that need the town sweep, so it is triggered here
            // rather than warmed -- open one and it starts; never open them and it never runs.
            var ranks = TownyMapMod.townRanks();
            if (ranks.isEmpty()) {
                b.add(new Cols(List.of(new Col("城镇", "正在扫描城镇..."))));
                return new Page(Kind.STATS, "信息面板",
                        "正在读取所有城镇，请稍候", b, null, null);
            }
            List<net.townymap.api.EarthMcApiClient.TownRank> list = new ArrayList<>(ranks.values());
            String title, unit;
            if (filter == 2) {
                list.sort((x, y) -> Double.compare(y.balance(), x.balance()));
                title = "最富有的城镇"; unit = "";
            } else if (filter == 3) {
                list.sort((x, y) -> Integer.compare(y.outlaws(), x.outlaws()));
                title = "按放逐数量排名的城镇"; unit = "放逐";
            } else {
                list.removeIf(r -> r.foundedMs() <= 0);
                list.sort((x, y) -> Long.compare(x.foundedMs(), y.foundedMs()));
                title = "最古老的城镇"; unit = "";
            }
            List<String[]> rows = new ArrayList<>();
            for (var r : list) {
                if (rows.size() >= RANK_DEPTH) break;
                if (filter == 3 && r.outlaws() <= 0) break;
                rows.add(new String[]{r.name(), filter == 2 ? money(r.balance())
                        : filter == 3 ? String.valueOf(r.outlaws())
                        : date(r.foundedMs())});
            }
            b.add(new Cols(List.of(new Col("城镇", String.valueOf(ranks.size())))));
            rankList(b, title, rows, unit, Kind.TOWN);
            return new Page(Kind.STATS, "信息面板", ranks.size() + " 个城镇", b, null, null);
        }

        rankList(b, filter == 1 ? "按区块数排名的城镇" : "按居民数排名的城镇",
                filter == 1 ? byChunks : byResidents,
                filter == 1 ? "区块" : "居民", Kind.TOWN);

        String subtitle = api != null && api.isArchiveActive()
                ? "来自已加载的存档快照" : towns.size() + " 个城镇";
        return new Page(Kind.STATS, "信息面板", subtitle, b, null, null);
    }

    /** Top n rows of {name, value}, labelled "name - value" so the ranking is readable in the list. */
    private static List<String> top(List<String[]> rows, int n) {
        List<String> out = new ArrayList<>();
        for (String[] row : rows) {
            if (out.size() >= n) break;
            out.add(row[0]);
        }
        return out;
    }

    private static List<String> topOfMap(Map<String, Integer> counts, int n) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((x, y) -> y.getValue() - x.getValue());
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : entries) {
            if (out.size() >= n) break;
            out.add(e.getKey());
        }
        return out;
    }

    public static Page town(TownFullData t) {
        List<DetailScreen.Block> b = new ArrayList<>();

        b.add(new Cols(List.of(
                new Col("市长", t.mayor(), new Ref(Kind.PLAYER, t.mayor())),
                new Col("建立", date(t.registeredMs())),
                new Col("创建者", t.founder().isBlank() ? "—" : t.founder(),
                        t.founder().isBlank() ? null : new Ref(Kind.PLAYER, t.founder())))));

        if (t.hasNation() && !t.nation().isBlank()) {
            b.add(new Cols(List.of(
                    new Col("国家", t.nation(), new Ref(Kind.NATION, t.nation())),
                    new Col("加入国家", ago(t.joinedNationAtMs())),
                    new Col("出生点", t.spawnX() + ", " + t.spawnY() + ", " + t.spawnZ()))));
        } else {
            b.add(new Cols(List.of(
                    new Col("国家", "—"),
                    new Col("出生点", t.spawnX() + ", " + t.spawnY() + ", " + t.spawnZ()))));
        }
        addAllianceBlocks(b, t.hasNation() ? t.nation() : "");

        if (!t.board().isBlank()) b.add(new DetailScreen.Wide("公告板", t.board()));
        b.add(new Rule());

        String size = t.numTownBlocks() + " / " + (t.maxTownBlocks() >= 0 ? t.maxTownBlocks() : "?")
                + (t.bonusBlocks() > 0 ? "  (+" + t.bonusBlocks() + ")" : "");
        b.add(new Cols(List.of(
                new Col("规模", size),
                new Col("银行", money(t.balance())),
                new Col("国家加成", String.valueOf(t.nationBonus())))));
        // No residents/trusted/outlawed counts here: the collapsible lists below already show them on
        // the right, and repeating them at the top just spent a row saying the same thing twice.
        if (t.isForSale() && t.forSalePrice() >= 0) {
            b.add(new Cols(List.of(new Col("出售中", money(t.forSalePrice())))));
        }
        b.add(overclaimCols(t));
        b.add(new Rule());

        b.add(chips(
                "公开", t.isPublic(), "开放", t.isOpen(), "中立", t.isNeutral(),
                "首都", t.isCapital(), "已超占", t.isOverClaimed(), "已废墟", t.isRuined(),
                "出售中", t.isForSale(), "外来者传送", t.canOutsidersSpawn(),
                "PVP", t.pvp(), "爆炸", t.explosion(), "火焰蔓延", t.fire(), "生物生成", t.mobs(),
                "被动生物", t.canPassiveMobsSpawn(), "积雪", t.hasSnowAccumulation(),
                "友伤", t.hasFriendlyFire()));

        addNames(b, "居民", t.residents(), Kind.PLAYER);
        addNames(b, "信任", t.trusted(), Kind.PLAYER);
        addNames(b, "放逐", t.outlaws(), Kind.PLAYER);
        if (!t.quarters().isEmpty()) b.add(new LineList("分区", t.quarters()));
        if (!t.warps().isEmpty()) {
            List<String> w = new ArrayList<>();
            for (TownFullData.Warp warp : t.warps()) {
                w.add(warp.name() + " — " + warp.access() + " — " + warp.x() + ", " + warp.y() + ", " + warp.z()
                        + (warp.createdBy().isBlank() ? "" : " (by " + warp.createdBy() + ")"));
            }
            b.add(new LineList("传送点", w));
        }
        addRanks(b, t.occupiedRanks());

        String sub = t.hasNation() && !t.nation().isBlank()
                ? (t.isCapital() ? "首都：" + t.nation() : t.nation()) : "无国家";
        return new Page(Kind.TOWN, t.name(), sub, b, DiscordUrl.normalize(t.discord()), t.wiki());
    }

    /** The Expand panel for an ARCHIVED town — built only from what the Wayback snapshot recorded. Fields the
     *  archive doesn't have (chunks, bank, spawn, open/for-sale, discord, wiki…) are simply not shown. */
    public static Page archiveTown(ArchiveClient.ArchiveTown t) {
        List<DetailScreen.Block> b = new ArrayList<>();

        b.add(new Cols(List.of(
                new Col("市长", orDash(t.mayor()), t.mayor() == null || t.mayor().isBlank() ? null : new Ref(Kind.PLAYER, t.mayor())),
                new Col("建立", orDash(t.founded())),
                new Col("区块", String.valueOf(t.chunks())))));

        if (t.nation() != null && !t.nation().isBlank()) {
            // No alliance/meganation rows here: that data is only available live, not for the archived date.
            b.add(new Cols(List.of(new Col("国家", t.nation(), new Ref(Kind.NATION, t.nation())))));
        }
        if (t.board() != null && !t.board().isBlank()) b.add(new DetailScreen.Wide("公告板", t.board()));
        b.add(new Rule());

        b.add(chips("公开", t.isPublic(), "PVP", t.pvp()));
        addNames(b, "居民", t.residents(), Kind.PLAYER);
        addNames(b, "议员", t.councillors(), Kind.PLAYER);

        String sub = (t.nation() == null || t.nation().isBlank()) ? "无国家"
                : (t.capital() ? "首都：" + t.nation() : t.nation());
        return new Page(Kind.TOWN, t.name(), sub, b, "", "");
    }

    private static String orDash(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    /**
     * EarthMC's formattedName is MiniMessage (e.g. "&lt;dark_blue&gt; Maire Name"). Builds a styled MC
     * {@link net.minecraft.text.Text} from it — named colours/formats via {@link net.minecraft.util.Formatting},
     * and &lt;#rrggbb&gt; hex via {@link net.minecraft.text.TextColor} — so it renders in its true colour.
     */
    static net.minecraft.text.Text miniToText(String s) {
        net.minecraft.text.MutableText root = net.minecraft.text.Text.empty();
        if (s == null || s.isEmpty()) return root;
        net.minecraft.text.Style style = net.minecraft.text.Style.EMPTY;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("<(/?)(#[0-9a-fA-F]{6}|[a-zA-Z_]+)(:[^>]*)?>").matcher(s);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) root.append(net.minecraft.text.Text.literal(s.substring(last, m.start())).setStyle(style));
            last = m.end();
            if (!m.group(1).isEmpty()) { style = net.minecraft.text.Style.EMPTY; continue; }   // closing → reset
            String tag = m.group(2).toLowerCase(java.util.Locale.ROOT);
            if (tag.startsWith("#") && tag.length() == 7) {
                try {
                    style = style.withColor(net.minecraft.text.TextColor.fromRgb(Integer.parseInt(tag.substring(1), 16)));
                } catch (NumberFormatException ignored) { /* keep style */ }
            } else if (tag.equals("reset")) {
                style = net.minecraft.text.Style.EMPTY;
            } else {
                net.minecraft.util.Formatting f = net.minecraft.util.Formatting.byName(tag);
                if (f != null) style = f.isColor() ? style.withColor(f) : style.withFormatting(f);   // unknown → dropped
            }
        }
        if (last < s.length()) root.append(net.minecraft.text.Text.literal(s.substring(last)).setStyle(style));
        return root;
    }

    /** The Expand panel for a player in ARCHIVE mode — only their residency that date (town, its nation, and
     *  their rank in it). No live data (last online, balance, registration…) exists in the snapshot. */
    public static Page archivePlayer(String name, String town, String nation, String role) {
        List<DetailScreen.Block> b = new ArrayList<>();
        List<Col> cols = new ArrayList<>();
        cols.add(new Col("城镇", orDash(town), town == null || town.isBlank() ? null : new Ref(Kind.TOWN, town)));
        if (nation != null && !nation.isBlank()) cols.add(new Col("国家", nation, new Ref(Kind.NATION, nation)));
        cols.add(new Col("职位", orDash(role)));
        b.add(new Cols(cols));
        String sub = town == null || town.isBlank() ? "" : role.toLowerCase(java.util.Locale.ROOT) + " · " + town;
        return new Page(Kind.PLAYER, name, sub, b, "", "");
    }

    /** The Expand panel for an ARCHIVED nation — derived entirely from that date's member towns: the towns
     *  themselves, their residents and their chunk totals. Nation-level data the snapshot can't give (king,
     *  founded, bank, bonus, over-claim, allies…) is omitted. */
    public static Page archiveNation(String name, String capital, List<String> towns,
                                     List<String> residents, int chunks) {
        List<DetailScreen.Block> b = new ArrayList<>();
        b.add(new Cols(List.of(
                new Col("首都", orDash(capital), capital == null || capital.isBlank() ? null : new Ref(Kind.TOWN, capital)),
                new Col("城镇", String.valueOf(towns.size())),
                new Col("居民", String.valueOf(residents.size())))));
        b.add(new Cols(List.of(new Col("区块", String.valueOf(chunks)))));
        b.add(new Rule());
        addNames(b, "城镇", towns, Kind.TOWN);
        addNames(b, "居民", residents, Kind.PLAYER);
        return new Page(Kind.NATION, name, towns.size() + " 个城镇", b, "", "");
    }

    /**
     * Overclaim standing. A town is overclaimable once it holds more chunks than it can support, and that
     * limit already includes the nation bonus — so a bonus drop can tip a currently-safe town over without
     * it claiming anything. When that is what will happen, show the date it happens rather than a bare
     * "no", since the date is the part worth acting on.
     */
    private static Cols overclaimCols(TownFullData t) {
        return new Cols(List.of(Col.live("可被OC（超占）", () -> overclaimText(t))));
    }

    private static String overclaimText(TownFullData t) {
        if (t.maxTownBlocks() < 0) return "未知";
        if (t.numTownBlocks() > t.maxTownBlocks()) return "是，现在";

        long now = System.currentTimeMillis();
        long soonest = Long.MAX_VALUE;
        String date = "";

        // Resident purges are the dominant term: each one costs a resident's worth of chunks, whereas the
        // nation bonus is a single smaller step. A town can sit far inside its limit today and still be
        // overclaimable in a few weeks purely because inactive residents drop off.
        var byResidents = TownyMapMod.townOverclaimProjection(t.name());
        if (byResidents != null && byResidents.known()) {
            soonest = byResidents.atMs();
            date = byResidents.date();
        }

        // The nation bonus falling can get there first for a town near its limit.
        if (t.hasNation()) {
            var bonus = TownyMapMod.nationBonusProjection(t.nation());
            if (bonus != null && bonus.nextBonus() < t.nationBonus() && bonus.dropAtMs() > 0) {
                int afterMax = t.maxTownBlocks() - (t.nationBonus() - bonus.nextBonus());
                if (t.numTownBlocks() > afterMax && bonus.dropAtMs() < soonest) {
                    soonest = bonus.dropAtMs();
                    date = bonus.dropDate();
                }
            }
        }

        if (soonest == Long.MAX_VALUE) return "否";
        int d = (int) Math.max(0, Math.ceil((soonest - now) / 86_400_000.0));
        String when = d > 1 ? d + " 天" : d == 1 ? "1 天" : "今天";
        return (date == null || date.isBlank() ? "" : date + ", ") + when;
    }

    // ── Player ────────────────────────────────────────────────────────────────

    /**
     * A player page built from town rosters, for players who have opted out of the EarthMC API.
     *
     * <p>Rosters are town data and carry no opt-out, so this is all we can honestly show: their town,
     * and the nation through it. Labelled as map-derived rather than presented as a full profile.
     */
    public static Page playerFromRoster(String name, String town, String nation) {
        List<DetailScreen.Block> b = new ArrayList<>();
        b.add(new Cols(List.of(
                new Col("城镇", town == null || town.isBlank() ? "-" : town,
                        town == null || town.isBlank() ? null : new Ref(Kind.TOWN, town)),
                new Col("国家", nation == null || nation.isBlank() ? "-" : nation,
                        nation == null || nation.isBlank() ? null : new Ref(Kind.NATION, nation)))));
        b.add(new Rule());
        b.add(new DetailScreen.Wide("说明",
                "该玩家未接入 EarthMC API，因此这里只能显示其城镇公开居民名单中的信息。"
                + "无法读取余额、加入日期和职位。"));
        return new Page(Kind.PLAYER, name, "来自地图数据", b, null, null);
    }

    public static Page player(PlayerFullData p) {
        List<DetailScreen.Block> b = new ArrayList<>();

        b.add(new Cols(List.of(
                new Col("城镇", p.hasTown() && !p.town().isBlank() ? p.town() : "—",
                        p.hasTown() && !p.town().isBlank() ? new Ref(Kind.TOWN, p.town()) : null),
                new Col("国家", p.hasNation() && !p.nation().isBlank() ? p.nation() : "—",
                        p.hasNation() && !p.nation().isBlank() ? new Ref(Kind.NATION, p.nation()) : null),
                new Col("余额", money(p.balance())))));

        if (!p.formattedName().isBlank() && !p.formattedName().equals(p.name())) {
            b.add(new DetailScreen.LegacyLine("格式化名称", miniToText(p.formattedName())));
        }
        if (!p.about().isBlank()) b.add(new DetailScreen.Wide("简介", p.about()));
        b.add(new Rule());

        b.add(new Cols(List.of(
                new Col("注册于", date(p.registeredMs())),
                new Col("加入城镇", ago(p.joinedTownAtMs())),
                new Col(p.isOnline() ? "在线" : "上次在线",
                        p.isOnline() ? "现在" : ago(p.lastOnlineMs())))));
        b.add(new Rule());

        b.add(chips("在线", p.isOnline(), "市长", p.isMayor(), "国王", p.isKing(),
                "有城镇", p.hasTown(), "有国家", p.hasNation(), "NPC", p.isNPC()));

        if (!p.townRanks().isEmpty() || !p.nationRanks().isEmpty()) {
            List<String> r = new ArrayList<>();
            if (!p.townRanks().isEmpty()) r.add("城镇: " + String.join(", ", p.townRanks()));
            if (!p.nationRanks().isEmpty()) r.add("国家: " + String.join(", ", p.nationRanks()));
            b.add(new LineList("职位", r));
        }
        addNames(b, "好友", p.friends(), Kind.PLAYER);

        String sub = p.hasTown() && !p.town().isBlank() ? p.town() : "无城镇";
        return new Page(Kind.PLAYER, p.name(), sub, b, "", "");
    }

    // ── Nation ────────────────────────────────────────────────────────────────

    public static Page nation(NationFullData n) {
        List<DetailScreen.Block> b = new ArrayList<>();

        b.add(new Cols(List.of(
                new Col("国王", n.king().isBlank() ? "—" : n.king(),
                        n.king().isBlank() ? null : new Ref(Kind.PLAYER, n.king())),
                new Col("首都", n.capital().isBlank() ? "—" : n.capital(),
                        n.capital().isBlank() ? null : new Ref(Kind.TOWN, n.capital())),
                new Col("建立", date(n.registeredMs())))));

        if (!n.board().isBlank()) b.add(new DetailScreen.Wide("公告板", n.board()));
        b.add(new Rule());

        b.add(new Cols(List.of(
                new Col("区块", String.valueOf(n.numTownBlocks())),
                new Col("城镇", String.valueOf(n.numTowns())),
                new Col("居民", String.valueOf(n.numResidents())))));
        b.add(new Cols(List.of(
                new Col("银行", money(n.balance())),
                // Live: the projection is fetched asynchronously and may not exist yet when this page is
                // built, and once it does the countdown has to keep moving while the panel stays open.
                Col.live("国家加成", () -> bonusText(n.name(), n.nationBonus())),
                new Col("出生点", n.spawnX() + ", " + n.spawnY() + ", " + n.spawnZ()))));
        b.add(new Rule());

        b.add(chips("公开", n.isPublic(), "开放", n.isOpen(), "中立", n.isNeutral()));

        addNames(b, "城镇", n.towns(), Kind.TOWN);
        addNames(b, "盟友", n.allies(), Kind.NATION);
        addNames(b, "敌国", n.enemies(), Kind.NATION);
        addNames(b, "制裁", n.sanctioned(), Kind.NATION);
        addNames(b, "放逐", n.outlaws(), Kind.PLAYER);
        addNames(b, "居民", n.residents(), Kind.PLAYER);
        // Blocs sit with the other collapsible lists rather than up in the summary rows.
        addAllianceBlocks(b, n.name());
        addRanks(b, n.occupiedRanks());

        if (!n.pacts().isEmpty()) {
            List<DetailScreen.RefLine> p = new ArrayList<>();
            for (NationFullData.Pact pact : n.pacts()) {
                // A perpetual pact only has a start, so showing "expires —, duration —" was noise. A
                // fixed-term one gets its end and how long it runs.
                String terms = pact.status().toLowerCase(java.util.Locale.ROOT)
                        + " 始于 " + date(pact.createdMs());
                if (pact.forever()) {
                    terms += " — 永不过期";
                } else {
                    terms += " — 到期于 " + date(pact.expiresAtMs());
                    if (pact.durationMs() > 0) terms += " (" + humanDuration(pact.durationMs()) + ")";
                }
                p.add(new DetailScreen.RefLine(
                        new Ref(Kind.NATION, pact.other(n.name())), terms));
            }
            b.add(new DetailScreen.RefLineList("条约", p));
        }
        List<String> emb = new ArrayList<>();
        for (String e : n.embargoesOwn()) emb.add("对 " + e);
        for (String e : n.embargoesAgainst()) emb.add("来自 " + e);
        if (!emb.isEmpty()) b.add(new LineList("禁运", emb));

        return new Page(Kind.NATION, n.name(), n.numTowns() + " 个城镇", b,
                DiscordUrl.normalize(n.discord()), n.wiki());
    }

    /**
     * "100 → 80 in 4d" once the projection lands, plain "100" until then. Asking for the projection also
     * kicks off the fetch when it is missing; that call is deduped by both an in-flight set and the cache,
     * so evaluating this every frame issues at most one request per nation.
     */
    private static String bonusText(String nation, int currentBonus) {
        var proj = TownyMapMod.nationBonusProjection(nation);
        if (proj == null || proj.nextBonus() >= currentBonus) return String.valueOf(currentBonus);
        long now = System.currentTimeMillis();
        if (proj.daysUntilDropAt(now) < 0) return String.valueOf(currentBonus);
        return currentBonus + " → " + proj.nextBonus() + " in " + countdown(proj, now);
    }

    /** Coarsest sensible unit: days out, then hours, then minutes. */
    private static String countdown(net.townymap.model.NationBonusProjection proj, long now) {
        int d = proj.daysUntilDropAt(now);
        if (d > 1) return d + "d";
        int h = proj.hoursUntilDropAt(now);
        if (h > 1) return h + "h";
        return Math.max(1, proj.minutesUntilDropAt(now)) + "m";
    }

    // ── shared ────────────────────────────────────────────────────────────────

    /** Rank holders are players, so they get the same clickable treatment as any other name list. */
    private static void addRanks(List<DetailScreen.Block> b, Map<String, List<String>> ranks) {
        if (ranks.isEmpty()) return;
        List<DetailScreen.RankGroup> groups = new ArrayList<>();
        ranks.forEach((rank, holders) -> groups.add(
                new DetailScreen.RankGroup(rank, refs(holders, Kind.PLAYER))));
        b.add(new DetailScreen.RankList("职位", groups));
    }

    /** chips("label", state, "label", state, …) */
    private static Chips chips(Object... pairs) {
        List<String> labels = new ArrayList<>();
        List<Boolean> states = new ArrayList<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            labels.add((String) pairs[i]);
            states.add((Boolean) pairs[i + 1]);
        }
        return new Chips(labels, states);
    }
}
