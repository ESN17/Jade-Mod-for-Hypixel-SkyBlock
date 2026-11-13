package dev.jade;

import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;

final class MenuSmoke {
    static void check(Minecraft mc) throws Exception {
        var screen=new JadeScreen();
        ScreenAccess.show(mc,screen);
        var previous=Jade.config().copy();
        Path sampleFile=Path.of("tone.flac").toAbsolutePath();
        try(var in=MenuSmoke.class.getResourceAsStream("/tone.flac")) {
            Files.copy(in,sampleFile,StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            screen.resize(320,240);
            field(screen).setValue("Hub Island");
            press(screen,"Hub Island  ·");
            press(screen,"设置整岛音乐");
            field(screen).setValue(sampleFile.toString());
            Jade.update(cfg -> cfg.tracks.put("Village","place.mp3"));
            press(screen,"保存岛屿默认");
            if (!Settings.load().track("Village",false).equals("place.mp3")) throw new IllegalStateException("Lost local override");
            press(screen,"统一整岛");
            if (!Settings.load().track("Village",false).equals(sampleFile.toString())) throw new IllegalStateException("Island binding not saved");
            if (Jade.config().track("The Mountaintop",true).equals(sampleFile.toString())) throw new IllegalStateException("Other island changed");
            press(screen,"返回岛屿");
            field(screen).setValue("Village");
            press(screen,"Village  ·");
            press(screen,"使用服务器音乐");
            if (!Jade.config().track("Village",false).isEmpty()) throw new IllegalStateException("Server override failed");
            press(screen,"跟随岛屿");
            press(screen,"试听 / 停止");
            if (!Jade.previewing) throw new IllegalStateException("Preview failed");
            var vanilla=net.minecraft.client.resources.sounds.SimpleSoundInstance.forMusic(net.minecraft.sounds.SoundEvents.MUSIC_MENU.value());
            if (!Jade.replacingMusic() || mc.getSoundManager().play(vanilla)!=net.minecraft.client.sounds.SoundEngine.PlayResult.NOT_STARTED)
                throw new IllegalStateException("Vanilla BGM overlapped preview");
            press(screen,"试听 / 停止");
            Path dir=Files.createTempDirectory(Path.of("."),"drop-test-");
            try {
                Path existing=Files.writeString(dir.resolve("tone.flac"),"keep");
                var imported=AudioImport.copy(java.util.List.of(sampleFile,existing,dir),dir);
                if(imported.added().size()!=2 || imported.skipped()!=1 ||
                        !Files.readString(existing).equals("keep") || Files.mismatch(sampleFile,dir.resolve("tone (2).flac"))!=-1)
                    throw new IllegalStateException("Drop collision or filter failed");
            } finally {
                Files.deleteIfExists(dir.resolve("tone (2).flac"));
                Files.deleteIfExists(dir.resolve("tone.flac"));
                Files.deleteIfExists(dir);
            }
            for(String ext:new String[]{"flac","mp3"}) {
                Path sample=Path.of("tone."+ext).toAbsolutePath();
                if (!ext.equals("flac")) try(var in=MenuSmoke.class.getResourceAsStream("/tone."+ext)) {
                    Files.copy(in,sample,StandardCopyOption.REPLACE_EXISTING);
                }
                try(var stream=new LocalTrack(sample).getAudioStream(null,null,true).join()) {
                    int audible=0;
                    for(int i=0;i<35;i++) {
                        var b=stream.read(8192);
                        if (!b.hasRemaining()) throw new IllegalStateException("No frames from "+ext);
                        while(b.hasRemaining()) if(b.get()!=0) audible++;
                    }
                    if (audible<100) throw new IllegalStateException("Silent "+ext);
                }
            }
        } finally {
            Jade.update(s -> {
                s.tracks.clear(); s.tracks.putAll(previous.tracks);
                s.islands.clear(); s.islands.putAll(previous.islands);
            });
            ScreenAccess.show(mc,null);
        }
    }
    private static JadeScreen dropScreen;
    private static Path dropDir;
    private static String priorFolder;
    static void beginDrop(Minecraft mc) throws Exception {
        priorFolder=Jade.config().directory;
        dropDir=Files.createTempDirectory(Path.of(".").toAbsolutePath(),"drop-screen-");
        Jade.update(cfg -> cfg.directory=dropDir.toString());
        dropScreen=new JadeScreen();
        ScreenAccess.show(mc,dropScreen);
        field(dropScreen).setValue("Hub Island");
        press(dropScreen,"Hub Island  ·");
        press(dropScreen,"设置整岛音乐");
        dropScreen.onFilesDrop(java.util.List.of(Path.of("tone.flac").toAbsolutePath()));
    }
    static boolean dropReady() {
        return field(dropScreen).getValue().equals("tone.flac") && Files.exists(dropDir.resolve("tone.flac"));
    }
    static void openPicker() { press(dropScreen,"选择文件…"); }
    static boolean picked() { return field(dropScreen).getValue().equals(Path.of("tone.mp3").toAbsolutePath().toString()); }
    static void finishDrop(Minecraft mc) throws Exception {
        Jade.update(cfg -> cfg.directory=priorFolder);
        ScreenAccess.show(mc,null);
        Files.deleteIfExists(dropDir.resolve("tone.flac"));
        Files.deleteIfExists(dropDir);
    }
    private static EditBox field(Screen s) {
        return s.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
    }
    private static void press(Screen s,String prefix) {
        var b=s.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(w -> w.getMessage().getString().startsWith(prefix)).findFirst().orElseThrow(() -> new IllegalStateException(prefix));
        b.onPress(null);
    }
}
