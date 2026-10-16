package com.xiafan.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiafan.agent.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Splits an Apple Live Photo HEIC into a still JPEG and (when the HEIC actually carries one) the
 * short embedded video.
 *
 * <p>A plain HEIC still stores the picture as tiled image items; those tiles are NOT video frames.
 * A real Live Photo carries its motion as a timed HEVC track (multiple frames). We therefore only
 * produce a video when such a timed track is present, and extract the cover via ffmpeg's native
 * HEIF primary image (so tiled covers are assembled instead of yielding a black tile).
 */
@Service
public class HeicService {

    private static final Logger log = LoggerFactory.getLogger(HeicService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** ffmpeg/ffprobe binary paths, overridable for machines where they aren't on PATH. */
    @Value("${app.ffmpeg.path:ffmpeg}")
    private String ffmpegPath;
    @Value("${app.ffmpeg.probe-path:ffprobe}")
    private String ffprobePath;

    private record StreamInfo(int index, int width, int height, int nbFrames) {
    }

    public record HeicExtraction(byte[] stillJpeg, byte[] videoMp4) {
        public boolean isLive() {
            return videoMp4 != null && videoMp4.length > 0;
        }
    }

    public HeicExtraction extract(byte[] heic) {
        Path dir = null;
        try {
            dir = Files.createTempDirectory("heic-extract-");
            Path in = dir.resolve("input.heic");
            Files.write(in, heic);
            log.info("HEIC upload: {} bytes, magic={}", heic.length, magicHex(heic));
            if (!looksLikeHeif(heic)) {
                throw new BusinessException(400, "上传的文件不是有效的 HEIC/HEIF（请通过 iOS「文件」App 选择原始 .heic 文件，而不是从相册选择）");
            }

            List<StreamInfo> streams = probeStreams(in);
            if (streams.isEmpty()) {
                throw new BusinessException(400, "HEIC 解码失败");
            }

            byte[] stillJpeg = extractStill(in, dir);
            StreamInfo videoTrack = findVideoTrack(streams);
            byte[] video = videoTrack == null ? null : extractVideo(in, dir, videoTrack);
            return new HeicExtraction(stillJpeg, video);
        } catch (IOException e) {
            log.warn("HEIC extraction failed: {}", e.getMessage());
            throw new BusinessException(500, "HEIC 处理失败");
        } finally {
            if (dir != null) {
                deleteRecursively(dir);
            }
        }
    }

    private List<StreamInfo> probeStreams(Path in) {
        byte[] json = runCommand(List.of(
                ffprobePath, "-v", "error",
                "-show_entries", "stream=index,codec_type,width,height,nb_frames",
                "-of", "json", in.toString()));
        if (json == null) {
            return List.of();
        }
        try {
            List<StreamInfo> streams = new ArrayList<>();
            JsonNode arr = MAPPER.readTree(json).path("streams");
            if (!arr.isArray()) {
                return List.of();
            }
            for (JsonNode s : arr) {
                if ("video".equals(s.path("codec_type").asText())) {
                    int width = s.path("width").asInt(0);
                    int height = s.path("height").asInt(0);
                    int nb = s.path("nb_frames").asInt(1);
                    if (width > 0 && height > 0) {
                        streams.add(new StreamInfo(s.path("index").asInt(), width, height, nb));
                    }
                }
            }
            return streams;
        } catch (IOException e) {
            log.warn("ffprobe output parse failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** A real Live Photo carries its video as a timed HEVC track (nb_frames &gt; 1); tiled stills
     *  expose only single-frame image items. Returns the video track, or null when there is none. */
    private StreamInfo findVideoTrack(List<StreamInfo> streams) {
        return streams.stream()
                .filter(s -> s.nbFrames() > 1)
                .max(Comparator.comparingInt(StreamInfo::nbFrames))
                .orElse(null);
    }

    /** Extracts the cover still via ffmpeg's native HEIF primary image. The cover is stored as
     *  tiled image items, so mapping a single stream index yields only one tile (a black/partial
     *  image); omitting -map lets ffmpeg assemble the full picture. */
    private byte[] extractStill(Path in, Path dir) {
        Path out = dir.resolve("still.jpg");
        if (runCommand(List.of(ffmpegPath, "-v", "error", "-y", "-i", in.toString(),
                "-frames:v", "1", "-q:v", "2", out.toString())) == null) {
            return null;
        }
        return readIfExists(out);
    }

    private byte[] extractVideo(Path in, Path dir, StreamInfo track) {
        Path out = dir.resolve("video.mp4");
        if (runCommand(List.of(ffmpegPath, "-v", "error", "-y", "-i", in.toString(),
                "-map", "0:v:" + track.index(),
                "-c:v", "libx264", "-pix_fmt", "yuv420p", "-movflags", "+faststart",
                out.toString())) == null) {
            return null;
        }
        return readIfExists(out);
    }

    private byte[] readIfExists(Path file) {
        try {
            return Files.exists(file) ? Files.readAllBytes(file) : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** Runs a command, returning its merged stdout/stderr (stderr is kept quiet via -v error). */
    private byte[] runCommand(List<String> cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            byte[] output = p.getInputStream().readAllBytes();
            if (!p.waitFor(120, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                log.warn("ffmpeg timed out: {}", cmd);
                return null;
            }
            if (p.exitValue() != 0) {
                if (p.exitValue() == 137) {
                    log.error("ffmpeg was killed (exit 137, likely OOM). Raise the container memory "
                            + "(docker run -m / compose mem_limit).");
                }
                log.warn("ffmpeg exited {}: {}\n{}", p.exitValue(), cmd, new String(output));
                return null;
            }
            return output;
        } catch (IOException e) {
            if (executableNotFound(e)) {
                throw new BusinessException(500, "服务器未安装 " + cmd.get(0) + "（处理实况照片需要 ffmpeg/ffprobe），请安装后重试");
            }
            log.warn("ffmpeg command failed: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("ffmpeg command failed: {}", e.getMessage());
            return null;
        }
    }

    /** True when the executable itself is missing (not a decode failure). */
    private static boolean executableNotFound(IOException e) {
        String m = e.getMessage();
        return m != null && (m.contains("error=2") || m.contains("No such file or directory")
                || m.contains("CreateProcess error=2"));
    }

    /** ISO BMFF files (HEIC/HEIF) start with a "ftyp" box at byte 4. */
    private static boolean looksLikeHeif(byte[] b) {
        return b != null && b.length >= 12
                && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p';
    }

    private static String magicHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        int n = b == null ? 0 : Math.min(b.length, 16);
        for (int i = 0; i < n; i++) {
            sb.append(String.format("%02x ", b[i] & 0xff));
        }
        return sb.toString().trim();
    }

    private static void deleteRecursively(Path dir) {
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
