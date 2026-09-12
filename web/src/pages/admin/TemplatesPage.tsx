import { useEffect, useState } from 'react'
import { Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Switch, Table, Tag, message } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import client from '../../api/client'
import { DocTemplate, FlowConfig } from '../../api/types'

export default function TemplatesPage() {
  const [list, setList] = useState<DocTemplate[]>([])
  const [flows, setFlows] = useState<FlowConfig[]>([])
  const [loading, setLoading] = useState(false)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<DocTemplate | null>(null)
  const [form] = Form.useForm()

  const load = () => {
    setLoading(true)
    client.get('/templates', { params: { all: true } }).then((d: any) => setList(d)).finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    client.get('/flows').then((d: any) => setFlows(d))
  }, [])

  const openEdit = (t?: DocTemplate) => {
    setEditing(t || null)
    form.resetFields()
    if (t) form.setFieldsValue(t)
    setOpen(true)
  }

  const save = async () => {
    const values = await form.validateFields()
    if (editing) await client.put(`/templates/${editing.id}`, values)
    else await client.post('/templates', values)
    message.success('已保存')
    setOpen(false)
    load()
  }

  const remove = async (t: DocTemplate) => {
    await client.delete(`/templates/${t.id}`)
    message.success('已删除')
    load()
  }

  return (
    <Card
      title="红头模板"
      extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => openEdit()}>新建模板</Button>}
    >
      <Table
        rowKey="id"
        loading={loading}
        dataSource={list}
        pagination={false}
        columns={[
          { title: '模板名称', dataIndex: 'name', width: 180 },
          { title: '红头（发文机关标志）', dataIndex: 'redTitle' },
          { title: '文号前缀', dataIndex: 'noPrefix', width: 160 },
          { title: '发文机关署名', dataIndex: 'issuer', width: 180 },
          {
            title: '绑定流程', dataIndex: 'flowId', width: 150,
            render: (v: number) => flows.find((f) => f.id === v)?.name || `#${v}`,
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
                <Popconfirm title="删除该模板？" onConfirm={() => remove(r)}>
                  <Button size="small" danger>删除</Button>
                </Popconfirm>
              </Space>
            ),
          },
        ]}
      />

      <Modal
        title={editing ? '编辑模板' : '新建模板'}
        open={open}
        onOk={save}
        onCancel={() => setOpen(false)}
        okText="保存"
        destroyOnClose
      >
        <Form form={form} layout="vertical" initialValues={{ enabled: true }}>
          <Form.Item name="name" label="模板名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input placeholder="如：市政府通知（下行文）" maxLength={60} />
          </Form.Item>
          <Form.Item name="redTitle" label="红头（发文机关标志）" rules={[{ required: true, message: '请输入红头' }]}>
            <Input placeholder="如：XX市人民政府文件" maxLength={60} />
          </Form.Item>
          <Form.Item name="noPrefix" label="文号前缀" rules={[{ required: true, message: '请输入文号前缀' }]}>
            <Input placeholder="如：市政发〔2025〕（编号自动追加序号）" maxLength={40} />
          </Form.Item>
          <Form.Item name="issuer" label="发文机关署名" rules={[{ required: true, message: '请输入署名' }]}>
            <Input placeholder="如：XX市人民政府办公室" maxLength={60} />
          </Form.Item>
          <Form.Item name="flowId" label="绑定流程" rules={[{ required: true, message: '请选择流程' }]}>
            <Select
              options={flows.filter((f) => f.enabled).map((f) => ({ value: f.id, label: `${f.name}（v${f.version}）` }))}
              placeholder="选择办理流程"
            />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={200} />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
