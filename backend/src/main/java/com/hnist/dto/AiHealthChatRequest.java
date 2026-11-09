package com.hnist.dto;

import com.hnist.domain.HealthChatMessage;
import com.hnist.domain.PetHealthContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 健康助手单轮问答的上下文载体：提问、最近对话历史与宠物档案，供拼装模型提示词。 */
@Data
public class AiHealthChatRequest {

    @NotBlank(message = "请输入健康问题")
    @Size(max = 1000, message = "问题不能超过1000个字符")
    private String message;

    @Valid
    @Size(max = 8, message = "历史消息过多")
    private List<HealthChatMessage> history;

    // 宠物健康档案快照由前端随问题提交：品种、年龄、体重、疫苗与病史决定回答口径，服务端不回查档案。
    @Valid
    private PetHealthContext pet;
}
