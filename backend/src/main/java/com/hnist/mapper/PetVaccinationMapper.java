package com.hnist.mapper;

import com.hnist.entity.PetVaccination;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 宠物疫苗记录数据访问。 */
public interface PetVaccinationMapper {

    List<PetVaccination> selectByPetId(@Param("petId") Integer petId);

    int insert(PetVaccination vaccination);

    int deleteByPetId(@Param("petId") Integer petId);
}
