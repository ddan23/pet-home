package com.hnist.support;

import com.hnist.common.constant.ModerationStatus;
import com.hnist.mapper.SensitiveWordMapper;
import com.hnist.entity.SensitiveWord;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于 Trie 的敏感词快速过滤器：词库来自 classpath 文件 + 数据库启用规则，匹配前做 NFKC 归一化。
 * root 用 AtomicReference 持有，规则重载时整体原子替换，读操作无锁、不会看到半成品词树。
 */
@Component
public class SensitiveWordFilter {

    private static final String WORD_FILE = "moderation/sensitive-words.txt";

    /** 当前生效的 Trie 根节点；替换始终指向完整编译好的新树。 */
    private final AtomicReference<RuleNode> root = new AtomicReference<>(new RuleNode());
    private final SensitiveWordMapper sensitiveWordMapper;

    public SensitiveWordFilter(SensitiveWordMapper sensitiveWordMapper) {
        this.sensitiveWordMapper = sensitiveWordMapper;
    }

    /**
     * 启动时加载并编译敏感词规则。文件缺失视为空词库（降级放行），读取失败抛 IllegalStateException 阻止启动；
     * 每行格式为 word|SEVERITY|category，严重级别非法的行直接跳过。
     */
    @PostConstruct
    public void loadRules() {
        RuleNode nextRoot = new RuleNode();
        ClassPathResource resource = new ClassPathResource(WORD_FILE);
        if (!resource.exists()) {
            root.set(nextRoot);
            return;
        }
        try (InputStream inputStream = resource.getInputStream();
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .forEach(line -> addRule(nextRoot, line));
        } catch (IOException exception) {
            throw new IllegalStateException("敏感词规则读取失败", exception);
        }
        loadDatabaseRules(nextRoot);
        root.set(nextRoot);
    }

    /** 返回全文中严重级别最高的一次命中（无命中返回 empty）；匹配前先归一化，绕过符号分隔等变体。 */
    public Optional<SensitiveMatch> find(String text) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        SensitiveMatch highest = null;
        for (int start = 0; start < normalized.length(); start++) {
            SensitiveMatch match = matchAt(normalized, start);
            if (match != null && isMoreSevere(match, highest)) {
                highest = match;
            }
        }
        return Optional.ofNullable(highest);
    }

    private void addRule(RuleNode rootNode, String line) {
        String[] parts = line.split("\\|", 3);
        if (parts.length < 2) {
            return;
        }
        String normalizedWord = normalize(parts[0]);
        if (normalizedWord.isEmpty()) {
            return;
        }
        ModerationStatus severity;
        try {
            severity = ModerationStatus.valueOf(parts[1].trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return;
        }
        String category = parts.length == 3 ? parts[2].trim() : "other";
        RuleNode current = rootNode;
        for (int index = 0; index < normalizedWord.length(); index++) {
            current = current.children.computeIfAbsent(
                    normalizedWord.charAt(index), key -> new RuleNode());
        }
        current.terminal = new SensitiveMatch(parts[0].trim(), severity, category);
    }

    private void loadDatabaseRules(RuleNode rootNode) {
        try {
            for (SensitiveWord rule : sensitiveWordMapper.selectEnabled()) {
                addRule(rootNode, rule.getWord() + "|" + rule.getSeverity()
                        + "|" + rule.getCategory());
            }
        } catch (RuntimeException exception) {
            throw new IllegalStateException("数据库敏感词规则读取失败", exception);
        }
    }

    /** 取从 start 出发沿 Trie 能走到的最长命中；走不下去即提前结束。 */
    private SensitiveMatch matchAt(String text, int start) {
        RuleNode current = root.get();
        SensitiveMatch result = null;
        for (int index = start; index < text.length(); index++) {
            current = current.children.get(text.charAt(index));
            if (current == null) {
                break;
            }
            if (current.terminal != null) {
                result = current.terminal;
            }
        }
        return result;
    }

    private boolean isMoreSevere(SensitiveMatch candidate, SensitiveMatch current) {
        return current == null || severityWeight(candidate.severity())
                > severityWeight(current.severity());
    }

    private int severityWeight(ModerationStatus status) {
        return switch (status) {
            case ALLOW -> 0;
            case REVIEW -> 1;
            case BLOCK -> 2;
        };
    }

    /** 归一化：NFKC 折叠全角/兼容字符 → 小写 → 只保留字母数字，使大小写与标点变体可被匹配。 */
    private String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(normalized.length());
        normalized.codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(builder::appendCodePoint);
        return builder.toString();
    }

    private static final class RuleNode {

        private final Map<Character, RuleNode> children = new HashMap<>();
        private SensitiveMatch terminal;
    }

    /** 敏感词命中结果。 */
    public record SensitiveMatch(String word, ModerationStatus severity, String category) {
    }
}
