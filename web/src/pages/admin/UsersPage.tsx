import { useEffect, useState } from 'react'
import { Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Switch, Table, Tag, message } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import client from '../../api/client'
import { Org, SECRET_LEVELS } from '../../api/types'

interface AdminUser {
  id: number
  username: string
  name: string
  orgId: number
  posts: string
  clearance: number
  admin: boolean
  enabled: boolean
}

export default function UsersPage() {
  const [list, setList] = useState<AdminUser[]>([])
  const [orgs, setOrgs] = useState<Org[]>([])
  const [loading, setLoading] = useState(false)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<AdminUser | null>(null)
  const [form] = Form.useForm()

  const load = () => {
    setLoading(true)
    client.get('/users', { params: { all: true } }).then((d: any) => setList(d)).finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    client.get('/orgs').then((d: any) => setOrgs(d))
  }, [])

  const openEdit = (u?: AdminUser) => {
    setEditing(u || null)
    form.resetFields()
    if (u) form.setFieldsValue({ ...u, password: '' })
    setOpen(true)
  }

  const save = async () => {
    const values = await form.validateFields()
    if (editing) await client.put(`/users/${editing.id}`, values)
    else await client.post('/users', values)
    message.success('已保存')
    setOpen(false)
    load()
  }

  const toggle = async (u: AdminUser) => {
    await client.put(`/users/${u.id}`, { ...u, enabled: !u.enabled })
    load()
  }

  return (
    <Card
      title="用户管理"
      extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => openEdit()}>新建用户</Button>}
    >
      <Table
        rowKey="id"
        loading={loading}
        dataSource={list}
        pagination={{ pageSize: 12 }}
        columns={[
          { title: '用户名', dataIndex: 'username', width: 130 },
          { title: '姓名', dataIndex: 'name', width: 110 },
          {
            title: '部门', dataIndex: 'orgId', width: 180,
            render: (v: number) => orgs.find((o) => o.id === v)?.name || '—',
          },
          { title: '岗位', dataIndex: 'posts', ellipsis: true, render: (v: string) => v || '—' },
          {
            title: '密级许可', dataIndex: 'clearance', width: 100,
            render: (v: number) => <Tag color={v >= 2 ? 'red' : 'default'}>{SECRET_LEVELS[v]}</Tag>,
          },
          {
            title: '角色', dataIndex: 'admin', width: 90,
            render: (v: boolean) => (v ? <Tag color="gold">管理员</Tag> : <Tag>普通</Tag>),
          },
          {
            title: '状态', dataIndex: 'enabled', width: 90,
            render: (v: boolean) => (v ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>),
          },
          {
            title: '操作', key: 'op', width: 150,
            render: (_, r) => (
              <Space>
                <Button size="small" onClick={() => openEdit(r)}>编辑</Button>
                <Popconfirm title={r.enabled ? '停用后该用户不能登录？' : '启用该用户？'} onConfirm={() => toggle(r)}>
                  <Button size="small" danger={r.enabled}>{r.enabled ? '停用' : '启用'}</Button>
                </Popconfirm>
              </Space>
            ),
          },
        ]}
      />

      <Modal
        title={editing ? '编辑用户' : '新建用户'}
        open={open}
        onOk={save}
        onCancel={() => setOpen(false)}
        okText="保存"
        destroyOnClose
      >
        <Form form={form} layout="vertical" initialValues={{ clearance: 1, admin: false, enabled: true }}>
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input disabled={!!editing} maxLength={32} />
          </Form.Item>
          <Form.Item
            name="password"
            label="密码"
            extra={editing ? '留空则不修改密码' : '留空则使用初始密码 123456'}
          >
            <Input.Password maxLength={32} autoComplete="new-password" />
          </Form.Item>
          <Form.Item name="name" label="姓名" rules={[{ required: true, message: '请输入姓名' }]}>
            <Input maxLength={32} />
          </Form.Item>
          <Form.Item name="orgId" label="部门" rules={[{ required: true, message: '请选择部门' }]}>
            <Select options={orgs.map((o) => ({ value: o.id, label: o.name }))} />
          </Form.Item>
          <Form.Item name="posts" label="岗位（逗号分隔，流程可按岗位指派）">
            <Input placeholder="如：科员,核稿,办公室主任" maxLength={128} />
          </Form.Item>
          <Form.Item name="clearance" label="密级许可（可查看不高于该密级的公文）">
            <Select options={SECRET_LEVELS.map((s, i) => ({ value: i, label: s }))} />
          </Form.Item>
          <Space size="large">
            <Form.Item name="admin" label="管理员" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name="enabled" label="启用" valuePropName="checked">
              <Switch />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </Card>
  )
}
