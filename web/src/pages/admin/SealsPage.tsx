import { useEffect, useState } from 'react'
import {
  Button, Card, Form, Image, Input, Modal, Popconfirm, Radio, Select,
  Space, Table, Tag, Upload, message,
} from 'antd'
import { PlusOutlined, UploadOutlined } from '@ant-design/icons'
import client from '../../api/client'
import { Org, Seal, UserInfo } from '../../api/types'

function sealImageUrl(id: number) {
  return `/api/seals/${id}/image?token=${localStorage.getItem('token')}`
}

export default function SealsPage() {
  const [list, setList] = useState<Seal[]>([])
  const [users, setUsers] = useState<UserInfo[]>([])
  const [orgs, setOrgs] = useState<Org[]>([])
  const [loading, setLoading] = useState(false)
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm()
  const ownerType = Form.useWatch('ownerType', form)

  const load = () => {
    setLoading(true)
    client.get('/seals').then((d: any) => setList(d)).finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    client.get('/users').then((d: any) => setUsers(d))
    client.get('/orgs').then((d: any) => setOrgs(d))
  }, [])

  const ownerName = (s: Seal) => {
    if (s.ownerType === 'ORG') return orgs.find((o) => o.id === s.ownerId)?.name || `#${s.ownerId}`
    const u = users.find((x) => x.id === s.ownerId)
    return u ? u.name : `#${s.ownerId}`
  }

  const create = async () => {
    const values = await form.validateFields()
    await client.post('/seals', values)
    message.success('已创建，请生成或上传章图')
    setOpen(false)
    load()
  }

  const generate = async (s: Seal) => {
    await client.post(`/seals/${s.id}/generate`)
    message.success('章图已生成')
    load()
  }

  const toggle = async (s: Seal) => {
    await client.put(`/seals/${s.id}/toggle`)
    load()
  }

  const remove = async (s: Seal) => {
    await client.delete(`/seals/${s.id}`)
    message.success('已删除')
    load()
  }

  return (
    <Card
      title="签章管理"
      extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => { form.resetFields(); setOpen(true) }}>新建签章</Button>}
    >
      <Table
        rowKey="id"
        loading={loading}
        dataSource={list}
        pagination={false}
        columns={[
          { title: '签章名称', dataIndex: 'name' },
          {
            title: '类型', dataIndex: 'ownerType', width: 100,
            render: (v: string) => (v === 'ORG' ? <Tag color="red">单位章</Tag> : <Tag color="blue">个人章</Tag>),
          },
          { title: '归属', key: 'owner', width: 160, render: (_, r) => ownerName(r) },
          {
            title: '章图', dataIndex: 'imageKey', width: 110,
            render: (v, r) =>
              v ? (
                <Image src={sealImageUrl(r.id)} width={56} height={56} style={{ objectFit: 'contain' }} />
              ) : (
                <Tag color="orange">未生成</Tag>
              ),
          },
          {
            title: '状态', dataIndex: 'enabled', width: 90,
            render: (v: boolean) => (v ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>),
          },
          {
            title: '操作', key: 'op', width: 320,
            render: (_, r) => (
              <Space wrap>
                <Button size="small" onClick={() => generate(r)}>生成章图</Button>
                <Upload
                  showUploadList={false}
                  accept="image/png"
                  customRequest={async ({ file, onSuccess, onError }) => {
                    const fd = new FormData()
                    fd.append('file', file as File)
                    try {
                      await client.post(`/seals/${r.id}/image`, fd, {
                        headers: { 'Content-Type': 'multipart/form-data' },
                      })
                      message.success('章图已上传')
                      onSuccess?.({})
                      load()
                    } catch (e) {
                      onError?.(e as Error)
                    }
                  }}
                >
                  <Button size="small" icon={<UploadOutlined />}>上传</Button>
                </Upload>
                <Button size="small" onClick={() => toggle(r)}>{r.enabled ? '停用' : '启用'}</Button>
                <Popconfirm title="删除该签章？" onConfirm={() => remove(r)}>
                  <Button size="small" danger>删除</Button>
                </Popconfirm>
              </Space>
            ),
          },
        ]}
      />

      <Modal title="新建签章" open={open} onOk={create} onCancel={() => setOpen(false)} okText="创建" destroyOnClose>
        <Form form={form} layout="vertical" initialValues={{ ownerType: 'ORG' }}>
          <Form.Item name="name" label="签章名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input placeholder="如：XX市人民政府办公室 / 张三（个人名章）" maxLength={60} />
          </Form.Item>
          <Form.Item name="ownerType" label="签章类型" rules={[{ required: true }]}>
            <Radio.Group
              options={[
                { value: 'ORG', label: '单位章' },
                { value: 'USER', label: '个人章' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="ownerId"
            label={ownerType === 'ORG' ? '归属单位' : '归属人员'}
            rules={[{ required: true, message: '请选择归属' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              options={
                ownerType === 'ORG'
                  ? orgs.map((o) => ({ value: o.id, label: o.name }))
                  : users.map((u) => ({ value: u.id, label: `${u.name}（${u.username}）` }))
              }
            />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
