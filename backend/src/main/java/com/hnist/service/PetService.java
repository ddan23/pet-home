package com.hnist.service;

/** 宠物健康档案服务。 */
public interface PetService {

    // 返回前端约定格式的 JSON 数组，只含该用户在用档案（status='normal' 且未软删）及其疫苗、驱虫、体重、过敏、待办计划。
    String getPetsJson(Integer userId);

    // 整份覆盖保存：请求体中缺失的 petId 视为删除，存在未完成的未来预约时整次保存失败并回滚。
    void savePetsJson(Integer userId, String petsJson);
}
