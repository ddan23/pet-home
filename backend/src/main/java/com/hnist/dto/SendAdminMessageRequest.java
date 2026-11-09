package com.hnist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 管理端发布系统公告入参（POST /api/admin/messages）：按 receiverId 单发，或按 targetRole 群发给可用账号。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendAdminMessageRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不能超过200个字符")
    private String title;

    @NotBlank(message = "内容不能为空")
    @Size(max = 5000, message = "内容不能超过5000个字符")
    private String content;

    // 通知类型必须落在 system 分类的白名单内，否则整批记录都不写入。
    @Size(max = 30, message = "消息类型过长")
    private String type;
    /** 与 targetRole 二选一：targetRole 优先，两者都为空时直接报错。 */
    private Integer receiverId;

    @Pattern(regexp = "user|business|all", message = "目标角色只能是user、business或all")
    private String targetRole;
}
