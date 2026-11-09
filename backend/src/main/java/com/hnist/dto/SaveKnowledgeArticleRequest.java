package com.hnist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/** 管理端知识文章新增 / 更新入参（/api/admin/knowledge）：reviewStatus 决定是否进入前台检索。 */
@Data
public class SaveKnowledgeArticleRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不能超过200个字符")
    private String title;

    @Pattern(regexp = "cat|dog|all", message = "物种只能是cat、dog或all")
    @NotBlank(message = "适用物种不能为空")
    private String species = "all";

    @NotBlank(message = "主题不能为空")
    @Size(max = 50, message = "主题不能超过50个字符")
    private String topic;

    @NotBlank(message = "摘要不能为空")
    @Size(max = 1000, message = "摘要不能超过1000个字符")
    private String summary;

    @NotBlank(message = "来源标题不能为空")
    @Size(max = 255, message = "来源标题过长")
    private String sourceTitle;

    @NotBlank(message = "来源链接不能为空")
    @Pattern(regexp = "https://.+", message = "来源链接必须使用HTTPS")
    @Size(max = 500, message = "来源链接过长")
    private String sourceUrl;

    @NotBlank(message = "发布机构不能为空")
    @Size(max = 120, message = "发布机构名称过长")
    private String publisher;

    private LocalDate publishedOn;

    @NotBlank(message = "正文不能为空")
    @Size(max = 20000, message = "正文不能超过20000个字符")
    private String content;

    // 只有 published 才写审核人与审核日期并同步进检索索引，草稿 / 归档的 reviewed_on 留空。
    @Pattern(regexp = "draft|published", message = "状态只能是draft或published")
    @NotBlank(message = "保存状态不能为空")
    private String reviewStatus = "draft";
}
