package org.lbl.intranet.dataasset.es.controller;

import jakarta.validation.Valid;
import org.lbl.common.result.*;
import org.lbl.intranet.dataasset.es.request.*;
import org.lbl.intranet.dataasset.es.service.EsAssetService;
import org.lbl.intranet.dataasset.es.vo.*;
import org.lbl.system.log.aspect.OperationLog;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/intranet/data-assets/es-indexes")
public class EsAssetController {
    private final EsAssetService service;
    public EsAssetController(EsAssetService service) { this.service = service; }
    @GetMapping @PreAuthorize("hasAuthority('intranet:dataasset:list')") Result<PageResult<EsIndexVO>> page(@RequestParam(defaultValue="1") long pageNum, @RequestParam(defaultValue="10") long pageSize, @RequestParam(required=false) String keyword) { return Result.ok(service.page(pageNum, pageSize, keyword)); }
    @GetMapping("/options") @PreAuthorize("hasAnyAuthority('intranet:dataasset:list','intranet:transaction:list','intranet:transaction:update')") Result<List<EsIndexVO>> options() { return Result.ok(service.options()); }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('intranet:dataasset:list')") Result<EsIndexVO> detail(@PathVariable Long id) { return Result.ok(service.detail(id)); }
    @PostMapping @PreAuthorize("hasAuthority('intranet:dataasset:add')") @OperationLog(module="ES 资产管理", action="新增 ES 索引", targetType="INTRANET_ES_INDEX") Result<EsIndexVO> create(@Valid @RequestBody EsIndexRequest r) { return Result.ok(service.create(r), "新增 ES 索引成功"); }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('intranet:dataasset:update')") @OperationLog(module="ES 资产管理", action="修改 ES 索引", targetType="INTRANET_ES_INDEX", targetId="#id") Result<EsIndexVO> update(@PathVariable Long id, @Valid @RequestBody EsIndexRequest r) { return Result.ok(service.update(id, r), "修改 ES 索引成功"); }
    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('intranet:dataasset:delete')") @OperationLog(module="ES 资产管理", action="删除 ES 索引", targetType="INTRANET_ES_INDEX", targetId="#id") Result<Void> delete(@PathVariable Long id) { service.remove(id); return Result.ok(null, "删除 ES 索引成功"); }
    @GetMapping("/{indexId}/fields") @PreAuthorize("hasAuthority('intranet:dataasset:list')") Result<List<EsFieldVO>> fields(@PathVariable Long indexId) { return Result.ok(service.fields(indexId)); }
    @PostMapping("/{indexId}/fields") @PreAuthorize("hasAuthority('intranet:dataasset:add')") Result<EsFieldVO> createField(@PathVariable Long indexId, @Valid @RequestBody EsFieldRequest r) { return Result.ok(service.createField(indexId, r), "新增 ES 字段成功"); }
    @PutMapping("/{indexId}/fields/{id}") @PreAuthorize("hasAuthority('intranet:dataasset:update')") Result<EsFieldVO> updateField(@PathVariable Long indexId, @PathVariable Long id, @Valid @RequestBody EsFieldRequest r) { return Result.ok(service.updateField(indexId, id, r), "修改 ES 字段成功"); }
    @DeleteMapping("/{indexId}/fields/{id}") @PreAuthorize("hasAuthority('intranet:dataasset:delete')") Result<Void> deleteField(@PathVariable Long indexId, @PathVariable Long id) { service.removeField(indexId, id); return Result.ok(null, "删除 ES 字段成功"); }
}
