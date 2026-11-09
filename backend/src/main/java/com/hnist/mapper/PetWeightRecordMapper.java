package com.hnist.mapper;

import com.hnist.entity.PetWeightRecord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 宠物体重记录数据访问。 */
public interface PetWeightRecordMapper {

    List<PetWeightRecord> selectByPetId(@Param("petId") Integer petId);

    int insert(PetWeightRecord record);

    int deleteByPetId(@Param("petId") Integer petId);
}
