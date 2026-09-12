import { useCallback, useEffect, useState } from 'react'
import {
  Button, Card, Descriptions, Form, Input, InputNumber, Modal, Popconfirm, Radio,
  Select, Space, Steps, Table, Tag, Timeline, Typography, message,
} from 'antd'
import {
  CheckOutlined, CloseOutlined, FilePdfOutlined, RollbackOutlined,
  SafetyCertificateOutlined, SendOutlined, SoundOutlined,
} from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import client, { docPdfUrl, fileUrl } from '../api/client'
import {
  Attachment, DocDetail, DOC_STATUS, NODE_MODE_TEXT, NODE_TYPE_TEXT,
  Seal, SealVerify, SECRET_LEVELS,
} from '../api/types'

const TRACE_ACTION: Record<string, { text: string; color: string }> = {
  CREATE: { text: '拟稿', color: 'blue' },
  SUBMIT: { text: '提交', color: 'blue' },
  RESUBMIT: { text: '重新提交', color: 'blue' },
  APPROVE: { text: '同意', color: 'green' },
  RETURN_PREV: { text: '退回上一步', color: 'orange' },
  RETURN_DRAFT: { text: '退回拟稿', color: 'orange' },
  URGE: { text: '催办', color: 'gold' },
  SEAL: { text: '盖章', color: 'red' },
  ISSUE_PREP: { text: '套红编号', color: 'purple' },
  OVERDUE: { text: '超时提醒', color: 'red' },
  ARCHIVE: { text: '归档', color: 'green' },
}

const TASK_ACTION: Record<string, string> = {
  APPROVE: '同意',
  RETURN_PREV: '退回上一步',
  RETURN_DRAFT: '退回拟稿',
}

const TASK_STATUS: Record<string, { text: string; color: string }> = {
  PENDING: { text: '待办理', color: 'processing' },
  DONE: { text: '已办理', color: 'success' },
  RETURNED: { text: '被退回', color: 'warning' },
  CANCELED: { text: '已取消', color: 'default' },
}

interface NameMap {
  [id: number]: string
}

export default function DocDetailPage() {
  const { id } = useParams()
  const docId = Number(id)
  const navigate = useNavigate()
  const [detail, setDetail] = useState<DocDetail | null>(null)
  const [userNames, setUserNames] = useState<NameMap>({})
  const [comment, setComment] = useState('')
  const [acting, setActing] = useState(false)
  const [sealOpen, setSealOpen] = useState(false)
  const [verifyOpen, setVerifyOpen] = useState(false)
  const [verify, setVerify] = useState<SealVerify | null>(null)
  const [seals, setSeals] = useState<Seal[]>([])
  const [sealForm] = Form.useForm()

  const load = useCallback(() => {
    client.get(`/docs/${docId}`).then((d: any) => setDetail(d)).catch(() => {})
  }, [docId])

  useEffect(() => {
    load()
    client.get('/users').then((d: any) => {
      const m: NameMap = {}
      ;(d as { id: number; name: string }[]).forEach((u) => (m[u.id] = u.name))
      setUserNames(m)
    }).catch(() => {})
  }, [load])

  if (!detail) return <Card loading />

  const { doc, flowNodes, tasks, traces, perms } = detail
  const attachments: Attachment[] = JSON.parse(doc.attachmentsJson || '[]')
  const currentIdx = flowNodes.findIndex((n) => n.key === doc.currentNodeKey)
  const stepStatus = (idx: number) => {
    if (doc.status === 'ARCHIVED') return 'finish' as const
    if (doc.status !== 'RUNNING') return idx === 0 ? 'process' as const : 'wait' as const
    if (idx < currentIdx) return 'finish' as const
    if (idx === currentIdx) return 'process' as const
    return 'wait' as const
  }

  const doAction = async (action: 'complete' | 'return-prev' | 'return-draft') => {
    if (!perms.myPendingTaskId) return
    if (action !== 'complete' && !comment.trim()) {
      message.warning('退回时请填写办理意见')
      return
    }
    setActing(true)
    try {
      await client.post(`/docs/${docId}/${action}`, {
        taskId: perms.myPendingTaskId,
        comment: comment.trim() || undefined,
      })
      message.success('已提交办理结果')
      setComment('')
      load()
    } finally {
      setActing(false)
    }
  }

  const urge = async () => {
    await client.post(`/docs/${docId}/urge`)
    message.success('已催办当前办理人')
    load()
  }

  const openSeal = () => {
    client.get('/seals').then((d: any) => {
      const usable = (d as Seal[]).filter((s) => s.enabled && s.imageKey)
      setSeals(usable)
      if (usable.length === 0) {
        message.warning('暂无可用签章，请先在「签章管理」生成章图')
        return
      }
      sealForm.resetFields()
      setSealOpen(true)
    })
  }

  const submitSeal = async () => {
    const v = await sealForm.validateFields()
    setActing(true)
    try {
      await client.post(`/docs/${docId}/seal`, {
        sealId: v.sealId,
        type: v.type,
        pageNo: v.pageNo ?? undefined,
        x: v.x ?? undefined,
        y: v.y ?? undefined,
        width: v.width ?? undefined,
      })
      message.success('盖章完成')
      setSealOpen(false)
      load()
    } finally {
      setActing(false)
    }
  }

  const openVerify = () => {
    client.get(`/docs/${docId}/verify`).then((d: any) => {
      setVerify(d)
      setVerifyOpen(true)
    })
  }

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card
        title={
          <Space>
            <span>{doc.title}</span>
            <Tag color={DOC_STATUS[doc.status]?.color}>{DOC_STATUS[doc.status]?.text}</Tag>
            <Tag color={doc.secretLevel >= 2 ? 'red' : 'default'}>{SECRET_LEVELS[doc.secretLevel]}</Tag>
          </Space>
        }
        extra={
          <Space>
            {perms.canEdit && (
              <Button icon={<SendOutlined />} onClick={() => navigate(`/docs/${doc.id}/edit`)}>
                {doc.status === 'RETURNED' ? '补正并重新提交' : '编辑'}
              </Button>
            )}
            {perms.canUrge && (
              <Popconfirm title="向当前所有待办人发送催办提醒？" onConfirm={urge}>
                <Button icon={<SoundOutlined />}>催办</Button>
              </Popconfirm>
            )}
            {perms.canSeal && (
              <Button icon={<SafetyCertificateOutlined />} type="primary" ghost onClick={openSeal}>
                盖章
              </Button>
            )}
            {detail.hasPdf && (
              <>
                <Button icon={<SafetyCertificateOutlined />} onClick={openVerify}>验章</Button>
                <Button
                  type="primary"
                  icon={<FilePdfOutlined />}
                  onClick={() => window.open(docPdfUrl(doc.id), '_blank')}
                >
                  查看红头文件
                </Button>
              </>
            )}
          </Space>
        }
      >
        <Descriptions size="small" column={3} bordered>
          <Descriptions.Item label="发文字号">{doc.docNo || '（签发时分配）'}</Descriptions.Item>
          <Descriptions.Item label="红头模板">{detail.templateName}</Descriptions.Item>
          <Descriptions.Item label="当前节点">{doc.currentNodeName || '—'}</Descriptions.Item>
          <Descriptions.Item label="主送单位">{doc.mainSend || '—'}</Descriptions.Item>
          <Descriptions.Item label="抄送单位">{doc.copySend || '—'}</Descriptions.Item>
          <Descriptions.Item label="拟稿时间">{doc.createdAt}</Descriptions.Item>
        </Descriptions>

        <Steps
          size="small"
          style={{ margin: '20px 0 8px' }}
          items={[
            {
              title: '拟稿',
              status: doc.status === 'DRAFT' || doc.status === 'RETURNED' ? 'process' : 'finish',
              description: doc.status === 'RETURNED' ? '退回补正中' : undefined,
            },
            ...flowNodes.map((n, i) => ({
              title: n.name,
              status: stepStatus(i),
              description: `${NODE_TYPE_TEXT[n.type]} · ${NODE_MODE_TEXT[n.mode]}`,
            })),
            { title: '归档', status: doc.status === 'ARCHIVED' ? 'finish' : 'wait' },
          ]}
        />
      </Card>

      <Card title="正文">
        <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', fontSize: 15, lineHeight: 2 }}>
          {doc.content}
        </Typography.Paragraph>
        {attachments.length > 0 && (
          <>
            <Typography.Text strong>附件（{attachments.length}）</Typography.Text>
            <ul style={{ marginTop: 8 }}>
              {attachments.map((a) => (
                <li key={a.key}>
                  <a href={fileUrl(a.key)} target="_blank" rel="noreferrer">
                    {a.name}
                  </a>
                  <span style={{ color: '#999' }}>（{(a.size / 1024).toFixed(1)} KB）</span>
                </li>
              ))}
            </ul>
          </>
        )}
      </Card>

      {perms.myPendingTaskId && (
        <Card title={`办理 —— ${doc.currentNodeName}`}>
          <Input.TextArea
            rows={3}
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            placeholder="办理意见（退回时必填）"
            maxLength={500}
          />
          <Space style={{ marginTop: 12 }}>
            <Button
              type="primary"
              icon={<CheckOutlined />}
              loading={acting}
              onClick={() => doAction('complete')}
            >
              同意
            </Button>
            <Button icon={<RollbackOutlined />} loading={acting} onClick={() => doAction('return-prev')}>
              退回上一步
            </Button>
            <Popconfirm title="退回拟稿人补正？本次流程将终止，文号作废。" onConfirm={() => doAction('return-draft')}>
              <Button danger icon={<CloseOutlined />} loading={acting}>
                退回拟稿
              </Button>
            </Popconfirm>
          </Space>
        </Card>
      )}

      <Card title="办理记录">
        <Table
          rowKey="id"
          size="small"
          pagination={false}
          dataSource={tasks}
          columns={[
            { title: '节点', dataIndex: 'nodeName', width: 140 },
            {
              title: '办理人', dataIndex: 'assigneeId', width: 110,
              render: (v?: number) => (v ? userNames[v] || `#${v}` : '—'),
            },
            {
              title: '状态', dataIndex: 'status', width: 100,
              render: (v: string) => <Tag color={TASK_STATUS[v]?.color}>{TASK_STATUS[v]?.text || v}</Tag>,
            },
            {
              title: '办理意见', dataIndex: 'action', width: 110,
              render: (v?: string) => (v ? TASK_ACTION[v] || v : '—'),
            },
            { title: '意见内容', dataIndex: 'comment', ellipsis: true, render: (v?: string) => v || '—' },
            { title: '到达时间', dataIndex: 'createdAt', width: 170 },
            { title: '办结时间', dataIndex: 'doneAt', width: 170, render: (v?: string) => v || '—' },
          ]}
        />
      </Card>

      <Card title="流转留痕">
        <Timeline
          items={[...traces].reverse().map((t) => ({
            color: TRACE_ACTION[t.action]?.color || 'gray',
            children: (
              <>
                <Space size="small">
                  <Tag color={TRACE_ACTION[t.action]?.color}>{TRACE_ACTION[t.action]?.text || t.action}</Tag>
                  <Typography.Text strong>{t.actorName}</Typography.Text>
                  {t.nodeName && <Tag>{t.nodeName}</Tag>}
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>{t.createdAt}</Typography.Text>
                </Space>
                <div style={{ color: '#555', marginTop: 4 }}>
                  {[t.detail, t.comment].filter(Boolean).join('；')}
                </div>
              </>
            ),
          }))}
        />
      </Card>

      <Modal
        title="电子签章"
        open={sealOpen}
        onOk={submitSeal}
        confirmLoading={acting}
        onCancel={() => setSealOpen(false)}
        okText="确认盖章"
        destroyOnClose
      >
        <Form form={sealForm} layout="vertical" initialValues={{ type: 'LOCATE', pageNo: 1 }}>
          <Form.Item name="sealId" label="选择签章" rules={[{ required: true, message: '请选择签章' }]}>
            <Select
              options={seals.map((s) => ({ value: s.id, label: s.name }))}
              placeholder="选择可用签章"
            />
          </Form.Item>
          <Form.Item name="type" label="盖章方式">
            <Radio.Group
              options={[
                { value: 'LOCATE', label: '定位盖章（指定页与坐标）' },
                { value: 'STITCH', label: '骑缝章（每页右侧边缘）' },
              ]}
            />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(a, b) => a.type !== b.type}>
            {({ getFieldValue }) =>
              getFieldValue('type') === 'LOCATE' && (
                <Space size="middle" wrap>
                  <Form.Item name="pageNo" label="页码">
                    <InputNumber min={1} precision={0} />
                  </Form.Item>
                  <Form.Item name="x" label="X 坐标（默认右侧）">
                    <InputNumber min={0} placeholder="默认" />
                  </Form.Item>
                  <Form.Item name="y" label="Y 坐标（默认 170）">
                    <InputNumber min={0} placeholder="默认" />
                  </Form.Item>
                  <Form.Item name="width" label="章宽（默认 140）">
                    <InputNumber min={40} max={300} placeholder="默认" />
                  </Form.Item>
                </Space>
              )
            }
          </Form.Item>
          <Typography.Paragraph type="secondary" style={{ fontSize: 12, marginBottom: 0 }}>
            坐标单位为 PDF 磅（A4 约 595×842），留空使用默认位置。每次盖章生成新版 PDF 并记录哈希，可验章追溯。
          </Typography.Paragraph>
        </Form>
      </Modal>

      <Modal
        title="验章结果"
        open={verifyOpen}
        footer={<Button onClick={() => setVerifyOpen(false)}>关闭</Button>}
        onCancel={() => setVerifyOpen(false)}
        width={720}
      >
        {verify && (
          <>
            <Typography.Paragraph>
              当前文件完整性：
              {verify.currentValid
                ? <Tag color="green">校验通过</Tag>
                : <Tag color="red">校验失败（文件可能被篡改）</Tag>}
              {verify.currentHash && (
                <Typography.Text code copyable style={{ fontSize: 12 }}>
                  SHA-256: {verify.currentHash.slice(0, 32)}…
                </Typography.Text>
              )}
            </Typography.Paragraph>
            <Table
              rowKey="id"
              size="small"
              pagination={false}
              dataSource={verify.records}
              columns={[
                { title: '签章', dataIndex: 'sealName' },
                {
                  title: '方式', dataIndex: 'type', width: 90,
                  render: (v: string) => (v === 'STITCH' ? '骑缝章' : '定位章'),
                },
                { title: '页码', dataIndex: 'pageNo', width: 70, render: (v?: number) => v || '—' },
                { title: '操作人', dataIndex: 'operatorName', width: 100 },
                { title: '盖章时间', dataIndex: 'createdAt', width: 170 },
                {
                  title: '校验', dataIndex: 'valid', width: 80,
                  render: (v: boolean) => (v ? <Tag color="green">有效</Tag> : <Tag color="red">失效</Tag>),
                },
              ]}
            />
          </>
        )}
      </Modal>
    </Space>
  )
}
