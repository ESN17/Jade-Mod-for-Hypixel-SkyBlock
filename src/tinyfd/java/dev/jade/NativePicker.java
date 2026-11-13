package dev.jade;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

final class NativePicker {
    static CompletableFuture<Path> open(boolean directory,String start) {
        return CompletableFuture.supplyAsync(() -> {
            String value;
            if (directory) {
                value=TinyFileDialogs.tinyfd_selectFolderDialog("Jade - Music folder",start);
            } else try(var stack=MemoryStack.stackPush()) {
                var patterns=stack.mallocPointer(AudioFiles.types.size());
                for (String type:AudioFiles.types) patterns.put(stack.UTF8("*."+type));
                patterns.flip();
                value=TinyFileDialogs.tinyfd_openFileDialog("Jade - Select music",start,patterns,"Audio files",false);
            }
            return value==null?null:Path.of(value);
        });
    }
}
