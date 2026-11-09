package com.hnist.service.impl;

import com.hnist.mapper.PetAllergyMapper;
import com.hnist.mapper.PetCarePlanMapper;
import com.hnist.mapper.PetDewormingRecordMapper;
import com.hnist.mapper.PetMapper;
import com.hnist.mapper.PetVaccinationMapper;
import com.hnist.mapper.PetWeightRecordMapper;
import com.hnist.entity.Pet;
import com.hnist.entity.PetAllergy;
import com.hnist.entity.PetCarePlan;
import com.hnist.entity.PetDewormingRecord;
import com.hnist.entity.PetVaccination;
import com.hnist.entity.PetWeightRecord;
import com.hnist.service.PetHealthQueryService;
import org.springframework.stereotype.Service;

import java.util.List;

/** 宠物档案与健康子记录（体重、疫苗、驱虫、过敏、养护计划）的统一查询入口，子表查询不做归属校验，必须先经 findOwnedPet。 */
@Service
public class PetHealthQueryServiceImpl implements PetHealthQueryService {

    private final PetMapper petMapper;
    private final PetWeightRecordMapper weightRecordMapper;
    private final PetVaccinationMapper vaccinationMapper;
    private final PetDewormingRecordMapper dewormingRecordMapper;
    private final PetAllergyMapper allergyMapper;
    private final PetCarePlanMapper carePlanMapper;

    public PetHealthQueryServiceImpl(PetMapper petMapper,
                                     PetWeightRecordMapper weightRecordMapper,
                                     PetVaccinationMapper vaccinationMapper,
                                     PetDewormingRecordMapper dewormingRecordMapper,
                                     PetAllergyMapper allergyMapper,
                                     PetCarePlanMapper carePlanMapper) {
        this.petMapper = petMapper;
        this.weightRecordMapper = weightRecordMapper;
        this.vaccinationMapper = vaccinationMapper;
        this.dewormingRecordMapper = dewormingRecordMapper;
        this.allergyMapper = allergyMapper;
        this.carePlanMapper = carePlanMapper;
    }

    @Override
    public Pet findOwnedPet(Integer userId, Integer petId) {
        // 归属校验的唯一入口：selectByIdAndUserId 同时限定 id 和 user_id，宠物不存在、已软删或属于他人时都只拿到 null，由调用方统一转成“档案不存在”。
        return petMapper.selectByIdAndUserId(petId, userId);
    }

    @Override
    public List<PetWeightRecord> listWeightRecords(Integer petId) {
        // 以下子档案查询只按 petId 过滤，自身不做归属校验：调用方必须先经 findOwnedPet 确认宠物属于当前用户，否则会越权读到他人的健康记录。
        return weightRecordMapper.selectByPetId(petId);
    }

    @Override
    public List<PetVaccination> listVaccinations(Integer petId) {
        return vaccinationMapper.selectByPetId(petId);
    }

    @Override
    public List<PetDewormingRecord> listDewormings(Integer petId) {
        return dewormingRecordMapper.selectByPetId(petId);
    }

    @Override
    public List<PetAllergy> listAllergies(Integer petId) {
        return allergyMapper.selectByPetId(petId);
    }

    @Override
    public List<PetCarePlan> listPendingCarePlans(Integer petId) {
        // 只取 status='pending'：其它状态的计划不进入 AI 上下文，避免已处理的待办被当成当前养护建议的依据。
        return carePlanMapper.selectPendingByPetId(petId);
    }
}
