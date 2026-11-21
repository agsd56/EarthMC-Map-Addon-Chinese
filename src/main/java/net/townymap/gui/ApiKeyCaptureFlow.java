package net.townymap.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.townymap.TownyMapConfig;
import net.townymap.TownyMapMod;
import net.townymap.api.ApiKeyCapture;

/**
 * Starts the "read my key out of chat" flow, from wherever the player asked for it.
 *
 * <p>Shared rather than duplicated because it is offered from two screens that cannot see each
 * other: the settings row and the first-launch notice. Both owe the player the same consent prompt,
 * the same command and the same promise about what is read, and a second copy of that promise would
 * be a second copy to keep honest.
 */
public final class ApiKeyCaptureFlow {

    /**
     * Always {@code copy}, never {@code create}, and not a guess.
     *
     * <p>EarthMC splits the job three ways: {@code create} mints a first key, {@code copy} hands
     * back one that already exists, {@code delete} is how you rotate. Writing the right one out
     * would mean knowing whether this player has a key -- and nothing here knows that. An empty
     * {@link TownyMapConfig#earthMcApiKey} covers a fresh install, a new machine, a cleared config
     * and a key made for some other tool exactly as well as it covers someone who has never made
     * one, and no keyless endpoint will answer the question either.
     *
     * <p>So it does not guess. {@code copy} is the one command that is safe under every answer: it
     * cannot mint, cannot rotate, and cannot disturb an account that is already set up -- which
     * matters all the more because what {@code create} does to an account that already has a key is
     * undocumented and not in any public source. If no key comes back, that silence is the answer,
     * and {@link TownyMapMod} offers {@code create} on the spot.
     */
    public static final String KEY_COMMAND = "/api key copy";

    private ApiKeyCaptureFlow() {}

    /**
     * Asks first, then opens the window and puts the command in the player's chat box.
     *
     * @param returnTo the screen Cancel goes back to -- whichever one the player asked from, so
     *                 declining leaves them where they were rather than dropping them into the world.
     */
    public static void start(Screen returnTo) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        TownyMapConfig cfg = TownyMapMod.getConfig();
        String tail = cfg == null || cfg.earthMcApiKey.isEmpty()
                ? "如果你还没有密钥，EarthMC 会提示，"
                        + "本模组会给出创建命令。"
                : "EarthMC 返回的任何内容都会替换当前保存的密钥。";
        mc.setScreen(new ConfirmScreen(
                ok -> {
                    if (!ok) { mc.setScreen(returnTo); return; }
                    ApiKeyCapture.arm();
                    TownyMapMod.announceKeyCaptureArmed(KEY_COMMAND);
                    // Chat, with the command already written out -- all that is left is Enter. It is
                    // not sent for them: these commands act on their account, which is theirs to
                    // trigger, and a command typed into their own chat box is visible, editable and
                    // abandonable in a way one sent on their behalf is not.
                    mc.setScreen(new ChatScreen(KEY_COMMAND, false));
                },
                Component.literal("读取聊天以自动获取你的 API 密钥？"),
                Component.literal("接下来的 " + (ApiKeyCapture.WINDOW_MS / 1000)
                        + " 秒里，本模组会读取收到的聊天消息，只查找 EarthMC API 密钥。"
                        + "聊天框会预填 " + KEY_COMMAND + "——按回车"
                        + "发送即可；无论 "
                        + "EarthMC 是直接打印密钥还是把它放在可点击复制的按钮后面，密钥一出现就会被保存。" + tail
                        + "不读取、不保存、不发送其它内容，窗口会"
                        + "自动关闭。"),
                Component.literal("Read chat for " + (ApiKeyCapture.WINDOW_MS / 1000) + "s"),
                Component.literal("取消")));
    }
}
