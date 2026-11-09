package com.hnist.service.impl;

import com.hnist.common.exception.BusinessException;
import com.hnist.mapper.PetCarePlanMapper;
import com.hnist.mapper.PetDewormingRecordMapper;
import com.hnist.mapper.PetAllergyMapper;
import com.hnist.mapper.PetMapper;
import com.hnist.mapper.PetVaccinationMapper;
import com.hnist.mapper.PetWeightRecordMapper;
import com.hnist.mapper.OrderAppointmentSlotMapper;
import com.hnist.entity.Pet;
import com.hnist.entity.PetCarePlan;
import com.hnist.entity.PetDewormingRecord;
import com.hnist.entity.PetVaccination;
import com.hnist.entity.PetWeightRecord;
import com.hnist.entity.PetAllergy;
import com.hnist.service.PetService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 宠物健康档案服务实现。 */
@Service
public class PetServiceImpl implements PetService {

    // 过敏原条数设上限：过敏史会整段进入 AI 健康上下文，条数不设限会让提示词无界增长。
    private static final int MAX_ALLERGY_COUNT = 20;
    // allergen_code 在库里是 varchar(50)，长度上限与字段宽度对齐，避免超长值入库被静默截断后与前端回显不一致。
    private static final int MAX_ALLERGY_NAME_LENGTH = 50;
    private static final String CARE_TYPE_VACCINE = "vaccine";
    private static final String CARE_TYPE_DEWORMING = "deworming";
    private static final int MAX_CARE_PLAN_TITLE_LENGTH = 100;
    private static final int MAX_CARE_PLAN_NOTE_LENGTH = 500;
    private static final String SPECIES_CAT = "猫";
    private static final String SPECIES_DOG = "狗";
    // “今天”统一按东八区判定：服务器时区不一致时，体重日期与照护计划日期的过去/未来判断必须落在同一口径。
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final PetMapper petMapper;
    private final PetWeightRecordMapper weightRecordMapper;
    private final PetVaccinationMapper vaccinationMapper;
    private final PetDewormingRecordMapper dewormingRecordMapper;
    private final PetAllergyMapper petAllergyMapper;
    private final PetCarePlanMapper petCarePlanMapper;
    private final OrderAppointmentSlotMapper orderAppointmentSlotMapper;
    private final ObjectMapper objectMapper;

    public PetServiceImpl(PetMapper petMapper, PetWeightRecordMapper weightRecordMapper,
                          PetVaccinationMapper vaccinationMapper,
                          PetDewormingRecordMapper dewormingRecordMapper,
                          PetAllergyMapper petAllergyMapper,
                          PetCarePlanMapper petCarePlanMapper,
                          OrderAppointmentSlotMapper orderAppointmentSlotMapper,
                          ObjectMapper objectMapper) {
        this.petMapper = petMapper;
        this.weightRecordMapper = weightRecordMapper;
        this.vaccinationMapper = vaccinationMapper;
        this.dewormingRecordMapper = dewormingRecordMapper;
        this.petAllergyMapper = petAllergyMapper;
        this.petCarePlanMapper = petCarePlanMapper;
        this.orderAppointmentSlotMapper = orderAppointmentSlotMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String getPetsJson(Integer userId) {
        // 读取边界只有 userId：selectByUserId 已限定 user_id + status='normal' + deleted_at IS NULL，软删档案不会回流到前端。
        List<Pet> pets = petMapper.selectByUserId(userId);
        try {
            return objectMapper.writeValueAsString(toJsonList(pets));
        } catch (JsonProcessingException exception) {
            // 序列化失败直接抛错：返回半份档案会让前端下次保存时按残缺数据整份覆盖，宁可让请求失败。
            throw new BusinessException(500, "宠物档案序列化失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void savePetsJson(Integer userId, String petsJson) {
        JsonNode root = readArray(petsJson);
        // 保存以整份 JSON 为准：先按当前 userId 读出在用档案，请求里没出现的 id 在方法末尾会被当作“用户删除”处理。
        List<Pet> existingPets = petMapper.selectByUserId(userId);
        Map<Integer, Pet> existingPetMap = new LinkedHashMap<>();
        for (Pet existingPet : existingPets) {
            existingPetMap.put(existingPet.getId(), existingPet);
        }
        Set<Integer> retainedPetIds = new HashSet<>();
        for (JsonNode item : root) {
            // 归属校验靠“查不到就当新建”：existingPetMap 只含当前用户的宠物，提交他人 petId 时命中不了，走新建分支而不是改写他人档案。
            Integer submittedId = integer(item, "id");
            Pet pet = submittedId == null ? null : existingPetMap.get(submittedId);
            // 集合必须在 clearPetChildren 之前读取：子表被删空后旧键不再存在，历史日期的计划会被误判成“新计划”而被拒。
            Set<String> existingCarePlanKeys = pet == null
                    ? Set.of() : carePlanKeys(pet.getId());
            if (pet == null) {
                pet = new Pet();
                pet.setUserId(userId);
                fillPet(pet, item);
                petMapper.insert(pet);
            } else {
                clearPetChildren(pet.getId());
                fillPet(pet, item);
                petMapper.update(pet);
            }
            retainedPetIds.add(pet.getId());
            saveWeightRecords(pet.getId(), item.path("weightRecords"));
            saveVaccinations(pet.getId(), item.path("vaccines"));
            saveDewormingRecords(pet.getId(), item);
            saveAllergies(pet.getId(), item.path("allergies"));
            saveCarePlans(pet.getId(), item.path("carePlans"), existingCarePlanKeys);
        }
        for (Pet existingPet : existingPets) {
            if (!retainedPetIds.contains(existingPet.getId())) {
                // 删除前先查未来预约：countFutureBookingsByPet 统计该 petId 下 status='confirmed' 且未结束的 booking 订单，命中即抛错终止整次保存。
                if (orderAppointmentSlotMapper.countFutureBookingsByPet(
                        existingPet.getId(), userId) > 0) {
                    throw new BusinessException("该宠物存在未完成的未来预约，请先取消或完成预约后再删除");
                }
                // 宠物走软删（status='deleted' + deleted_at），历史订单和健康记录仍可追溯，只是不再出现在档案列表。
                petMapper.deleteByIdAndUserId(existingPet.getId(), userId);
            }
        }
    }

    private void fillPet(Pet pet, JsonNode item) {
        String name = text(item, "name", null);
        if (name == null || name.isBlank()) {
            throw new BusinessException("请填写宠物名字");
        }
        pet.setName(name.trim());
        pet.setSpecies(normalizeSpecies(
                text(item, "species", text(item, "type", null))));
        pet.setBreed(text(item, "breed", null));
        pet.setGender(text(item, "gender", null));
        pet.setColor(text(item, "color", null));
        pet.setBirthDate(date(item, "birthDate"));
        // neutered 缺省为 false：与 pets.neutered NOT NULL DEFAULT 0 对齐；也因此漏传该字段会把已绝育改回未绝育，前端必须回传原值。
        pet.setNeutered(item.path("neutered").asBoolean(false));
        // 体重单位为 kg（库里 decimal(6,2)）：null 表示未称重、允许留空；填了必须为正，0 或负数会被 WeightTrendPolicy 直接丢弃，存进去也无法参与趋势对比。
        BigDecimal weight = decimal(item, "weight");
        if (weight != null && weight.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("体重必须大于0");
        }
        pet.setWeight(weight);
        pet.setPhotoUrl(text(item, "photo", null));
        pet.setMedicalNotes(text(item, "medicalHistory", null));
    }

    private void clearPetChildren(Integer petId) {
        // 子档案整表替换而不是逐条 diff：五张子表都以 pet_id 为归属键，先 DELETE 再 INSERT 让重复提交收敛成同一份数据；删除在上层事务内，后续校验失败会一并回滚。
        weightRecordMapper.deleteByPetId(petId);
        vaccinationMapper.deleteByPetId(petId);
        dewormingRecordMapper.deleteByPetId(petId);
        petAllergyMapper.deleteByPetIds(List.of(petId));
        petCarePlanMapper.deleteByPetId(petId);
    }

    private List<Map<String, Object>> toJsonList(List<Pet> pets) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Pet pet : pets) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", pet.getId());
            item.put("name", pet.getName());
            item.put("species", pet.getSpecies());
            // type 与 species 同值输出：type 是早期约定的字段名，保留两个键，只读 type 的调用方不会因为改字段拿不到物种。
            item.put("type", pet.getSpecies());
            item.put("breed", pet.getBreed());
            item.put("gender", pet.getGender());
            item.put("color", pet.getColor());
            item.put("birthDate", dateText(pet.getBirthDate()));
            item.put("neutered", Boolean.TRUE.equals(pet.getNeutered()));
            item.put("weight", pet.getWeight());
            item.put("photo", pet.getPhotoUrl());
            item.put("medicalHistory", pet.getMedicalNotes());

            List<Map<String, Object>> weights = new ArrayList<>();
            // 子记录顺序由 mapper 的 ORDER BY recorded_on ASC 保证，这里不再排序；
            // 下游取数组末条作为最新记录（见 PetHealthContextAssembler.latestWeight）。
            for (PetWeightRecord record : weightRecordMapper.selectByPetId(pet.getId())) {
                Map<String, Object> weight = new LinkedHashMap<>();
                weight.put("weight", record.getWeight());
                weight.put("date", dateText(record.getRecordedOn()));
                weight.put("note", record.getNote());
                weights.add(weight);
            }
            item.put("weightRecords", weights);

            List<Map<String, Object>> vaccines = new ArrayList<>();
            for (PetVaccination vaccination : vaccinationMapper.selectByPetId(pet.getId())) {
                Map<String, Object> vaccine = new LinkedHashMap<>();
                vaccine.put("name", vaccination.getVaccineName());
                vaccine.put("date", dateText(vaccination.getVaccinatedOn()));
                vaccine.put("nextDate", dateText(vaccination.getNextDueOn()));
                vaccine.put("batchNo", vaccination.getBatchNo());
                vaccine.put("vetName", vaccination.getVetName());
                vaccines.add(vaccine);
            }
            item.put("vaccines", vaccines);

            List<PetDewormingRecord> dewormings = dewormingRecordMapper.selectByPetId(pet.getId());
            String nextDewormingDate = null;
            List<Map<String, Object>> dewormingRecords = new ArrayList<>();
            for (PetDewormingRecord deworming : dewormings) {
                Map<String, Object> record = new LinkedHashMap<>();
                record.put("productName", deworming.getProductName());
                record.put("date", dateText(deworming.getTreatedOn()));
                record.put("nextDate", dateText(deworming.getNextDueOn()));
                record.put("note", deworming.getNote());
                dewormingRecords.add(record);
            }
            item.put("dewormings", dewormingRecords);
            List<String> allergies = new ArrayList<>();
            for (PetAllergy allergy : petAllergyMapper.selectByPetId(pet.getId())) {
                allergies.add(allergy.getAllergenCode());
            }
            item.put("allergies", allergies);

            List<Map<String, Object>> carePlans = new ArrayList<>();
            // selectPendingByPetId 只取 status='pending'：其它状态的计划不回传，档案里只展示还需要处理的待办。
            for (PetCarePlan plan : petCarePlanMapper.selectPendingByPetId(pet.getId())) {
                Map<String, Object> carePlan = new LinkedHashMap<>();
                carePlan.put("careType", plan.getCareType());
                carePlan.put("title", plan.getTitle());
                carePlan.put("plannedOn", dateText(plan.getPlannedOn()));
                carePlan.put("note", plan.getNote());
                carePlans.add(carePlan);
                // 计划已按 planned_on 升序，第一个 deworming 待办就是最近一次驱虫；nextDewormingDate 只保留这一条，取到后不再覆盖。
                if (CARE_TYPE_DEWORMING.equals(plan.getCareType())
                        && nextDewormingDate == null) {
                    nextDewormingDate = dateText(plan.getPlannedOn());
                }
            }
            item.put("carePlans", carePlans);
            item.put("nextDewormingDate", nextDewormingDate);
            result.add(item);
        }
        return result;
    }

    private void saveWeightRecords(Integer petId, JsonNode records) {
        if (!records.isArray()) {
            return;
        }
        for (JsonNode node : records) {
            // 体重记录缺值直接报错而不是跳过：这一批记录刚被整表删除，静默丢弃会让用户的记录凭空消失。
            BigDecimal weight = decimal(node, "weight");
            LocalDate date = date(node, "date");
            if (weight == null || date == null) {
                throw new BusinessException("体重值和体重日期不能为空");
            }
            if (weight.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("体重必须大于0");
            }
            // 上界按东八区的今天：最新一条落在未来时 WeightTrendPolicy 会判定为异常数据并放弃整轮分析，趋势提示随之失效。
            if (date.isAfter(LocalDate.now(ZONE_ID))) {
                throw new BusinessException("体重日期不能晚于今天");
            }
            PetWeightRecord record = new PetWeightRecord();
            record.setPetId(petId);
            record.setWeight(weight);
            record.setRecordedOn(date);
            record.setNote(text(node, "note", null));
            weightRecordMapper.insert(record);
        }
    }

    private void saveVaccinations(Integer petId, JsonNode vaccines) {
        if (!vaccines.isArray()) {
            return;
        }
        for (JsonNode node : vaccines) {
            String name = text(node, "name", null);
            // 名称为空的行跳过而不报错：前端表格允许留空行，空行不代表用户填写意图，报错会让整份档案存不上。
            if (name == null || name.isBlank()) {
                continue;
            }
            PetVaccination vaccination = new PetVaccination();
            vaccination.setPetId(petId);
            vaccination.setVaccineName(name);
            vaccination.setVaccinatedOn(date(node, "date"));
            vaccination.setNextDueOn(date(node, "nextDate"));
            vaccination.setBatchNo(text(node, "batchNo", null));
            vaccination.setVetName(text(node, "vetName", null));
            vaccinationMapper.insert(vaccination);
        }
    }

    private void saveDewormingRecords(Integer petId, JsonNode petNode) {
        JsonNode records = petNode.path("dewormings");
        // 优先读 dewormings 数组；数组缺失时退回 saveLegacyDeworming，用 nextDewormingDate/lastDewormingDate 等散字段兜底，否则子表删空后驱虫历史无法重建。
        if (records.isArray()) {
            for (JsonNode node : records) {
                LocalDate treatedOn = date(node, "date");
                if (treatedOn == null) {
                    continue;
                }
                PetDewormingRecord record = new PetDewormingRecord();
                record.setPetId(petId);
                record.setProductName(text(node, "productName",
                        text(node, "name", "驱虫")));
                record.setTreatedOn(treatedOn);
                record.setNextDueOn(date(node, "nextDate"));
                record.setNote(text(node, "note", null));
                dewormingRecordMapper.insert(record);
            }
            return;
        }
        saveLegacyDeworming(petId, petNode);
    }

    private void saveLegacyDeworming(Integer petId, JsonNode petNode) {
        LocalDate nextDue = date(petNode, "nextDewormingDate");
        if (nextDue == null) {
            return;
        }
        PetDewormingRecord record = new PetDewormingRecord();
        record.setPetId(petId);
        record.setProductName(text(petNode, "dewormingProduct", "驱虫"));
        record.setTreatedOn(date(petNode, "lastDewormingDate"));
        record.setNextDueOn(nextDue);
        record.setNote(text(petNode, "dewormingNote", null));
        dewormingRecordMapper.insert(record);
    }

    private void saveCarePlans(Integer petId, JsonNode plans,
                               Set<String> existingCarePlanKeys) {
        if (!plans.isArray()) {
            return;
        }
        for (JsonNode item : plans) {
            String careType = text(item, "careType", null);
            String title = text(item, "title", null);
            LocalDate plannedOn = date(item, "plannedOn");
            // care_type 只允许 vaccine/deworming：其它值会让后续提醒和健康上下文里的分类对不上，直接拒绝。
            if (!CARE_TYPE_VACCINE.equals(careType) && !CARE_TYPE_DEWORMING.equals(careType)) {
                throw new BusinessException("照护类型不合法");
            }
            if (title == null || title.isBlank() || plannedOn == null) {
                throw new BusinessException("照护计划名称和日期不能为空");
            }
            String normalizedTitle = title.trim();
            String planKey = carePlanKey(careType, normalizedTitle, plannedOn);
            // 历史计划允许保留过去的日期：键（careType|title|plannedOn）在 existingCarePlanKeys 里说明是用户原有待办，只拦新增的过期计划。
            if (plannedOn.isBefore(LocalDate.now(ZONE_ID))
                    && !existingCarePlanKeys.contains(planKey)) {
                throw new BusinessException("新的照护计划日期不能早于今天");
            }
            if (title.length() > MAX_CARE_PLAN_TITLE_LENGTH) {
                throw new BusinessException("照护计划名称不能超过" + MAX_CARE_PLAN_TITLE_LENGTH + "个字符");
            }
            String note = text(item, "note", null);
            if (note != null && note.length() > MAX_CARE_PLAN_NOTE_LENGTH) {
                throw new BusinessException("照护计划备注不能超过" + MAX_CARE_PLAN_NOTE_LENGTH + "个字符");
            }
            PetCarePlan plan = new PetCarePlan();
            plan.setPetId(petId);
            plan.setCareType(careType);
            plan.setTitle(normalizedTitle);
            plan.setPlannedOn(plannedOn);
            plan.setNote(note);
            petCarePlanMapper.insert(plan);
        }
    }

    private void saveAllergies(Integer petId, JsonNode allergies) {
        if (!allergies.isArray()) {
            return;
        }
        int count = 0;
        for (JsonNode item : allergies) {
            // 元素可能是纯字符串，也可能是带 code/severity/note 的对象：两种形态都归一到过敏原名称，避免其中一种被当成空值丢掉。
            String code = item.isTextual() ? item.asText() : text(item, "code", null);
            if (code == null || code.isBlank()) {
                continue;
            }
            String allergenName = code.trim();
            if (allergenName.length() > MAX_ALLERGY_NAME_LENGTH) {
                throw new BusinessException("单个过敏原不能超过" + MAX_ALLERGY_NAME_LENGTH + "个字符");
            }
            if (count >= MAX_ALLERGY_COUNT) {
                throw new BusinessException("每只宠物最多记录" + MAX_ALLERGY_COUNT + "个过敏原");
            }
            // 一条条 insert 而不是批量：真正的去重由唯一键 uk_pet_allergen(pet_id, allergen_code) 配合 INSERT IGNORE 完成，
            // 重复过敏原不会插入第二行，count 仍按提交行数计。
            PetAllergy allergy = new PetAllergy();
            allergy.setPetId(petId);
            allergy.setAllergenCode(allergenName);
            allergy.setSeverity(item.isObject()
                    ? text(item, "severity", "unknown") : "unknown");
            allergy.setNote(item.isObject() ? text(item, "note", null) : null);
            petAllergyMapper.insert(allergy);
            count++;
        }
    }

    private JsonNode readArray(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createArrayNode();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            // 兼容“JSON 字符串再套一层 JSON 字符串”的提交：解包一层后再要求节点是数组，否则按格式错误拒绝。
            if (node.isTextual()) {
                node = objectMapper.readTree(node.asText());
            }
            if (!node.isArray()) {
                throw new BusinessException("宠物档案格式不正确");
            }
            return node;
        } catch (BusinessException exception) {
            throw exception;
        } catch (JsonProcessingException exception) {
            throw new BusinessException("宠物档案格式不正确");
        }
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank()
                ? fallback : value.asText();
    }

    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : null;
    }

    private LocalDate date(JsonNode node, String field) {
        String value = text(node, field, null);
        if (value == null) {
            return null;
        }
        try {
            // 解析失败返回 null 而不是抛错：出生日期等字段是可选的，脏值按“未填写”处理，必填日期由各 save 方法单独校验。
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private String dateText(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private String normalizeSpecies(String value) {
        // 物种入库统一成中文：上游可能传 CAT/cat/猫，这里收敛成“猫/狗”；无法识别的值直接报错，避免按物种过滤或推荐时出现空匹配。
        if ("CAT".equalsIgnoreCase(value) || SPECIES_CAT.equals(value)) {
            return SPECIES_CAT;
        }
        if ("DOG".equalsIgnoreCase(value) || SPECIES_DOG.equals(value)) {
            return SPECIES_DOG;
        }
        throw new BusinessException("宠物物种只能是猫或狗");
    }

    private Set<String> carePlanKeys(Integer petId) {
        Set<String> result = new HashSet<>();
        for (PetCarePlan plan : petCarePlanMapper.selectPendingByPetId(petId)) {
            result.add(carePlanKey(
                    plan.getCareType(), plan.getTitle(), plan.getPlannedOn()));
        }
        return result;
    }

    private String carePlanKey(String careType, String title, LocalDate plannedOn) {
        return careType + "|" + (title == null ? "" : title.trim())
                + "|" + (plannedOn == null ? "" : plannedOn);
    }
}
