package com.hnist.support;

import com.hnist.common.exception.BusinessException;
import com.hnist.domain.PetHealthContext;
import com.hnist.entity.Pet;
import com.hnist.entity.PetAllergy;
import com.hnist.entity.PetCarePlan;
import com.hnist.entity.PetDewormingRecord;
import com.hnist.entity.PetVaccination;
import com.hnist.entity.PetWeightRecord;
import com.hnist.service.PetHealthQueryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 服务端宠物健康上下文组装器：汇聚档案、过敏原与近期健康记录，供 AI 提示词与工具调用消费。 */
@Service
public class PetHealthContextAssembler {

    /** 组装上下文时最多携带的体重记录条数，防止超长历史撑爆提示词。 */
    private static final int MAX_WEIGHT_RECORDS = 20;
    private final PetHealthQueryService petHealthQueryService;
    private final ObjectMapper objectMapper;

    public PetHealthContextAssembler(PetHealthQueryService petHealthQueryService,
                                     ObjectMapper objectMapper) {
        this.petHealthQueryService = petHealthQueryService;
        this.objectMapper = objectMapper;
    }

    /** 组装 AI 上下文：字段一律 null 安全化，体重记录仅取最近 20 条，过敏原最多 20 个。 */
    public PetHealthContext assemble(Pet pet) {
        PetHealthContext context = new PetHealthContext();
        context.setName(pet.getName());
        context.setSpecies(pet.getSpecies());
        context.setBreed(pet.getBreed());
        context.setGender(pet.getGender());
        context.setBirthDate(text(pet.getBirthDate()));
        context.setNeutered(Boolean.TRUE.equals(pet.getNeutered()));
        context.setWeight(pet.getWeight());
        context.setMedicalHistory(pet.getMedicalNotes());
        context.setAllergies(petHealthQueryService.listAllergies(pet.getId()).stream()
                .map(PetAllergy::getAllergenCode)
                .limit(20)
                .toList());
        context.setWeightRecords(weightRecords(pet.getId()));
        context.setVaccines(vaccinations(pet.getId()));
        context.setDewormings(dewormings(pet.getId()));
        context.setCarePlans(carePlans(pet.getId()));
        return context;
    }

    /**
     * 构建结构化快照（工具调用返回值）：LinkedHashMap 保证字段顺序稳定；
     * 体重变化需至少 2 条记录才计算，不足时省略该键，日期缺失/未来出生均按未记录处理。
     */
    public Map<String, Object> buildStructuredSnapshot(Pet pet) {
        List<PetWeightRecord> weights = petHealthQueryService.listWeightRecords(pet.getId());
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("name", pet.getName());
        snapshot.put("species", pet.getSpecies());
        snapshot.put("breed", text(pet.getBreed()));
        snapshot.put("gender", text(pet.getGender()));
        snapshot.put("birthDate", text(pet.getBirthDate()));
        snapshot.put("age", ageDescription(pet.getBirthDate()));
        snapshot.put("ageMonths", ageMonths(pet.getBirthDate()));
        snapshot.put("neutered", Boolean.TRUE.equals(pet.getNeutered()));
        snapshot.put("currentWeight", pet.getWeight());
        snapshot.put("latestWeightRecord", latestWeight(weights));
        snapshot.put("weightChange", weightChange(weights));
        snapshot.put("daysSinceWeightRecord", daysSinceLatestWeight(weights));
        snapshot.put("medicalHistory", text(pet.getMedicalNotes()));
        snapshot.put("allergies", petHealthQueryService.listAllergies(pet.getId()).stream()
                .map(PetAllergy::getAllergenCode)
                .toList());
        snapshot.put("weightRecords", weightRecords(pet.getId()));
        snapshot.put("vaccines", vaccinations(pet.getId()));
        snapshot.put("dewormings", dewormings(pet.getId()));
        snapshot.put("carePlans", carePlans(pet.getId()));
        return snapshot;
    }

    /** 序列化失败抛 {@link BusinessException}(500)，不让原始 JsonProcessingException 泄漏到上层。 */
    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "宠物健康上下文序列化失败");
        }
    }

    private List<Map<String, Object>> weightRecords(Integer petId) {
        List<PetWeightRecord> records = petHealthQueryService.listWeightRecords(petId);
        int start = Math.max(0, records.size() - MAX_WEIGHT_RECORDS);
        List<Map<String, Object>> result = new ArrayList<>();
        for (int index = start; index < records.size(); index++) {
            PetWeightRecord record = records.get(index);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", text(record.getRecordedOn()));
            item.put("weight", record.getWeight());
            item.put("note", text(record.getNote()));
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> vaccinations(Integer petId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (PetVaccination item : petHealthQueryService.listVaccinations(petId)) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("name", text(item.getVaccineName()));
            value.put("date", text(item.getVaccinatedOn()));
            value.put("nextDate", text(item.getNextDueOn()));
            value.put("batchNo", text(item.getBatchNo()));
            value.put("vetName", text(item.getVetName()));
            result.add(value);
        }
        return result;
    }

    private List<Map<String, Object>> dewormings(Integer petId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (PetDewormingRecord item : petHealthQueryService.listDewormings(petId)) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("product", text(item.getProductName()));
            value.put("date", text(item.getTreatedOn()));
            value.put("nextDate", text(item.getNextDueOn()));
            value.put("note", text(item.getNote()));
            result.add(value);
        }
        return result;
    }

    private List<Map<String, Object>> carePlans(Integer petId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (PetCarePlan item : petHealthQueryService.listPendingCarePlans(petId)) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("type", text(item.getCareType()));
            value.put("title", text(item.getTitle()));
            value.put("plannedOn", text(item.getPlannedOn()));
            value.put("note", text(item.getNote()));
            result.add(value);
        }
        return result;
    }

    private Map<String, Object> latestWeight(List<PetWeightRecord> records) {
        if (records.isEmpty()) {
            return new LinkedHashMap<>();
        }
        PetWeightRecord latest = records.get(records.size() - 1);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", text(latest.getRecordedOn()));
        result.put("weight", latest.getWeight());
        return result;
    }

    private Map<String, Object> weightChange(List<PetWeightRecord> records) {
        if (records.size() < 2) {
            return Map.of();
        }
        BigDecimal first = records.get(records.size() - 2).getWeight();
        BigDecimal latest = records.get(records.size() - 1).getWeight();
        if (first == null || latest == null) {
            return Map.of();
        }
        return Map.of(
                "fromWeight", first,
                "toWeight", latest,
                "difference", latest.subtract(first)
        );
    }

    private Long daysSinceLatestWeight(List<PetWeightRecord> records) {
        if (records.isEmpty() || records.get(records.size() - 1).getRecordedOn() == null) {
            return null;
        }
        return ChronoUnit.DAYS.between(
                records.get(records.size() - 1).getRecordedOn(), LocalDate.now());
    }

    private String ageDescription(LocalDate birthDate) {
        if (birthDate == null || birthDate.isAfter(LocalDate.now())) {
            return "未记录";
        }
        Period period = Period.between(birthDate, LocalDate.now());
        return period.getYears() + "岁" + period.getMonths() + "个月";
    }

    private int ageMonths(LocalDate birthDate) {
        if (birthDate == null || birthDate.isAfter(LocalDate.now())) {
            return 0;
        }
        Period period = Period.between(birthDate, LocalDate.now());
        return period.getYears() * 12 + period.getMonths();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
