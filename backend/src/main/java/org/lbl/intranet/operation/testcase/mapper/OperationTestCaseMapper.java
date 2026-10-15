package org.lbl.intranet.operation.testcase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.lbl.intranet.operation.testcase.entity.OperationTestCaseEntity;

@Mapper
public interface OperationTestCaseMapper extends BaseMapper<OperationTestCaseEntity> { }
