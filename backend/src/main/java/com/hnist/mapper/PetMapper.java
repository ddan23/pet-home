package com.hnist.mapper;

import com.hnist.entity.Pet;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 宠物档案数据访问。 */
public interface PetMapper {

    /** 只返回 status = 'normal' 且 deleted_at IS NULL 的宠物，软删除的档案不可见。 */
    List<Pet> selectByUserId(@Param("userId") Integer userId);

    Pet selectByIdAndUserId(@Param("id") Integer id, @Param("userId") Integer userId);

    int countByUserId(@Param("userId") Integer userId);

    int insert(Pet pet);

    int update(Pet pet);

    int deleteByUserId(@Param("userId") Integer userId);

    /** 软删除：置 status = 'deleted' 和 deleted_at，非物理删除；返回 0 表示不存在或已删。 */
    int deleteByIdAndUserId(@Param("id") Integer id,
                            @Param("userId") Integer userId);
}
