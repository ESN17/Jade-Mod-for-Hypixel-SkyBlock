package dev.jade;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import javax.sound.sampled.*;
import javazoom.spi.mpeg.sampled.file.MpegAudioFileReader;
import javazoom.spi.mpeg.sampled.convert.MpegFormatConversionProvider;
import org.jflac.sound.spi.FlacAudioFileReader;
import org.jflac.sound.spi.FlacFormatConversionProvider;

final class AudioFiles {
    static final Set<String> types = Set.of("wav","wave","flac","mp3","ogg","aif","aiff","au");
    static String extension(String filename) {
        return filename.substring(filename.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
    }
    static boolean supported(String filename) { return types.contains(extension(filename)); }

    static AudioInputStream pcm(Path path) throws IOException {
        String type = extension(path.toString());
        try {
            AudioInputStream input = switch (type) {
                case "mp3" -> new MpegAudioFileReader().getAudioInputStream(path.toFile());
                case "flac" -> new FlacAudioFileReader().getAudioInputStream(path.toFile());
                default -> AudioSystem.getAudioInputStream(path.toFile());
            };
            try {
                AudioFormat format = input.getFormat();
                if (format.getChannels() < 1 || format.getChannels() > 2)
                    throw new IOException("请选择单声道或立体声文件");
                AudioFormat target = new AudioFormat(format.getSampleRate(),16,format.getChannels(),true,false);
                if (type.equals("mp3")) return new MpegFormatConversionProvider().getAudioInputStream(target,input);
                if (type.equals("flac")) input = new FlacFormatConversionProvider()
                        .getAudioInputStream(AudioFormat.Encoding.PCM_SIGNED,input);
                return AudioSystem.getAudioInputStream(target,input);
            } catch (Exception ex) { input.close(); throw ex; }
        } catch (UnsupportedAudioFileException | IllegalArgumentException ex) { throw new IOException("无法解码音频：" + path.getFileName(),ex); }
    }
}
