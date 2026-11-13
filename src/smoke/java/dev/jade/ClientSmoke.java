package dev.jade;

import java.nio.file.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.sounds.SoundSource;

/** Offline client playback and migration regression checks. */
public final class ClientSmoke implements ClientModInitializer {
    private LocalTrack first, second;
    private int age;
    private final long deadline = System.nanoTime() + java.time.Duration.ofMinutes(20).toNanos();
    private final Jade sidebar = new Jade();
    private boolean observedBlend,dropStarted,pickerOpened;
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (!mc.getSoundManager().getAvailableSounds().contains(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("jade_skyblock_music","local"))) return;
            try {
                if (age==0 && !MusicDownloads.status.startsWith("默认曲库已就绪")) {
                    require(System.nanoTime()<deadline && !MusicDownloads.status.contains("/jade reload"),MusicDownloads.status);
                    return;
                }
                if (age++==0) {
                    var menu=net.minecraft.client.resources.sounds.SimpleSoundInstance.forMusic(net.minecraft.sounds.SoundEvents.MUSIC_MENU.value());
                    require(!Jade.blockVanillaScheduler() && !Jade.suppress(menu),"Menu music blocked before join");
                    Jade.setMultiplayerMusicBlock(mc,true);
                    var notice=Jade.notice(Jade.STARTUP_TIP);
                    require(notice.getString().equals("[Jade] "+Jade.STARTUP_TIP) &&
                            notice.getSiblings().get(1).getStyle().getColor().getValue()==0x55ff55,"Startup notice");
                    var note=net.minecraft.client.resources.sounds.SimpleSoundInstance.forMusic(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value());
                    Jade.previewing=true;
                    Jade.takeOver(mc);
                    require(!Jade.suppress(note) && mc.getSoundManager().play(note)!=net.minecraft.client.sounds.SoundEngine.PlayResult.NOT_STARTED,
                            "Server note on MUSIC channel blocked during Jade playback");
                    Jade.previewing=false;
                    Jade.stop(mc);
                    require(Jade.blockVanillaScheduler() && mc.getSoundManager().play(menu)==net.minecraft.client.sounds.SoundEngine.PlayResult.NOT_STARTED,
                        "Server music not blocked without a Jade track");
                    require(!Jade.suppress(new LocalTrack(Path.of("test.mp3"))),"Local track blocked");
                    Jade.setMultiplayerMusicBlock(mc,false);
                    require(!Jade.blockVanillaScheduler() && !Jade.suppress(menu),"Menu music blocked after disconnect");
                    verifyDefaults();
                    MenuSmoke.check(mc);
                    require(Locations.resolve("The Mount§paintop",true,"").equals("The Mountaintop"),"Rift");
                    require(Locations.resolve("The Garden \uE018 x3",false,"").equals("The Garden"),"Garden");
                    require(Jade.channel()==SoundSource.MUSIC,"Volume slider");
                    require(Islands.byPlace.size()==343,"Location count");
                    sidebar.updateMusic(mc,"None",false);
                    require(Jade.currentArea()==null && !Jade.replacingMusic(),"None before first location");
                    var config=Jade.config();
                    String old=config.islands.put("Rift Dimension",Path.of("tone.flac").toRealPath().toString());
                    try {
                        sidebar.updateMusic(mc,"Unlisted Rift Room",true);
                        first=Jade.transition(mc,Path.of("tone.flac").toRealPath());
                        for (int i=0;i<20;i++) sidebar.updateMusic(mc,"§7nOnE",false);
                        require("Unlisted Rift Room".equals(Jade.currentArea()) && Jade.replacingMusic(),"None lost Rift binding");
                        require(Jade.transition(mc,first.file)==first,"None restarted or stopped the track");
                    } finally { if(old!=null) config.islands.put("Rift Dimension",old); else config.islands.remove("Rift Dimension"); }
                    // Keep simulated transitions independent of the real disconnected sidebar.
                    Jade.previewing=true;
                }
                if (age>2400) throw new IllegalStateException("Transition timed out");
                if (second==null && first.ready && first.getVolume()>=0.99f) {
                    require(Jade.transition(mc,first.file)==first,"Same file restarted");
                    var config=Jade.config();
                    String old=config.tracks.put("Village",Path.of("tone.mp3").toRealPath().toString());
                    try {
                        sidebar.updateMusic(mc,"Village",false);
                        require("Village".equals(Jade.currentArea()),"None did not release on real location");
                        second=Jade.transition(mc,Path.of("tone.mp3").toRealPath());
                    } finally { if(old!=null) config.tracks.put("Village",old); else config.tracks.remove("Village"); }
                    require(second!=first && second.getVolume()==0,"Fade-in start");
                }
                if (second!=null) {
                    if(first.getVolume()>0 && first.getVolume()<0.99f && second.getVolume()>0 && second.getVolume()<0.99f)
                        observedBlend=true;
                    if(first.isStopped() && second.getVolume()>=0.99f) {
                        require(observedBlend,"No crossfade overlap");
                        if (!dropStarted) require(mc.getSoundManager().isActive(second),"Next sound inactive");
                        Jade.previewing=false;
                        Jade.stop(mc);
                        if (!dropStarted) {MenuSmoke.beginDrop(mc); dropStarted=true; return;}
                        if (!MenuSmoke.dropReady() && !pickerOpened) return;
                        if (Files.exists(Path.of("verify-picker.flag"))) {
                            if (!pickerOpened) {MenuSmoke.openPicker(); pickerOpened=true; return;}
                            if (!MenuSmoke.picked()) return;
                        }
                        MenuSmoke.finishDrop(mc);
                        Files.writeString(Path.of("jade-041-ok.txt"),"Downloaded music decoding, None continuity, island menu, inheritance, replace-all, server override, music channel, same-file continuity and live crossfade passed.");
                        mc.stop();
                    }
                }
            } catch(Exception ex) {
                ex.printStackTrace();
                try {Files.writeString(Path.of("jade-041-failed.txt"),ex.toString());} catch(Exception ignored) {}
                mc.stop();
            }
        });
    }
    private static void verifyDefaults() throws Exception {
        Settings fresh=new Settings();
        DefaultMusic.install(fresh);
        require(fresh.islands.keySet().equals(new java.util.TreeSet<>(Islands.byPlace.values())),"Island catalog coverage");
        var names=new java.util.HashSet<>(fresh.islands.values());
        names.addAll(fresh.tracks.values());
        verifyRetry(fresh.file(names.iterator().next()));
        for(String name:names) {
            var path=fresh.file(name);
            try(var stream=new LocalTrack(path).getAudioStream(null,null,true).join()) {
                require(stream.read(32768).hasRemaining(),"No audio frames: "+name);
            } catch(Exception ex) { throw new IllegalStateException("Bundled audio: "+name,ex); }
        }
    }
    private static void verifyRetry(Path source) throws Exception {
        var folder=Files.createTempDirectory(Settings.root,"retry-test-");
        var name=source.getFileName().toString();
        var target=folder.resolve(name);
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        var attempts=new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/", exchange -> {
            try(exchange) {
                if (attempts.incrementAndGet()==1) {
                    exchange.sendResponseHeaders(200,4);
                    exchange.getResponseBody().write(new byte[4]);
                } else {
                    exchange.sendResponseHeaders(200,Files.size(source));
                    Files.copy(source,exchange.getResponseBody());
                }
            }
        });
        server.start();
        var base=java.net.URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/");
        try(var client=java.net.http.HttpClient.newHttpClient()) {
            Files.writeString(target,"prior");
            try {
                MusicDownloads.download(client,base,folder,name);
                throw new IllegalStateException("Truncated response accepted");
            } catch(java.io.IOException expected) {
                require(Files.readString(target).equals("prior"),"Failed download replaced existing file");
            }
            MusicDownloads.download(client,base,folder,name);
            require(Files.mismatch(source,target)==-1,"Retry corrupted track");
        } finally {
            server.stop(0);
            Files.deleteIfExists(target);
            Files.delete(folder);
        }
    }
    private static void require(boolean value,String name) {if(!value) throw new IllegalStateException(name);}
}
