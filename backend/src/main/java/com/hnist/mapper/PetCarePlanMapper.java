package com.hnist.mapper;

import com.hnist.entity.PetCarePlan;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 宠物照护计划数据访问。 */
public interface PetCarePlanMapper {

    /** 只返回 status = 'pending' 的计划，按 planned_on 升序；已完成/取消的计划不出现。 */
    List<PetCarePlan> selectPendingByPetId(@Param("petId") Integer petId);

    int insert(PetCarePlan plan);

    int deleteByPetId(@Param("petId") Integer petId);
}
