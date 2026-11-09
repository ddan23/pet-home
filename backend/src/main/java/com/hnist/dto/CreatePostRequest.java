package com.hnist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 发帖入参（POST /api/posts）：提交后进入内容审核，帖子的 status 由服务端按审核结果回填。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePostRequest {

    @NotBlank(message = "分类不能为空")
    @Pattern(regexp = "晒宠|求助|领养|好物|日常", message = "帖子分类不合法")
    private String category;

    @Size(max = 200, message = "标题不能超过200个字符")
    private String title;

    @NotBlank(message = "帖子正文不能为空")
    @Size(max = 4000, message = "帖子内容不能超过4000个字符")
    private String content;

    @Size(max = 255, message = "图片地址过长")
    private String image;
}
