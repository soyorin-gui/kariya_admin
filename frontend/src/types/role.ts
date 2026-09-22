export interface Role {
  id: number;
  roleName: string;
  roleCode: string;
  dataScope: 'ALL' | 'DEPT_AND_CHILDREN' | 'DEPT' | 'SELF';
  status: number;
  builtin: number;
  userCount: number;
  createdTime?: string;
}

export interface RoleRequest {
  roleName: string;
  roleCode: string;
  dataScope: Role['dataScope'];
  status: number;
}
