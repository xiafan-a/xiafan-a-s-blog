package com.xiafan.agent.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiafan.agent.entity.Image;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

public interface ImageRepository extends BaseMapper<Image> {

    default Image insert(String imageName, String originalName, int fileSize, String contentType,
                         Integer width, Integer height) {
        Image image = new Image();
        image.setImageName(imageName);
        image.setOriginalName(originalName);
        image.setFileSize(fileSize);
        image.setContentType(contentType);
        image.setWidth(width);
        image.setHeight(height);
        insert(image);
        return findById(image.getId()).orElseThrow();
    }

    default Optional<Image> findById(int id) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<Image>()
                .eq(Image::getId, id)
                .eq(Image::getIsDeleted, 0)));
    }

    default Optional<Image> findByImageName(String imageName) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<Image>()
                .eq(Image::getImageName, imageName)
                .eq(Image::getIsDeleted, 0)));
    }

    default List<Image> findAll(int skip, int limit) {
        return selectList(new LambdaQueryWrapper<Image>()
                .eq(Image::getIsDeleted, 0)
                .orderByDesc(Image::getCreatedAt)
                .last("LIMIT " + limit + " OFFSET " + skip));
    }

    @Update("""
            UPDATE images
            SET is_deleted = 1, updated_at = now()
            WHERE id = #{id} AND is_deleted = 0
            """)
    int softDelete(@Param("id") int id);
}
