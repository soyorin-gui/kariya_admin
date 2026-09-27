package org.lbl.capability.compare.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.lbl.capability.compare.model.CompareModels.DiffReport;
import org.lbl.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 双系统对比的确定性入口。
 * <p>
 * ⚠️ 当前是<b>演示桩</b>：为了让你先把整条 agent 链路端到端跑通，这里直接返回两份写死的
 * 报文（"数据一致、仅排序不同"的场景）。接真实数据时，把 {@link #compare(String)} 里
 * 的演示数据替换为对你现有"同报文双发对比"接口的 HTTP 调用即可，其余代码不用动。
 * <p>
 * 你已有接口是"真相来源"，本类只负责：拿到两份返回 → 归一化 → 差异分类（交给
 * {@link Canonicalizer} + {@link DiffAnalyzer}）。
 */
@Service
public class ComparisonService {
    private final ObjectMapper mapper;

    public ComparisonService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public DiffReport compare(String requestBody) {
        // TODO(接入真实接口): 用 requestBody 分别请求 solr+hbase 与 es+hbase（你已有的对比接口），
        //  拿到两份返回后调用 DiffAnalyzer.diff(solrRecords, esRecords, "业务主键字段")。
        //  注意：这里不要做事务、不要长时间占 Hikari 连接 —— 外部 HTTP 调用要走超时控制。

        // 演示数据：solr 侧顺序 A,B,C；es 侧顺序 C,A,B，但内容完全一致 → 应分类为 SORT_ONLY。
        List<JsonNode> solr = parseRecords("""
                [{"docId":"A","name":"甲","price":"10"},
                 {"docId":"B","name":"乙","price":"20"},
                 {"docId":"C","name":"丙","price":"30"}]
                """);
        List<JsonNode> es = parseRecords("""
                [{"docId":"C","name":"丙","price":"30"},
                 {"docId":"A","name":"甲","price":"10"},
                 {"docId":"B","name":"乙","price":"20"}]
                """);

        return DiffAnalyzer.diff(solr, es, "docId");
    }

    /** 兼容两种返回形状：JSON 数组，或 {"records":[...]} / {"data":[...]} 包裹。 */
    private List<JsonNode> parseRecords(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode array = root;
            if (root.isObject() && root.has("records")) array = root.get("records");
            else if (root.isObject() && root.has("data")) array = root.get("data");
            List<JsonNode> records = new ArrayList<>();
            if (array.isArray()) array.forEach(records::add);
            return records;
        } catch (Exception ex) {
            throw new BusinessException("对比响应解析失败：" + ex.getMessage());
        }
    }
}
