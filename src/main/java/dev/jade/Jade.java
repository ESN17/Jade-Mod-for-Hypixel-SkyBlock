package dev.jade;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import org.slf4j.LoggerFactory;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;

public final class Jade implements ClientModInitializer {
    private static Settings settings;
    private static String area;
    private static LocalTrack playing;
    private static final List<LocalTrack> fading = new ArrayList<>();
    private static String problem;
    private static boolean multiplayerMusicBlock;
    private static boolean mutedGame;
    static boolean previewing;
    private static boolean reported;
    private static long retryAt;
    private static final BloodRoom blood = new BloodRoom();
    public static void systemMessage(Minecraft mc,String raw) { blood.message(mc,raw); }
    private boolean noticeShown;
    static final String STARTUP_TIP = "为了模组可以正常工作，请不要开启SkyBlock的“开启音乐”功能。您可以在SkyBlock菜单-设置-音效设置-播放音乐中关闭。";
    static Component notice(String body) {
        return Component.empty()
                .append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("Jade").withStyle(ChatFormatting.GREEN))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(body).withStyle(ChatFormatting.GRAY));
    }
    private int ticks;
    private int missing;
    private boolean riftArea;
    private Object level;

    public void onInitializeClient() {
        reload();
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientPlayConnectionEvents.JOIN.register((handler,sender,mc) -> setMultiplayerMusicBlock(mc,!mc.hasSingleplayerServer()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc) -> {
            setMultiplayerMusicBlock(mc,false);
            area=null;
            riftArea=false;
            missing=0;
            level=null;
            blood.select(null,null);
            stop(mc);
        });
        ClientCommandRegistrationCallback.EVENT.register((d,access) -> d.register(literal("jade")
            .executes(c -> openMenu())
            .then(literal("status").executes(c -> status(c)))
            .then(literal("reload").executes(c -> { var e = reload(); return reply(c,e == null ? "Reloaded " + Settings.json : e); }))
            .then(literal("directory").executes(c -> reply(c,settings == null ? problem : settings.directory))
                .then(argument("path",StringArgumentType.greedyString()).executes(c -> change(c,s -> {
                    Path p = Path.of(value(c,"path")).toAbsolutePath().normalize();
                    if (!Files.isDirectory(p)) throw new IllegalArgumentException("Folder does not exist: "+p);
                    s.directory = p.toString();
                }))))
            .then(literal("here").then(argument("file",StringArgumentType.greedyString()).executes(c -> change(c,s -> {
                if (area == null) throw new IllegalArgumentException("No SkyBlock area detected");
                bind(s,area,value(c,"file"));
            }))))
            .then(literal("bind").then(placeArg().then(argument("file",StringArgumentType.greedyString())
                .executes(c -> change(c,s -> bind(s,value(c,"place"),value(c,"file")))))))
            .then(literal("clear").then(placeArg().executes(c -> change(c,s -> s.tracks.put(value(c,"place"),"")))))
            .then(literal("notes").then(literal("on").executes(c -> reply(c,"Jade 已保留服务器音效。")))
                .then(literal("off").executes(c -> reply(c,"Jade 已保留服务器音效。"))))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<FabricClientCommandSource,String> placeArg() {
        return argument("place",StringArgumentType.string()).suggests((c,b) -> {
            if (settings != null) {
                String prefix = b.getRemainingLowerCase().replace("\"","");
                settings.catalog().stream().filter(s -> Locations.key(s).contains(prefix)).limit(100)
                    .forEach(s -> b.suggest(StringArgumentType.escapeIfRequired(s)));
            }
            return b.buildFuture();
        });
    }
    static Settings config() { return settings; }
    static String currentArea() { return area; }
    static String error() { return problem; }
    static int openMenu() {
        var mc = Minecraft.getInstance();
        mc.schedule(() -> ScreenAccess.show(mc,new JadeScreen()));
        return 1;
    }

    interface Edit { void apply(Settings s) throws Exception; }
    private static int change(CommandContext<FabricClientCommandSource> c, Edit edit) {
        try {
            update(edit);
            return reply(c,"Saved. " + Settings.json);
        } catch (Exception e) { return reply(c,"Could not save: " + e.getMessage()); }
    }
    static void update(Edit edit) throws Exception {
        if (settings == null) throw new IllegalStateException(problem);
        Settings next = settings.copy();
        edit.apply(next);
        next.save();
        settings = next;
        retryAt = 0;
    }
    static void bind(Settings s,String place,String file) throws Exception {
        s.file(file);
        s.tracks.put(place,file);
    }
    private static String value(CommandContext<FabricClientCommandSource> c,String key) { return StringArgumentType.getString(c,key); }
    private static int status(CommandContext<FabricClientCommandSource> c) {
        String file = playing == null ? "—" : playing.file.toString();
        return reply(c,"Area: "+Objects.toString(area,"—")+" | Track: "+file+"\n"+MusicDownloads.status+
                (problem == null ? "" : "\n"+problem)+"\n/jade directory <path> · here <file> · bind \"place\" <file> · clear \"place\" · reload");
    }
    private static int reply(CommandContext<FabricClientCommandSource> c,String msg) {
        c.getSource().sendFeedback(notice(msg));
        return 1;
    }
    private static String reload() {
        try { settings = Settings.load(); MusicDownloads.start(); retryAt=0; problem=null; return null; }
        catch (Exception e) { problem="Configuration: "+e.getMessage(); LoggerFactory.getLogger("Jade").warn(problem); return problem; }
    }

    private void tick(Minecraft mc) {
        if (++ticks % 5 != 0 || settings == null || previewing) return;
        String raw = Locations.current(mc);
        if (raw != null && !noticeShown) {
            mc.player.sendSystemMessage(notice(STARTUP_TIP));
            noticeShown=true;
        }
        updateMusic(mc,raw,Locations.riftMarker);
    }

    void updateMusic(Minecraft mc,String raw,boolean rift) {
        boolean inherit = Locations.isNone(raw);
        String found = inherit ? area : blood.select(mc.level,raw);
        // Give the sidebar a second to refresh within the same world.
        if (!inherit && found == null && level == mc.level && mc.level != null && mc.player != null && ++missing < 4) return;
        if (found != null) missing = 0;
        if (!inherit) riftArea = rift;
        level = mc.level;
        if (!Objects.equals(area,found)) retryAt=0;
        area = found;
        fading.removeIf(track -> track.isStopped() || !mc.getSoundManager().isActive(track));
        String name = settings.track(area,riftArea);
        if (name.isBlank()) { fadeCurrent(); return; }
        if (MusicDownloads.pending(name)) { fadeCurrent(); return; }
        if (System.currentTimeMillis() < retryAt) return;
        try {
            Path path = settings.file(name);
            if (playing != null && playing.file.equals(path) && playing.getSource() == channel()) {
                if (playing.failure != null) throw new IllegalStateException(playing.failure);
                if (mc.getSoundManager().isActive(playing)) {
                    return;
                }
            }
            transition(mc,path);
            reported = false;
            problem = null;
        } catch (Exception e) {
            stop(mc);
            problem = area + ": " + e.getMessage();
            if (!reported && mc.player != null) {
                mc.player.sendSystemMessage(notice(problem));
                reported = true;
            }
            retryAt = System.currentTimeMillis()+15000;
        }
    }
    static LocalTrack transition(Minecraft mc,Path path) {
        if (playing!=null && playing.file.equals(path) && mc.getSoundManager().isActive(playing)) return playing;
        LocalTrack next = fading.stream().filter(t -> t.file.equals(path) && !t.isStopped()
                && mc.getSoundManager().isActive(t)).findFirst().orElse(null);
        if (next==null) {
            next=new LocalTrack(path);
            next.fadeIn();
            if (mc.getSoundManager().play(next)==SoundEngine.PlayResult.NOT_STARTED) {
                fadeCurrent();
                retryAt=System.currentTimeMillis()+1500;
                return null;
            }
        } else {
            fading.remove(next);
            next.resumeFade();
        }
        fadeCurrent();
        playing=next;
        if (!mutedGame) takeOver(mc);
        return next;
    }
    private static void fadeCurrent() {
        if (playing!=null) {
            playing.fadeOut();
            fading.add(playing);
            playing=null;
        }
        if (fading.isEmpty()) mutedGame=false;
    }
    static void setMultiplayerMusicBlock(Minecraft mc,boolean enabled) {
        multiplayerMusicBlock=enabled;
        if (!enabled) return;
        // Menu music can still be active when the join packet completes.
        mc.getMusicManager().stopPlaying();
        for (var id:mc.getSoundManager().getAvailableSounds())
            if (vanillaMusic(id)) mc.getSoundManager().stop(id,null);
    }
    public static boolean blockVanillaScheduler() {
        return multiplayerMusicBlock || replacingMusic();
    }
    private static boolean vanillaMusic(Identifier id) {
        return id.getNamespace().equals("minecraft") &&
            (id.getPath().startsWith("music.") || id.getPath().startsWith("music_disc."));
    }

    static void takeOver(Minecraft mc) {
        mutedGame = true;
        mc.getMusicManager().stopPlaying();
    }
    public static boolean replacingMusic() {
        return mutedGame && (previewing || playing!=null && playing.failure==null || !fading.isEmpty());
    }
    static SoundSource channel() {
        return SoundSource.MUSIC;
    }
    static void stop(Minecraft mc) {
        if (playing != null) mc.getSoundManager().stop(playing);
        fading.forEach(mc.getSoundManager()::stop);
        fading.clear();
        playing=null;
        mutedGame=false;
    }
    public static boolean suppress(SoundInstance sound) {
        return blockVanillaScheduler() && vanillaMusic(sound.getIdentifier());
    }
}
