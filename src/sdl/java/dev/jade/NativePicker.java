package dev.jade;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.*;
import org.lwjgl.system.MemoryUtil;

final class NativePicker {
    static CompletableFuture<Path> open(boolean directory,String start) {
        var result=new CompletableFuture<Path>();
        var mc=Minecraft.getInstance();
        var callback=SDL_DialogFileCallback.create((userdata,list,filter) -> {
            try {
                if (list==0) throw new IllegalStateException("无法打开系统文件窗口");
                long first=MemoryUtil.memGetAddress(list);
                result.complete(first==0?null:Path.of(MemoryUtil.memUTF8(first)));
            } catch (RuntimeException ex) { result.completeExceptionally(ex); }
        });
        var label=MemoryUtil.memUTF8("Audio files");
        var pattern=MemoryUtil.memUTF8(String.join(";",AudioFiles.types));
        var filters=SDL_DialogFileFilter.calloc(1);
        filters.get(0).name(label).pattern(pattern);
        result.whenComplete((path,error) -> mc.schedule(() -> {
            callback.free(); filters.free(); MemoryUtil.memFree(label); MemoryUtil.memFree(pattern);
        }));
        try {
            long window=mc.getWindow().handle();
            if (directory) SDLDialog.SDL_ShowOpenFolderDialog(callback,0,window,start,false);
            else SDLDialog.SDL_ShowOpenFileDialog(callback,0,window,filters,start,false);
        } catch (RuntimeException | LinkageError ex) { result.completeExceptionally(ex); }
        return result;
    }
}
