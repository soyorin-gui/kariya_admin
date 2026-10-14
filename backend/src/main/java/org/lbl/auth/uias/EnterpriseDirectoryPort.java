package org.lbl.auth.uias;

import java.util.Optional;

/** 内网 ESF/ESB 人员目录适配面；网络调用实现留给实际内网部署。 */
public interface EnterpriseDirectoryPort {
    Optional<EmployeeProfile> findByEmployeeNo(String employeeNo);

    record EmployeeProfile(String employeeNo, String realName, String email, boolean employed) {
    }
}
