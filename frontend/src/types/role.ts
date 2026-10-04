export interface Role {
  id: number;
  roleName: string;
  roleCode: string;
  dataScope: 'ALL' | 'DEPT_AND_CHILDREN' | 'DEPT' | 'SELF' | 'CUSTOM';
  status: number;
  builtin: number;
  userCount: number;
  createdTime?: string;
  manageable: boolean;
  customDeptIds: number[];
  scopeEditable: boolean;
}

export interface RoleRequest {
  roleName: string;
  roleCode: string;
  dataScope: Role['dataScope'];
  customDeptIds?: number[];
  status: number;
}

export interface RoleDeptOption {
  id: number;
  parentId: number;
  deptName: string;
  status: number;
  selectable: boolean;
}
