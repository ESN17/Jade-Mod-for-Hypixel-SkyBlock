package dev.jade;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import java.io.File;


final class JadeScreen extends Screen {
    private enum Page { PLACES, TRACK, FOLDER }
    private Page page = Page.PLACES;
    private String island="";
    private boolean wholeIsland;
    private int listY;
    private String query="", selection="", draft="", folder="", feedback="";
    private final List<Button> rows = new ArrayList<>();
    private List<String> matches = List.of();
    private int offset, x, y, w, h, nav, cx, cw, capacity;
    private EditBox input;
    private Button previous,next;
    private LocalTrack preview;
    private boolean choosing;
    private int revision;

    JadeScreen() {
        super(Component.literal("Jade 音乐设置"));
    }
    private Button button(String label,int bx,int by,int bw,Runnable click) {
        return addRenderableWidget(Button.builder(Component.literal(label),b -> click.run())
                .bounds(bx,by,bw,20).build());
    }
    private EditBox field(String label,String value,int fy,Consumer<String> onChange) {
        EditBox box = new EditBox(font,cx,fy,cw,20,Component.literal(label));
        box.setHint(Component.literal(label));
        box.setMaxLength(4096);
        box.setValue(value);
        box.setResponder(onChange);
        return addRenderableWidget(box);
    }
    @Override protected void init() {
        w=Math.min(720,width-10); h=Math.min(424,height-10);
        x=(width-w)/2; y=(height-h)/2; nav=w<430?73:104;
        cx=x+nav+9; cw=w-nav-18;
        rows.clear();
        button("岛屿目录",x+4,y+35,nav-7,() -> list(""));
        if (!island.isEmpty() && page!=Page.PLACES)
            button("返回岛屿",x+4,y+107,nav-7,() -> list(island));
        button("当前地点",x+4,y+59,nav-7,() -> edit(Jade.currentArea())).active=Jade.currentArea()!=null;
        button("曲库设置",x+4,y+83,nav-7,() -> {
            if (Jade.config()!=null) folder=Jade.config().directory;
            page(Page.FOLDER);
        });
        button("完成",x+4,y+h-26,nav-7,this::onClose);
        if (Jade.config()==null) { feedback=Objects.toString(Jade.error(),"配置加载失败"); return; }
        switch(page) {
            case PLACES -> {
                boolean root=island.isEmpty();
                if (!root) button("设置整岛音乐",cx,y+26,cw,this::editIsland);
                input=field(root?"搜索岛屿或地点":"搜索岛内地点",query,y+(root?28:51),s -> { query=s; offset=0; refresh(); });
                listY=y+(root?54:77);
                capacity=Math.max(1,(y+h-50-listY)/22);
                previous=button("‹",cx+cw-48,y+h-48,22,() -> shift(-capacity));
                next=button("›",cx+cw-22,y+h-48,22,() -> shift(capacity));
                refresh();
            }
            case TRACK -> {
                input=field("曲库文件名或完整路径",draft,y+55,s -> draft=s);
                int half=(cw-4)/2;
                button("选择文件…",cx,y+79,half,() -> pick(false));
                button("试听 / 停止",cx+half+4,y+79,cw-half-4,this::listen);
                button(wholeIsland?"保存岛屿默认":"保存地点配乐",cx,y+103,half,this::saveTrack);
                if (wholeIsland) {
                    button("统一整岛",cx+half+4,y+103,cw-half-4,() -> {
                        stopPreview();
                        if (apply(cfg -> cfg.setIsland(island,draft.strip(),true))) feedback="全岛已统一，地点单独设置已清除";
                    }).setTooltip(Tooltip.create(Component.literal("把所选音乐应用到本岛全部地点，包括已有单独设置的地点。")));
                } else {
                    button("跟随岛屿",cx+half+4,y+103,cw-half-4,() -> {
                        stopPreview();
                        if (apply(cfg -> cfg.tracks.remove(selection))) input.setValue(Jade.config().track(selection,false));
                    });
                }
                button("使用服务器音乐",cx,y+127,cw,() -> {input.setValue(""); saveTrack();});
            }
            case FOLDER -> {
                input=field("音乐目录完整路径",folder,y+47,s -> folder=s);
                int half=(cw-4)/2;
                button("选择目录…",cx,y+72,half,() -> pick(true));
                button("保存目录",cx+half+4,y+72,cw-half-4,() -> apply(s -> {
                    Path path=Path.of(folder).toAbsolutePath().normalize();
                    if (!Files.isDirectory(path) || folder.isBlank()) throw new IllegalArgumentException("目录不存在");
                    s.directory=path.toString();
                }));

            }
        }
    }
    private void page(Page newPage) { stopPreview(); revision++; page=newPage; feedback=""; rebuildWidgets(); }
    private void list(String id) {
        island=id; query=""; offset=0; wholeIsland=false; page(Page.PLACES);
    }
    private void editIsland() {
        wholeIsland=true;
        draft=Jade.config().islands.getOrDefault(island,"");
        page(Page.TRACK);
    }
    private void edit(String place) {
        if (place==null || Jade.config()==null) return;
        wholeIsland=false;
        selection=place;
        island=Islands.of(place,place.equals(Jade.currentArea()) && Locations.riftMarker);
        draft=Jade.config().track(place,Locations.riftMarker);
        page(Page.TRACK);
    }
    private void refresh() {
        if (rows.contains(getFocused())) setFocused(input);
        rows.forEach(this::removeWidget); rows.clear();
        Settings cfg=Jade.config();
        String term=Locations.key(query);
        boolean root=island.isEmpty();
        var groups=new TreeMap<String,List<String>>(String.CASE_INSENSITIVE_ORDER);
        for (String id:cfg.catalog())
            groups.computeIfAbsent(Islands.of(id,false),ignored -> new ArrayList<>()).add(id);
        if (root) matches=groups.keySet().stream().filter(g -> Locations.key(g).contains(term) ||
            groups.get(g).stream().anyMatch(id -> Locations.key(id).contains(term))).toList();
        else matches=groups.getOrDefault(island,List.of()).stream().filter(id ->
            Locations.key(id+" "+cfg.track(id,false)).contains(term)).toList();
        offset=Math.clamp(offset,0,Math.max(0,matches.size()-capacity));
        for(int i=offset;i<Math.min(matches.size(),offset+capacity);i++) {
            String id=matches.get(i);
            String file=root?cfg.islands.getOrDefault(id,""):cfg.track(id,false);
            String state=file.isBlank()?"服务器音乐":pathLabel(file);
            String label=root?id+"  ·  "+groups.get(id).size()+" 个地点":id+"  ·  "+(cfg.tracks.containsKey(id)?state:"继承 · "+state);
            Button row=button(font.plainSubstrByWidth(label,cw-10),cx,listY+(i-offset)*22,cw,() -> {
                if (root) list(id); else edit(id);
            });
            row.setTooltip(Tooltip.create(Component.literal(id+"\\n"+state)));
            rows.add(row);
        }
        previous.active=offset>0; next.active=offset+capacity<matches.size();
    }
    private static String pathLabel(String file) {
        return file.substring(Math.max(file.lastIndexOf('/'),file.lastIndexOf('\\'))+1);
    }
    private void shift(int n) { offset+=n; refresh(); }
    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy) {
        if (page==Page.PLACES && Jade.config()!=null && mx>=cx && mx<=cx+cw && my>=y+52 && my<y+h-49) {
            shift(-(int)Math.signum(dy)); return true;
        }
        return super.mouseScrolled(mx,my,dx,dy);
    }
    private boolean apply(Jade.Edit change) {
        try { Jade.update(change); feedback="已保存"; return true; }
        catch(Exception ex) { feedback="未保存 · "+ex.getMessage(); return false; }
    }
    private void saveTrack() {
        stopPreview();
        apply(s -> {
            if (wholeIsland) s.setIsland(island,draft.strip(),false);
            else if (draft.isBlank()) s.tracks.put(selection,"");
            else Jade.bind(s,selection,draft.strip());
        });
    }
    private void pick(boolean directory) {
        if (choosing) return;
        Path base=Path.of(Jade.config().directory);
        String value=directory?folder:draft;
        Path start=base;
        try {
            Path typed=base.resolve(value);
            if (Files.exists(typed)) start=typed;
        } catch (InvalidPathException ignored) {}
        String initial=start.toString();
        if (Files.isDirectory(start) && !initial.endsWith(File.separator)) initial+=File.separator;
        int origin=revision;
        choosing=true;
        feedback="正在打开系统选择窗口…";
        try {
            NativePicker.open(directory,initial).whenComplete((path,error) -> minecraft.execute(() -> {
                choosing=false;
                if (origin!=revision || ScreenAccess.current(minecraft)!=this) return;
                if (error!=null) { feedback="窗口打开失败，可拖入音乐或输入路径"; return; }
                if (path==null) { feedback="已取消选择"; return; }
                if (!(directory?Files.isDirectory(path):Files.isRegularFile(path) && AudioFiles.supported(path.toString()))) {
                    feedback="请选择有效的"+(directory?"目录":"音频文件"); return;
                }
                input.setValue(path.toString());
                feedback="已选择，保存后生效";
            }));
        } catch (RuntimeException | LinkageError ex) {
            choosing=false; feedback="窗口打开失败，可拖入音乐或输入路径";
        }
    }

    @Override public void onFilesDrop(List<Path> paths) {
        if (Jade.config()==null || paths.isEmpty()) return;
        Path destination=Path.of(Jade.config().directory);
        var sources=List.copyOf(paths);
        int origin=revision;
        String originalDraft=draft;
        feedback="正在导入 "+sources.size()+" 个文件…";
        CompletableFuture.supplyAsync(() -> AudioImport.copy(sources,destination)).whenComplete((result,error) ->
            minecraft.execute(() -> {
                if (ScreenAccess.current(minecraft)!=this) return;
                if (error!=null) { feedback="导入失败 · "+error.getMessage(); return; }
                feedback="已加入曲库 "+result.added().size()+" 首";
                if (result.skipped()>0) feedback+=" · 跳过 "+result.skipped()+" 项";
                if (!result.firstError().isEmpty()) feedback+=" · "+result.firstError();
                if (sources.size()==1 && result.added().size()==1 && page==Page.TRACK && origin==revision
                        && Objects.equals(draft,originalDraft) && destination.equals(Path.of(Jade.config().directory))) {
                    input.setValue(result.added().getFirst().getFileName().toString());
                    feedback+=" · 已填入，保存后生效";
                }
            }));
    }
    private void listen() {
        if (preview!=null) { stopPreview(); feedback="试听已停止"; return; }
        try {
            var nextTrack=new LocalTrack(Jade.config().file(draft.strip()));
            Jade.stop(minecraft);
            if (minecraft.getSoundManager().play(nextTrack)==SoundEngine.PlayResult.NOT_STARTED)
                throw new IllegalStateException("无法开始播放，请检查音量");
            Jade.previewing=true; Jade.takeOver(minecraft); preview=nextTrack; feedback="试听中 · "+nextTrack.file.getFileName();
        } catch(Exception ex) {feedback="无法试听 · "+ex.getMessage();}
    }
    @Override public void tick() {
        if (preview!=null && preview.failure!=null) {feedback="试听失败 · "+preview.failure; stopPreview();}
    }
    private void stopPreview() {
        if (preview!=null) minecraft.getSoundManager().stop(preview);
        preview=null; Jade.previewing=false;
    }
    @Override public void removed() { stopPreview(); super.removed(); }
    @Override public boolean isPauseScreen() { return false; }
    private void line(GuiGraphicsExtractor g,String text,int px,int py,int limit,int color) {
        g.text(font,font.plainSubstrByWidth(text,limit),px,py,color,false);
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float dt) {
        super.extractBackground(g,mx,my,dt);
        g.fill(x,y,x+w,y+h,0xff152421); g.fill(x,y,x+nav,y+h,0xff10201e);
        g.fill(x,y,x+w,y+2,0xff74cba9);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt) {
        int fg=0xffe3f2ea, muted=0xffa9c3b8;
        line(g,"Jade",x+10,y+13,nav-14,0xff81dfb5);
        line(g,switch(page){case PLACES -> island.isEmpty()?"岛屿目录":island; case TRACK -> wholeIsland?island+" · 整岛音乐":selection; case FOLDER -> "曲库与播放";},cx,y+12,cw,fg);
        if (Jade.config()!=null) {
            if (page==Page.PLACES) line(g,matches.isEmpty()?"没有匹配的地点":(offset+1)+"–"+Math.min(matches.size(),offset+capacity)+" / "+matches.size(),cx,y+h-42,cw-52,muted);
            if (page==Page.TRACK) {
                line(g,wholeIsland?"默认音乐由岛内地点继承":"所属岛屿 · "+island,cx,y+29,cw,muted);
                line(g,"音频文件 · 可拖入导入曲库",cx,y+43,cw,fg);
                if(h>=205) line(g,"已绑定 · "+(wholeIsland?Jade.config().islands.getOrDefault(island,""):Jade.config().track(selection,false)),cx,y+156,cw,muted);
            }
            if (page==Page.FOLDER) {
                line(g,"音乐目录",cx,y+31,cw,muted);
                int vol=Math.round(100*minecraft.options.getSoundSourceVolume(Jade.channel()));
                int master=Math.round(100*minecraft.options.getSoundSourceVolume(SoundSource.MASTER));
                line(g,"音乐音量 "+vol+"%   主音量 "+master+"%",cx,y+104,cw,muted);
                line(g,"从资源管理器拖入音乐，可一次导入多首",cx,y+123,cw,muted);
            }
        }
        line(g,feedback.isEmpty()?"当前地点 · "+Objects.toString(Jade.currentArea(),"未识别"):feedback,cx,y+h-17,cw,muted);
        super.extractRenderState(g,mx,my,dt);
    }
}
