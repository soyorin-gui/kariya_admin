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
  /** Read-only ancestor records are returned to keep the tree structurally complete. */
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

export interface DeptFormOptions {
  departments: { value: number; label: string }[];
  leaders: { value: number; label: string }[];
  canCreate: boolean;
}
