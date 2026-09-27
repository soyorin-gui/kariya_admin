export interface Dept {
  id: number;
  parentId: number;
  ancestors: string;
  deptName: string;
  deptCode: string;
  leaderUserId?: number;
  leaderName: string;
  sortOrder: number;
  status: number;
  builtin: number;
  createdTime?: string;
  /** 返回只读祖先记录是为了保持部门树结构完整。 */
  manageable: boolean;
  canCreateChildren: boolean;
}

export interface DeptRequest {
  parentId?: number;
  deptName: string;
  deptCode: string;
  leaderUserId?: number;
  sortOrder: number;
  status: number;
}

/**
 * 新增/编辑部门表单的候选数据。
 * canCreateRoot 由后端给出：它决定「上级部门」能不能被清空（清空 = 建顶级部门），
 * 而这条规则的唯一依据在后端 AccessPolicy.canCreateRootDept，前端推不出来。
 */
export interface DeptFormOptions {
  leaders: { value: number; label: string }[];
  canCreateRoot: boolean;
}
