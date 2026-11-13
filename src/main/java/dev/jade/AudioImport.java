package dev.jade;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

final class AudioImport {
    record Result(List<Path> added,int skipped,String firstError) {}

    static Result copy(List<Path> sources,Path folder) {
        var added=new ArrayList<Path>();
        int skipped=0;
        String error="";
        for (Path source:sources) {
            try {
                if (!Files.isRegularFile(source) || !AudioFiles.supported(source.getFileName().toString())) {
                    skipped++;
                    continue;
                }
                added.add(copyOne(source,folder.toRealPath()));
            } catch (IOException | SecurityException ex) {
                skipped++;
                if(error.isEmpty()) error=source.getFileName()+" · "+ex.getMessage();
            }
        }
        return new Result(List.copyOf(added),skipped,error);
    }
    private static Path copyOne(Path source,Path folder) throws IOException {
        String name=source.getFileName().toString();
        Path target=folder.resolve(name);
        if (Files.exists(target) && Files.isSameFile(source,target)) return target;
        // Stage beside the destination so an incomplete copy never appears as a playable track.
        Path temp=Files.createTempFile(folder,".jade-import-",".part");
        try {
            Files.copy(source,temp,StandardCopyOption.REPLACE_EXISTING);
            int dot=name.lastIndexOf('.');
            for (int n=2;;n++) {
                try { return Files.move(temp,target); }
                catch (FileAlreadyExistsException collision) {
                    target=folder.resolve(name.substring(0,dot)+" ("+n+")"+name.substring(dot));
                }
            }
        } finally { Files.deleteIfExists(temp); }
    }
}
