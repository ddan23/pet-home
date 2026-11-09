package com.hnist.service;

import com.hnist.entity.Pet;
import com.hnist.entity.PetAllergy;
import com.hnist.entity.PetCarePlan;
import com.hnist.entity.PetDewormingRecord;
import com.hnist.entity.PetVaccination;
import com.hnist.entity.PetWeightRecord;

import java.util.List;

/** 宠物健康数据查询服务。 */
public interface PetHealthQueryService {

    // 返回 null 表示宠物不存在、已被删除或不属于该 userId；调用方应据此拒绝请求，不要退回按 petId 直接查子档案。
    Pet findOwnedPet(Integer userId, Integer petId);

    // 下面几个方法只按 petId 取数、不做归属校验：必须在 findOwnedPet 校验通过后再调用，否则会跨用户读到健康记录。
    List<PetWeightRecord> listWeightRecords(Integer petId);

    List<PetVaccination> listVaccinations(Integer petId);

    List<PetDewormingRecord> listDewormings(Integer petId);

    List<PetAllergy> listAllergies(Integer petId);

    List<PetCarePlan> listPendingCarePlans(Integer petId);
}
