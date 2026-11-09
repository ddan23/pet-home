package com.hnist.mapper;

import com.hnist.entity.PetDewormingRecord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 宠物驱虫记录数据访问。 */
public interface PetDewormingRecordMapper {

    List<PetDewormingRecord> selectByPetId(@Param("petId") Integer petId);

    int insert(PetDewormingRecord record);

    int deleteByPetId(@Param("petId") Integer petId);
}
