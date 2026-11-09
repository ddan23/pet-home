package com.hnist.mcp;

import com.hnist.entity.Pet;
import com.hnist.service.PetHealthQueryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.LinkedHashMap;
import java.util.Map;

/** 宠物健康档案 MCP 工具：薄适配层，复用 HTTP 侧同一套归属校验与领域服务，输出 JSON 供外部 AI 应用消费。 */
public class PetHealthTools {

    private final PetHealthQueryService petHealthQueryService;
    private final ObjectMapper objectMapper;

    public PetHealthTools(PetHealthQueryService petHealthQueryService, ObjectMapper objectMapper) {
        this.petHealthQueryService = petHealthQueryService;
        this.objectMapper = objectMapper;
    }

    /** 汇总一只宠物的健康档案，供外部 AI 应用一次性取得健康上下文。 */
    @Tool(name = "get_pet_health_overview",
            description = "查询指定用户的宠物健康档案汇总：基础信息、体重记录、疫苗接种、驱虫记录、"
                    + "过敏原与待办护理计划。petId 不属于该 userId 时返回 NOT_FOUND，"
                    + "请提示用户核对宠物归属，不要重试其它 petId。")
    public String getPetHealthOverview(
            @ToolParam(description = "用户 ID，必须与宠物归属一致") Integer userId,
            @ToolParam(description = "宠物 ID") Integer petId) {
        // 与 HTTP 侧同一套校验语义：先 findOwnedPet 确认归属，再查子档案，防止跨用户读到健康记录
        Pet pet = petHealthQueryService.findOwnedPet(userId, petId);
        if (pet == null) {
            return "{\"code\":\"NOT_FOUND\",\"message\":\"宠物不存在或不属于该用户\"}";
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pet", baseInfoOf(pet));
        data.put("weightRecords", petHealthQueryService.listWeightRecords(petId));
        data.put("vaccinations", petHealthQueryService.listVaccinations(petId));
        data.put("dewormings", petHealthQueryService.listDewormings(petId));
        data.put("allergies", petHealthQueryService.listAllergies(petId));
        data.put("pendingCarePlans", petHealthQueryService.listPendingCarePlans(petId));
        return toJson(data);
    }

    // 只透出 AI 健康场景所需的基础档案字段，photoUrl 等展示型字段不进 MCP 通道
    private Map<String, Object> baseInfoOf(Pet pet) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", pet.getId());
        info.put("name", pet.getName());
        info.put("species", pet.getSpecies());
        info.put("breed", pet.getBreed());
        info.put("gender", pet.getGender());
        info.put("birthDate", pet.getBirthDate() == null ? null : pet.getBirthDate().toString());
        info.put("neutered", pet.getNeutered());
        info.put("weight", pet.getWeight());
        info.put("medicalNotes", pet.getMedicalNotes());
        return info;
    }

    private String toJson(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            // 不吞异常：向上抛让 MCP 协议层返回 tool 执行错误，而不是静默给模型半截数据
            throw new IllegalStateException("健康档案序列化失败", e);
        }
    }
}
