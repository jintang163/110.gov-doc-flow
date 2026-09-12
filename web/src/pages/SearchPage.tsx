import { useEffect, useState } from 'react'
import { Button, Card, Form, Input, Select, Space, Table, Tag } from 'antd'
import { useNavigate } from 'react-router-dom'
import client from '../api/client'
import { DocTemplate, Document, DOC_STATUS, SECRET_LEVELS } from '../api/types'

export default function SearchPage() {
  const [list, setList] = useState<Document[]>([])
  const [templates, setTemplates] = useState<DocTemplate[]>([])
  const [loading, setLoading] = useState(false)
  const [form] = Form.useForm()
  const navigate = useNavigate()

  const load = (values?: { status?: string; keyword?: string; templateId?: number }) => {
    setLoading(true)
    const params: Record<string, unknown> = {}
    if (values?.status) params.status = values.status
    if (values?.keyword) params.keyword = values.keyword
    if (values?.templateId) params.templateId = values.templateId
    client.get('/docs/search', { params }).then((d: any) => setList(d)).finally(() => setLoading(false))
  }

  useEffect(() => {
    client.get('/templates').then((d: any) => setTemplates(d))
    load()
  }, [])

  return (
    <Card title="公文查询">
      <Form form={form} layout="inline" onFinish={load} style={{ marginBottom: 16 }}>
        <Form.Item name="keyword">
          <Input placeholder="标题 / 文号关键词" allowClear style={{ width: 220 }} />
        </Form.Item>
        <Form.Item name="status">
          <Select
            placeholder="状态"
            allowClear
            style={{ width: 130 }}
            options={Object.entries(DOC_STATUS).map(([value, v]) => ({ value, label: v.text }))}
          />
        </Form.Item>
        <Form.Item name="templateId">
          <Select
            placeholder="模板"
            allowClear
            style={{ width: 220 }}
            options={templates.map((t) => ({ value: t.id, label: t.name }))}
          />
        </Form.Item>
        <Space>
          <Button type="primary" htmlType="submit">查询</Button>
          <Button onClick={() => { form.resetFields(); load() }}>重置</Button>
        </Space>
      </Form>
      <Table
        rowKey="id"
        loading={loading}
        dataSource={list}
        pagination={{ pageSize: 10 }}
        columns={[
          { title: '标题', dataIndex: 'title', render: (v, r) => <a onClick={() => navigate(`/docs/${r.id}`)}>{v}</a> },
          { title: '文号', dataIndex: 'docNo', width: 180, render: (v) => v || '—' },
          {
            title: '密级', dataIndex: 'secretLevel', width: 90,
            render: (v: number) => <Tag color={v >= 2 ? 'red' : 'default'}>{SECRET_LEVELS[v]}</Tag>,
          },
          {
            title: '状态', dataIndex: 'status', width: 110,
            render: (v: string) => <Tag color={DOC_STATUS[v]?.color}>{DOC_STATUS[v]?.text || v}</Tag>,
          },
          { title: '当前节点', dataIndex: 'currentNodeName', width: 120, render: (v) => v || '—' },
          { title: '更新时间', dataIndex: 'updatedAt', width: 170 },
        ]}
      />
    </Card>
  )
}
