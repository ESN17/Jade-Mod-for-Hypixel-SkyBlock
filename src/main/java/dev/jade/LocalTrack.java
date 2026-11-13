package dev.jade;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.concurrent.*;
import javax.sound.sampled.*;
import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.client.sounds.*;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

final class LocalTrack extends AbstractSoundInstance implements FabricSoundInstance, TickableSoundInstance {
    final Path file;
    volatile boolean ready;
    volatile String failure;
    LocalTrack(Path file) {
        super(Identifier.fromNamespaceAndPath("jade_skyblock_music","local"), Jade.channel(), SoundInstance.createUnseededRandom());
        this.file = file;
        relative = true;
        attenuation = SoundInstance.Attenuation.NONE;
        looping = true;
    }
    private static final float FADE_STEP = 1f/40;
    private boolean ending, stopped;
    void fadeIn() { volume=0; ending=false; }
    void fadeOut() { ending=true; }
    void resumeFade() { ending=false; }
    @Override public boolean canStartSilent() { return true; }
    @Override public boolean isStopped() { return stopped; }
    @Override public void tick() {
        if (ending) {
            volume=Math.max(0,volume-FADE_STEP);
            if (volume<=0.00001f) stopped=true;
        } else if (ready) volume=Math.min(1,volume+FADE_STEP);
    }
    @Override public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary library, Identifier id, boolean repeat) {
        return CompletableFuture.supplyAsync(() -> {
            try { return new FileLoop(); }
            catch (Exception ex) { failure = ex.toString(); throw new CompletionException(ex); }
        });
    }
    private AudioStream open() throws IOException {
        if (!AudioFiles.extension(file.toString()).equals("ogg")) return new PcmStream(file);
        var input = new BufferedInputStream(Files.newInputStream(file));
        try { return new JOrbisAudioStream(input); }
        catch (IOException | RuntimeException ex) { input.close(); throw ex; }
    }
    private final class FileLoop implements AudioStream {
        private AudioStream stream = open();
        FileLoop() throws IOException {}
        public AudioFormat getFormat() { return stream.getFormat(); }
        public ByteBuffer read(int size) throws IOException {
            try {
                var data = stream.read(size);
                if (!data.hasRemaining()) {
                    stream.close();
                    stream = open();
                    data = stream.read(size);
                    if (!data.hasRemaining()) throw new IOException("Empty audio file: " + file);
                }
                ready = true;
                return data;
            } catch (IOException | RuntimeException ex) { failure = ex.toString(); throw ex; }
        }
        public void close() throws IOException { stream.close(); }
    }

    private static final class PcmStream implements AudioStream {
        private final AudioInputStream pcm;
        PcmStream(Path file) throws IOException { pcm = AudioFiles.pcm(file); }
        public AudioFormat getFormat() { return pcm.getFormat(); }
        public ByteBuffer read(int size) throws IOException {
            int frame = pcm.getFormat().getFrameSize();
            byte[] bytes = pcm.readNBytes(Math.max(frame, size / frame * frame));
            return ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder()).put(bytes).flip();
        }
        public void close() throws IOException { pcm.close(); }
    }
}
