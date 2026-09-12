export interface UserInfo {
  id: number
  username: string
  name: string
  orgId: number
  orgName: string
  posts: string
  clearance: number
  admin: boolean
}

export interface Org {
  id: number
  name: string
  parentId: number
  leaderId?: number
  sort: number
}

export interface DocTemplate {
  id: number
  name: string
  redTitle: string
  noPrefix: string
  issuer: string
  flowId: number
  enabled: boolean
  remark?: string
}

export interface FlowNode {
  key: string
  name: string
  type: 'AUDIT' | 'COUNTERSIGN' | 'ISSUE'
  mode: 'ALL' | 'ANY' | 'SEQUENCE'
  assigneeType: 'USERS' | 'POST' | 'ORG_LEADER'
  userIds: number[]
  post?: string
  orgId?: number
  timeoutHours: number
}

export interface FlowConfig {
  id: number
  name: string
  nodesJson: string
  version: number
  enabled: boolean
  remark?: string
  updatedAt: string
}

export interface Attachment {
  name: string
  key: string
  size: number
}

export interface Document {
  id: number
  title: string
  templateId: number
  flowId: number
  secretLevel: number
  mainSend?: string
  copySend?: string
  content?: string
  attachmentsJson: string
  status: 'DRAFT' | 'RUNNING' | 'RETURNED' | 'ARCHIVED'
  docNo?: string
  currentNodeKey?: string
  currentNodeName?: string
  attempt: number
  createdBy: number
  createdAt: string
  updatedAt: string
  submittedAt?: string
  issuedAt?: string
  archivedAt?: string
}

export interface TaskItem {
  id: number
  docId: number
  nodeName: string
  status: string
  action?: string
  comment?: string
  createdAt: string
  doneAt?: string
  attempt: number
  title?: string
  docNo?: string
  docStatus?: string
  secretLevel?: number
  overdue?: boolean
}

export interface Trace {
  id: number
  actorName: string
  action: string
  nodeName?: string
  comment?: string
  detail?: string
  createdAt: string
}

export interface DocDetail {
  doc: Document
  templateName: string
  flowNodes: FlowNode[]
  tasks: (TaskItem & { assigneeId?: number })[]
  traces: Trace[]
  perms: {
    canEdit: boolean
    canSubmit: boolean
    myPendingTaskId?: number
    canUrge: boolean
    canSeal: boolean
  }
  hasPdf: boolean
}

export interface Seal {
  id: number
  name: string
  ownerType: 'ORG' | 'USER'
  ownerId: number
  imageKey?: string
  enabled: boolean
}

export interface SealVerify {
  records: {
    id: number
    sealName: string
    type: string
    pageNo: number
    operatorName: string
    createdAt: string
    pdfHash: string
    valid: boolean
  }[]
  currentHash?: string
  currentValid: boolean
}

export interface Notification {
  id: number
  type: string
  title: string
  content?: string
  docId?: number
  readFlag: boolean
  createdAt: string
}

export const SECRET_LEVELS = ['公开', '内部', '秘密', '机密']

export const DOC_STATUS: Record<string, { text: string; color: string }> = {
  DRAFT: { text: '草稿', color: 'default' },
  RUNNING: { text: '流转中', color: 'processing' },
  RETURNED: { text: '退回补正', color: 'warning' },
  ARCHIVED: { text: '已归档', color: 'success' },
}

export const NODE_TYPE_TEXT: Record<string, string> = {
  AUDIT: '审核',
  COUNTERSIGN: '会签',
  ISSUE: '签发',
}

export const NODE_MODE_TEXT: Record<string, string> = {
  ALL: '会签(全部)',
  ANY: '或签(任一)',
  SEQUENCE: '串签(依次)',
}
