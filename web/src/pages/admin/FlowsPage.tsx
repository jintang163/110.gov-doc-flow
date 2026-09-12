import { useEffect, useState } from 'react'
import {
  Button, Card, Form, Input, InputNumber, Modal, Popconfirm, Select, Space,
  Switch, Table, Tag, Typography, message,
} from 'antd'
import { ArrowDownOutlined, ArrowUpOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import client from '../../api/client'
import { FlowConfig, FlowNode, NODE_MODE_TEXT, NODE_TYPE_TEXT, Org, UserInfo } from '../../api/types'

const ASSIGNEE_TYPE_TEXT: Record<string, string> = {
  USERS: '指定人员',
  POST: '按岗位',
  ORG_LEADER: '部门负责人',
}

function newNode(idx: number): FlowNode {
  return {
    key: `n${idx}`,
    name: '',
    type: 'AUDIT',
    mode: 'ALL',
    assigneeType: 'USERS',
    userIds: [],
    timeoutHours: 24,
  }
}

export default function FlowsPage() {
  const [list, setList] = useState<FlowConfig[]>([])
  const [users, setUsers] = useState<UserInfo[]>([])
  const [orgs, setOrgs] = useState<Org[]>([])
  const [loading, setLoading] = useState(false)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<FlowConfig | null>(null)
  const [nodes, setNodes] = useState<FlowNode[]>([])
  const [form] = Form.useForm()

  const load = () => {
    setLoading(true)
    client.get('/flows').then((d: any) => setList(d)).finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    client.get('/users').then((d: any) => setUsers(d))
    client.get('/orgs').then((d: any) => setOrgs(d))
  }, [])

  const openEdit = (f?: FlowConfig) => {
    setEditing(f || null)
    form.resetFields()
    if (f) {
      form.setFieldsValue({ name: f.name, remark: f.remark, enabled: f.enabled })
      setNodes(JSON.parse(f.nodesJson))
    } else {
      form.setFieldsValue({ enabled: true })
      setNodes([newNode(1)])
    }
    setOpen(true)
  }

  const updateNode = (idx: number, patch: Partial<FlowNode>) => {
    setNodes((prev) => prev.map((n, i) => (i === idx ? { ...n, ...patch } : n)))
  }

  const moveNode = (idx: number, dir: -1 | 1) => {
    setNodes((prev) => {
      const next = [...prev]
      const j = idx + dir
      if (j < 0 || j >= next.length) return prev
      ;[next[idx], next[j]] = [next[j], next[idx]]
      return next
    })
  }

  const save = async () => {
    const values = await form.validateFields()
    if (nodes.length === 0) {
      message.warning('至少配置一个办理节点')
      return
    }
    for (const [i, n] of nodes.entries()) {
      if (!n.name.trim()) return message.warning(`第 ${i + 1} 个节点未填写名称`)
      if (n.assigneeType === 'USERS' && n.userIds.length === 0) return message.warning(`节点「${n.name}」未选择办理人`)
      if (n.assigneeType === 'POST' && !n.post?.trim()) return message.warning(`节点「${n.name}」未填写岗位`)
      if (n.assigneeType === 'ORG_LEADER' && !n.orgId) return message.warning(`节点「${n.name}」未选择部门`)
    }
    // 按顺序重排节点 key，保证唯一且与快照一致
    const payload = nodes.map((n, i) => ({ ...n, key: `n${i + 1}`, name: n.name.trim() }))
    const body = { ...values, nodesJson: JSON.stringify(payload) }
    if (editing) await client.put(`/flows/${editing.id}`, body)
    else await client.post('/flows', body)
    message.success('已保存')
    setOpen(false)
    load()
  }

  const remove = async (f: FlowConfig) => {
    await client.delete(`/flows/${f.id}`)
    message.success('已删除')
    load()
  }

  return (
    <Card
      title="流程配置"
      extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => openEdit()}>新建流程</Button>}
    >
      <Table
        rowKey="id"
        loading={loading}
        dataSource={list}
        pagination={false}
        columns={[
          { title: '流程名称', dataIndex: 'name', width: 200 },
          {
            title: '节点', dataIndex: 'nodesJson',
            render: (v: string) => (
              <Space size={4} wrap>
                {(JSON.parse(v) as FlowNode[]).map((n) => (
                  <Tag key={n.key}>{n.name}（{NODE_TYPE_TEXT[n.type]}）</Tag>
                ))}
              </Space>
            ),
          },
          { title: '版本', dataIndex: 'version', width: 70, render: (v) => `v${v}` },
          {
            title: '状态', dataIndex: 'enabled', width: 90,
            render: (v: boolean) => (v ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>),
          },
          { title: '更新时间', dataIndex: 'updatedAt', width: 170 },
          {
            title: '操作', key: 'op', width: 150,
            render: (_, r) => (
              <Space>
                <Button size="small" onClick={() => openEdit(r)}>编辑</Button>
                <Popconfirm title="删除该流程？已被模板引用的流程删除后会导致发文失败。" onConfirm={() => remove(r)}>
                  <Button size="small" danger>删除</Button>
                </Popconfirm>
              </Space>
            ),
          },
        ]}
      />

      <Modal
        title={editing ? `编辑流程（保存后版本 +1，在办公文不受影响）` : '新建流程'}
        open={open}
        onOk={save}
        onCancel={() => setOpen(false)}
        okText="保存"
        width={860}
        destroyOnClose
      >
        <Form form={form} layout="inline" style={{ marginBottom: 12 }}>
          <Form.Item name="name" label="流程名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input placeholder="如：发文三级审批流程" style={{ width: 240 }} maxLength={60} />
          </Form.Item>
          <Form.Item name="remark" label="备注" style={{ flex: 1 }}>
            <Input placeholder="选填" maxLength={200} />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>

        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          节点按顺序依次流转；类型为「签发」的节点到达时自动分配文号并生成套红文件，签发完成即归档。
        </Typography.Text>

        <Space direction="vertical" style={{ width: '100%', marginTop: 12 }}>
          {nodes.map((n, i) => (
            <Card
              key={i}
              size="small"
              title={`节点 ${i + 1}`}
              extra={
                <Space>
                  <Button size="small" type="text" icon={<ArrowUpOutlined />} disabled={i === 0} onClick={() => moveNode(i, -1)} />
                  <Button size="small" type="text" icon={<ArrowDownOutlined />} disabled={i === nodes.length - 1} onClick={() => moveNode(i, 1)} />
                  <Button
                    size="small" type="text" danger icon={<DeleteOutlined />}
                    onClick={() => setNodes((prev) => prev.filter((_, idx) => idx !== i))}
                  />
                </Space>
              }
            >
              <Space wrap size="middle">
                <span>
                  名称{' '}
                  <Input
                    value={n.name}
                    onChange={(e) => updateNode(i, { name: e.target.value })}
                    placeholder="如：办公室审核"
                    style={{ width: 160 }}
                    maxLength={30}
                  />
                </span>
                <span>
                  类型{' '}
                  <Select
                    value={n.type}
                    style={{ width: 110 }}
                    onChange={(v) => updateNode(i, { type: v })}
                    options={Object.entries(NODE_TYPE_TEXT).map(([value, label]) => ({ value, label }))}
                  />
                </span>
                <span>
                  方式{' '}
                  <Select
                    value={n.mode}
                    style={{ width: 130 }}
                    onChange={(v) => updateNode(i, { mode: v })}
                    options={Object.entries(NODE_MODE_TEXT).map(([value, label]) => ({ value, label }))}
                  />
                </span>
                <span>
                  办理人{' '}
                  <Select
                    value={n.assigneeType}
                    style={{ width: 120 }}
                    onChange={(v) => updateNode(i, { assigneeType: v })}
                    options={Object.entries(ASSIGNEE_TYPE_TEXT).map(([value, label]) => ({ value, label }))}
                  />
                </span>
                {n.assigneeType === 'USERS' && (
                  <Select
                    mode="multiple"
                    value={n.userIds}
                    style={{ minWidth: 240 }}
                    placeholder="选择办理人"
                    onChange={(v) => updateNode(i, { userIds: v })}
                    options={users.map((u) => ({ value: u.id, label: `${u.name}（${u.orgName || u.username}）` }))}
                    optionFilterProp="label"
                  />
                )}
                {n.assigneeType === 'POST' && (
                  <Input
                    value={n.post}
                    onChange={(e) => updateNode(i, { post: e.target.value })}
                    placeholder="岗位，如：核稿"
                    style={{ width: 180 }}
                    maxLength={30}
                  />
                )}
                {n.assigneeType === 'ORG_LEADER' && (
                  <Select
                    value={n.orgId}
                    style={{ width: 200 }}
                    placeholder="选择部门（取其负责人）"
                    onChange={(v) => updateNode(i, { orgId: v })}
                    options={orgs.map((o) => ({ value: o.id, label: o.name }))}
                  />
                )}
                <span>
                  时限(h){' '}
                  <InputNumber
                    value={n.timeoutHours}
                    min={1}
                    max={720}
                    onChange={(v) => updateNode(i, { timeoutHours: v || 24 })}
                  />
                </span>
              </Space>
            </Card>
          ))}
        </Space>
        <Button
          block
          type="dashed"
          icon={<PlusOutlined />}
          style={{ marginTop: 12 }}
          onClick={() => setNodes((prev) => [...prev, newNode(prev.length + 1)])}
        >
          添加节点
        </Button>
      </Modal>
    </Card>
  )
}
