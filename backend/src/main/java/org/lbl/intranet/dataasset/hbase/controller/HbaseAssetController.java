package org.lbl.intranet.dataasset.hbase.controller;

import jakarta.validation.Valid;
import org.lbl.common.result.*;
import org.lbl.intranet.dataasset.hbase.request.*;
import org.lbl.intranet.dataasset.hbase.service.HbaseAssetService;
import org.lbl.intranet.dataasset.hbase.vo.*;
import org.lbl.system.log.aspect.OperationLog;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/intranet/data-assets/hbase-tables")
public class HbaseAssetController {
    private final HbaseAssetService service;
    public HbaseAssetController(HbaseAssetService service) { this.service = service; }
    @GetMapping @PreAuthorize("hasAuthority('intranet:dataasset:list')") Result<PageResult<HbaseTableVO>> page(@RequestParam(defaultValue="1") long pageNum, @RequestParam(defaultValue="10") long pageSize, @RequestParam(required=false) String keyword) { return Result.ok(service.page(pageNum, pageSize, keyword)); }
    @GetMapping("/options") @PreAuthorize("hasAnyAuthority('intranet:dataasset:list','intranet:transaction:list','intranet:transaction:update')") Result<List<HbaseTableVO>> options() { return Result.ok(service.options()); }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('intranet:dataasset:list')") Result<HbaseTableVO> detail(@PathVariable Long id) { return Result.ok(service.detail(id)); }
    @PostMapping @PreAuthorize("hasAuthority('intranet:dataasset:add')") @OperationLog(module="HBase 资产管理", action="新增 HBase 表", targetType="INTRANET_HBASE_TABLE") Result<HbaseTableVO> create(@Valid @RequestBody HbaseTableRequest r) { return Result.ok(service.create(r), "新增 HBase 表成功"); }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('intranet:dataasset:update')") @OperationLog(module="HBase 资产管理", action="修改 HBase 表", targetType="INTRANET_HBASE_TABLE", targetId="#id") Result<HbaseTableVO> update(@PathVariable Long id, @Valid @RequestBody HbaseTableRequest r) { return Result.ok(service.update(id, r), "修改 HBase 表成功"); }
    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('intranet:dataasset:delete')") @OperationLog(module="HBase 资产管理", action="删除 HBase 表", targetType="INTRANET_HBASE_TABLE", targetId="#id") Result<Void> delete(@PathVariable Long id) { service.remove(id); return Result.ok(null, "删除 HBase 表成功"); }
    @GetMapping("/{tableId}/fields") @PreAuthorize("hasAuthority('intranet:dataasset:list')") Result<List<HbaseFieldVO>> fields(@PathVariable Long tableId) { return Result.ok(service.fields(tableId)); }
    @PostMapping("/{tableId}/fields") @PreAuthorize("hasAuthority('intranet:dataasset:add')") Result<HbaseFieldVO> createField(@PathVariable Long tableId, @Valid @RequestBody HbaseFieldRequest r) { return Result.ok(service.createField(tableId, r), "新增 HBase 字段成功"); }
    @PutMapping("/{tableId}/fields/{id}") @PreAuthorize("hasAuthority('intranet:dataasset:update')") Result<HbaseFieldVO> updateField(@PathVariable Long tableId, @PathVariable Long id, @Valid @RequestBody HbaseFieldRequest r) { return Result.ok(service.updateField(tableId, id, r), "修改 HBase 字段成功"); }
    @DeleteMapping("/{tableId}/fields/{id}") @PreAuthorize("hasAuthority('intranet:dataasset:delete')") Result<Void> deleteField(@PathVariable Long tableId, @PathVariable Long id) { service.removeField(tableId, id); return Result.ok(null, "删除 HBase 字段成功"); }
}
