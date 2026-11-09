package com.hnist.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 密码哈希配置：全局唯一 PasswordEncoder，注册加密与登录校验共用同一实例，保证哈希算法一致。 */
@Configuration
public class PasswordConfig {

    /** BCrypt 强度因子 12：约 2^12 轮哈希，单次校验几十毫秒，在登录体验与暴力破解成本间取平衡；提升会使存量哈希自动失效校验变慢，不可随意调。 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
