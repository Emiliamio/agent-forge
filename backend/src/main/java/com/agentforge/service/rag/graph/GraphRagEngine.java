package com.agentforge.service.rag.graph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GraphRAG 知识图谱实体三元组提取与多跳扩散检索引擎
 * 对标 Microsoft GraphRAG 工业级知识增强标准：
 * 1. 自动从文本中抽取 (Subject, Predicate, Object) 知识关系三元组；
 * 2. 构建内存多维拓扑关系图谱（邻接表索引）；
 * 3. 支持多跳深度扩散搜索 (Multi-Hop Graph Traversal)，解决多层复杂因果推理；
 * 4. 与向量检索并联加权，提供强结构化的关系上下文。
 */
@Service
public class GraphRagEngine {

    private static final Logger log = LoggerFactory.getLogger(GraphRagEngine.class);

    public static class EntityTriplet implements Serializable {
        private final String subject;
        private final String predicate;
        private final String object;

        public EntityTriplet(String subject, String predicate, String object) {
            this.subject = subject != null ? subject.trim() : "";
            this.predicate = predicate != null ? predicate.trim() : "";
            this.object = object != null ? object.trim() : "";
        }

        public String getSubject() { return subject; }
        public String getPredicate() { return predicate; }
        public String getObject() { return object; }

        @Override
        public String toString() {
            return String.format("(%s)-[%s]->(%s)", subject, predicate, object);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof EntityTriplet that)) return false;
            return Objects.equals(subject, that.subject) &&
                    Objects.equals(predicate, that.predicate) &&
                    Objects.equals(object, that.object);
        }

        @Override
        public int hashCode() {
            return Objects.hash(subject, predicate, object);
        }
    }

    // 邻接表：实体 -> 相关的三元组边集合
    private final Map<String, Set<EntityTriplet>> graphIndex = new ConcurrentHashMap<>();

    // 常用中文与业务实体谓词抽取正则模式
    private static final Pattern RELATION_PATTERN = Pattern.compile(
            "([\\u4e00-\\u9fa5A-Za-z0-9_]{2,16})\\s*(签署|属于|负责|拥有|依赖|包含|由.*审核|关联|调用|提供)\\s*([\\u4e00-\\u9fa5A-Za-z0-9_]{2,16})"
    );

    /**
     * 从非结构化文本中自动提取实体三元组
     */
    public List<EntityTriplet> extractTriplets(String text) {
        List<EntityTriplet> list = new ArrayList<>();
        if (text == null || text.isEmpty()) return list;

        Matcher m = RELATION_PATTERN.matcher(text);
        while (m.find()) {
            String subj = m.group(1);
            String pred = m.group(2);
            String obj = m.group(3);
            if (!subj.equalsIgnoreCase(obj)) {
                list.add(new EntityTriplet(subj, pred, obj));
            }
        }
        return list;
    }

    /**
     * 将实体三元组写入图谱索引
     */
    public void indexTriplets(List<EntityTriplet> triplets) {
        if (triplets == null) return;
        for (EntityTriplet t : triplets) {
            graphIndex.computeIfAbsent(t.getSubject(), k -> ConcurrentHashMap.newKeySet()).add(t);
            // 同时建立反向索引便于双向探索
            graphIndex.computeIfAbsent(t.getObject(), k -> ConcurrentHashMap.newKeySet()).add(t);
        }
        log.info("🕸️ [GRAPH_RAG] 成功载入图谱索引: 新增边数={}", triplets.size());
    }

    /**
     * 执行 1~2 跳多跳扩散遍历搜索 (Multi-Hop Graph Traversal)
     *
     * @param startEntity 起始查询实体
     * @param maxHops 最大跳数 (通常建议 1~2 跳以避免爆炸)
     * @return 扩散搜索发现的所有关联三元组
     */
    public Set<EntityTriplet> multiHopSearch(String startEntity, int maxHops) {
        Set<EntityTriplet> resultSet = new LinkedHashSet<>();
        if (startEntity == null || !graphIndex.containsKey(startEntity)) {
            return resultSet;
        }

        Set<String> visitedEntities = new HashSet<>();
        Queue<String> currentQueue = new LinkedList<>();

        currentQueue.add(startEntity);
        visitedEntities.add(startEntity);

        int currentHop = 0;
        while (!currentQueue.isEmpty() && currentHop < maxHops) {
            int levelSize = currentQueue.size();
            for (int i = 0; i < levelSize; i++) {
                String entity = currentQueue.poll();
                Set<EntityTriplet> edges = graphIndex.getOrDefault(entity, Collections.emptySet());

                for (EntityTriplet edge : edges) {
                    resultSet.add(edge);
                    // 发现新实体加入下一跳队列
                    String nextEntity = edge.getSubject().equals(entity) ? edge.getObject() : edge.getSubject();
                    if (!visitedEntities.contains(nextEntity)) {
                        visitedEntities.add(nextEntity);
                        currentQueue.add(nextEntity);
                    }
                }
            }
            currentHop++;
        }

        log.info("🔍 [GRAPH_TRAVERSAL] 完成多跳图谱遍历: 起始实体={}, 跳数={}, 召回三元组数={}",
                startEntity, currentHop, resultSet.size());
        return resultSet;
    }

    /**
     * 格式化图谱上下文为增强提示词
     */
    public String formatGraphContext(Set<EntityTriplet> triplets) {
        if (triplets == null || triplets.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("【GraphRAG 实体关系图谱上下文】：\n");
        for (EntityTriplet t : triplets) {
            sb.append("- ").append(t.toString()).append("\n");
        }
        return sb.toString();
    }
}