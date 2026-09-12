import { useEffect, useMemo, useState } from 'react'
import { Button, Card, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, TreeSelect, message } from 'antd'
import type { DataNode } from 'antd/es/tree'
import { PlusOutlined } from '@ant-design/icons'
import client from '../../api/client'
import { Org, UserInfo } from '../../api/types'

interface OrgNode extends Org {
  children?: OrgNode[]
}

function buildTree(orgs: Org[]): OrgNode[] {
  const map = new Map<number, OrgNode>()
  orgs.forEach((o) => map.set(o.id, { ...o }))
  const roots: OrgNode[] = []
  map.forEach((node) => {
    if (node.parentId && map.has(node.parentId)) {
      const parent = map.get(node.parentId)!
      parent.children = [...(parent.children || []), node]
    } else {
      roots.push(node)
    }
  })
  const sortRec = (list: OrgNode[]) => {
    list.sort((a, b) => a.sort - b.sort)
    list.forEach((n) => n.children && sortRec(n.children))
  }
  sortRec(roots)
  return roots
}

export default function OrgsPage() {
  const [orgs, setOrgs] = useState<Org[]>([])
  const [users, setUsers] = useState<UserInfo[]>([])
  const [loading, setLoading] = useState(false)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Org | null>(null)
  const [form] = Form.useForm()

  const load = () => {
    setLoading(true)
    client.get('/orgs').then((d: any) => setOrgs(d)).finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    client.get('/users').then((d: any) => setUsers(d))
  }, [])

  const tree = useMemo(() => buildTree(orgs), [orgs])
  const treeSelectData = useMemo(() => {
    const conv = (list: OrgNode[]): DataNode[] =>
      list.map((n) => ({ key: n.id, title: n.name, value: n.id, children: n.children ? conv(n.children) : undefined }))
    return conv(tree)
  }, [tree])

  const openEdit = (o?: Org) => {
    setEditing(o || null)
    form.resetFields()
    if (o) form.setFieldsValue(o)
    setOpen(true)
  }

  const save = async () => {
    const values = await form.validateFields()
    if (editing) await client.put(`/orgs/${editing.id}`, values)
    else await client.post('/orgs', values)
    message.success('已保存')
    setOpen(false)
    load()
  }

  const remove = async (o: Org) => {
    await client.delete(`/orgs/${o.id}`)
    message.success('已删除')
    load()
  }

  return (
    <Card
      title="部门管理"
      extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => openEdit()}>新建部门</Button>}
    >
      <Table
        rowKey="id"
        loading={loading}
        dataSource={tree}
        pagination={false}
        expandable={{ defaultExpandAllRows: true }}
        columns={[
          { title: '部门名称', dataIndex: 'name' },
          {
            title: '负责人', dataIndex: 'leaderId', width: 140,
            render: (v?: number) => users.find((u) => u.id === v)?.name || '—',
          },
          { title: '排序', dataIndex: 'sort', width: 80 },
          {
            title: '操作', key: 'op', width: 150,
            render: (_, r) => (
              <Space>
                <Button size="small" onClick={() => openEdit(r)}>编辑</Button>
                <Popconfirm title="删除该部门？（部门下有用户时不可删）" onConfirm={() => remove(r)}>
                  <Button size="small" danger>删除</Button>
                </Popconfirm>
              </Space>
            ),
          },
        ]}
      />

      <Modal
        title={editing ? '编辑部门' : '新建部门'}
        open={open}
        onOk={save}
        onCancel={() => setOpen(false)}
        okText="保存"
        destroyOnClose
      >
        <Form form={form} layout="vertical" initialValues={{ sort: 0 }}>
          <Form.Item name="name" label="部门名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input maxLength={60} />
          </Form.Item>
          <Form.Item name="parentId" label="上级部门（留空为顶级）">
            <TreeSelect
              allowClear
              treeDefaultExpandAll
              treeData={treeSelectData.filter((t) => t.key !== editing?.id)}
              placeholder="顶级部门"
            />
          </Form.Item>
          <Form.Item name="leaderId" label="负责人（流程「部门负责人」节点取此人）">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              options={users.map((u) => ({ value: u.id, label: `${u.name}（${u.username}）` }))}
            />
          </Form.Item>
          <Form.Item name="sort" label="排序号">
            <InputNumber min={0} max={999} precision={0} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
