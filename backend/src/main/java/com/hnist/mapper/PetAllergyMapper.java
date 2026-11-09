package com.hnist.mapper;

import com.hnist.entity.PetAllergy;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 宠物过敏原数据访问。 */
public interface PetAllergyMapper {

    List<PetAllergy> selectByPetId(@Param("petId") Integer petId);

    /** INSERT IGNORE：同一 pet_id + allergen_code 重复添加时静默忽略，返回 0。 */
    int insert(PetAllergy allergy);

    int deleteByPetIds(@Param("petIds") List<Integer> petIds);
}
