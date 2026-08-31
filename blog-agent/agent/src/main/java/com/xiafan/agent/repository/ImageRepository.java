package com.xiafan.agent.repository;

import com.xiafan.agent.entity.Image;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

@Repository
public class ImageRepository extends BaseRepository {

    public ImageRepository(DataSource dataSource) {
        super(dataSource);
    }

    public static final RowMapper<Image> ROW_MAPPER = (rs, rowNum) -> {
        Image i = new Image();
        i.setId(rs.getInt("id"));
        i.setImageName(rs.getString("image_name"));
        i.setOriginalName(rs.getString("original_name"));
        i.setFileSize(rs.getInt("file_size"));
        i.setContentType(rs.getString("content_type"));
        int width = rs.getInt("width");
        i.setWidth(rs.wasNull() ? null : width);
        int height = rs.getInt("height");
        i.setHeight(rs.wasNull() ? null : height);
        i.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        i.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        i.setIsDeleted(rs.getInt("is_deleted"));
        return i;
    };

    public Image insert(String imageName, String originalName, int fileSize, String contentType,
                        Integer width, Integer height) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO images (image_name, original_name, file_size, content_type, width, height, "
                            + "created_at, updated_at, is_deleted) "
                            + "VALUES (?, ?, ?, ?, ?, ?, now(), now(), 0)",
                    new String[]{"id"});
            ps.setString(1, imageName);
            ps.setString(2, originalName);
            ps.setInt(3, fileSize);
            ps.setString(4, contentType);
            if (width == null) {
                ps.setNull(5, java.sql.Types.INTEGER);
            } else {
                ps.setInt(5, width);
            }
            if (height == null) {
                ps.setNull(6, java.sql.Types.INTEGER);
            } else {
                ps.setInt(6, height);
            }
            return ps;
        }, keyHolder);
        return findById(keyHolder.getKey().intValue()).orElseThrow();
    }

    public Optional<Image> findById(int id) {
        return jdbc.query("SELECT * FROM images WHERE id = ? AND is_deleted = 0", ROW_MAPPER, id)
                .stream().findFirst();
    }

    public Optional<Image> findByImageName(String imageName) {
        return jdbc.query("SELECT * FROM images WHERE image_name = ? AND is_deleted = 0", ROW_MAPPER, imageName)
                .stream().findFirst();
    }

    public List<Image> findAll(int skip, int limit) {
        return jdbc.query("SELECT * FROM images WHERE is_deleted = 0 ORDER BY created_at DESC LIMIT ? OFFSET ?",
                ROW_MAPPER, limit, skip);
    }

    public int softDelete(int id) {
        return jdbc.update("UPDATE images SET is_deleted = 1, updated_at = now() WHERE id = ? AND is_deleted = 0", id);
    }
}