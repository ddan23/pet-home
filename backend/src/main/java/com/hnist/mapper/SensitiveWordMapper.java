package com.hnist.mapper;

import com.hnist.entity.SensitiveWord;

import java.util.List;

/** 敏感词规则数据访问。 */
public interface SensitiveWordMapper {

    List<SensitiveWord> selectEnabled();
}
