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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Splits an Apple Live Photo HEIC into a still JPEG and (when present) the short embedded video.
 *
 * <p>ffmpeg reads the HEIC still fine, but it exposes a Live Photo's video as many separate
 * single-frame HEVC streams rather than one timed stream. We therefore reconstruct the video by
 * re-muxing those frames into an MP4. Uses the ffmpeg/ffprobe binaries (must be on the server PATH).
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

    /** Fewer frames than this means there is no real motion sequence (just a plain HEIC still). */
    private static final int MIN_VIDEO_FRAMES = 5;
    /** Live Photos are ~3 s; clamp the reconstructed frame rate to a sane range. */
    private static final double LIVE_PHOTO_SECONDS = 3.0;

    private record StreamInfo(int index, int width, int height) {
        long pixels() {
            return (long) width * height;
        }
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

            StreamInfo still = pickStill(streams);
            byte[] stillJpeg = extractStill(in, dir, still);

            List<StreamInfo> videoStreams = pickVideoStreams(streams, still);
            byte[] video = videoStreams.isEmpty() ? null : buildVideo(in, dir, videoStreams);
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
                "-show_entries", "stream=index,codec_type,width,height",
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
                    if (width > 0 && height > 0) {
                        streams.add(new StreamInfo(s.path("index").asInt(), width, height));
                    }
                }
            }
            return streams;
        } catch (IOException e) {
            log.warn("ffprobe output parse failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** The cover still is the highest-resolution frame. */
    private StreamInfo pickStill(List<StreamInfo> streams) {
        return streams.stream().max(Comparator.comparingLong(StreamInfo::pixels)).orElseThrow();
    }

    /** The video is the most frequent frame size (a real sequence), excluding the still's size. */
    private List<StreamInfo> pickVideoStreams(List<StreamInfo> streams, StreamInfo still) {
        Map<String, List<StreamInfo>> bySize = new LinkedHashMap<>();
        for (StreamInfo s : streams) {
            bySize.computeIfAbsent(s.width() + "x" + s.height(), k -> new ArrayList<>()).add(s);
        }
        String stillKey = still.width() + "x" + still.height();
        List<StreamInfo> best = List.of();
        for (Map.Entry<String, List<StreamInfo>> e : bySize.entrySet()) {
            if (e.getKey().equals(stillKey)) {
                continue;
            }
            if (e.getValue().size() > best.size()) {
                best = e.getValue();
            }
        }
        return best.size() >= MIN_VIDEO_FRAMES ? best : List.of();
    }

    private byte[] extractStill(Path in, Path dir, StreamInfo still) {
        Path out = dir.resolve("still.jpg");
        if (runCommand(List.of(ffmpegPath, "-v", "error", "-y", "-i", in.toString(),
                "-map", "0:v:" + still.index(), "-frames:v", "1", "-q:v", "2", out.toString())) == null) {
            return null;
        }
        return readIfExists(out);
    }

    /** Re-muxes the selected video frames (in stream order) into a single MP4. */
    private byte[] buildVideo(Path in, Path dir, List<StreamInfo> videoStreams) {
        // Decode one frame per selected stream. A single bulk "-map 0:v" pass opens every HEVC
        // decoder at once and gets OOM-killed (exit 137) on memory-limited containers, so we decode
        // one stream at a time to keep peak memory low.
        int k = 0;
        for (StreamInfo s : videoStreams) {
            Path frame = dir.resolve(String.format("seq_%04d.jpg", ++k));
            if (runCommand(List.of(ffmpegPath, "-v", "error", "-y", "-i", in.toString(),
                    "-map", "0:v:" + s.index(), "-frames:v", "1", "-q:v", "2",
                    frame.toString())) == null) {
                return null;
            }
        }
        int fps = (int) Math.round(Math.max(10.0, Math.min(30.0, videoStreams.size() / LIVE_PHOTO_SECONDS)));
        Path out = dir.resolve("video.mp4");
        if (runCommand(List.of(ffmpegPath, "-v", "error", "-y", "-framerate", String.valueOf(fps),
                "-i", dir.resolve("seq_%04d.jpg").toString(),
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
                    log.error("ffmpeg was killed (exit 137, likely OOM). Container memory limit is too low "
                            + "to decode this HEIC's many HEVC streams. Raise the container memory "
                            + "(docker run -m / compose mem_limit) or decode frames one at a time.");
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
