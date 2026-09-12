import { useEffect, useState } from 'react'
import { Button, Card, DatePicker, Form, Input, Select, Space, Table, Tag } from 'antd'
import { useNavigate } from 'react-router-dom'
import client from '../api/client'
import { DocTemplate, Document, SECRET_LEVELS } from '../api/types'

interface ArchiveQuery {
  keyword?: string
  docNo?: string
  templateId?: number
  secretLevel?: number
  range?: [{ format: (f: string) => string }, { format: (f: string) => string }]
}

export default function ArchivePage() {
  const [list, setList] = useState<Document[]>([])
  const [templates, setTemplates] = useState<DocTemplate[]>([])
  const [loading, setLoading] = useState(false)
  const [form] = Form.useForm()
  const navigate = useNavigate()

  const load = (values?: ArchiveQuery) => {
    setLoading(true)
    const params: Record<string, unknown> = {}
    if (values?.keyword) params.keyword = values.keyword
    if (values?.docNo) params.docNo = values.docNo
    if (values?.templateId) params.templateId = values.templateId
    if (values?.secretLevel !== undefined && values?.secretLevel !== null) params.secretLevel = values.secretLevel
    if (values?.range?.[0]) params.from = values.range[0].format('YYYY-MM-DD')
    if (values?.range?.[1]) params.to = values.range[1].format('YYYY-MM-DD')
    client.get('/docs/archives', { params }).then((d: any) => setList(d)).finally(() => setLoading(false))
  }

  useEffect(() => {
    client.get('/templates', { params: { all: true } }).then((d: any) => setTemplates(d))
    load()
  }, [])

  return (
    <Card title="归档查询">
      <Form form={form} layout="inline" onFinish={load} style={{ marginBottom: 16, rowGap: 8 }}>
        <Form.Item name="keyword">
          <Input placeholder="标题关键词" allowClear style={{ width: 180 }} />
        </Form.Item>
        <Form.Item name="docNo">
          <Input placeholder="文号" allowClear style={{ width: 160 }} />
        </Form.Item>
        <Form.Item name="templateId">
          <Select
            placeholder="模板"
            allowClear
            style={{ width: 200 }}
            options={templates.map((t) => ({ value: t.id, label: t.name }))}
          />
        </Form.Item>
        <Form.Item name="secretLevel">
          <Select
            placeholder="密级"
            allowClear
            style={{ width: 110 }}
            options={SECRET_LEVELS.map((s, i) => ({ value: i, label: s }))}
          />
        </Form.Item>
        <Form.Item name="range">
          <DatePicker.RangePicker placeholder={['归档起', '归档止']} />
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
          { title: '文号', dataIndex: 'docNo', width: 180 },
          {
            title: '密级', dataIndex: 'secretLevel', width: 90,
            render: (v: number) => <Tag color={v >= 2 ? 'red' : 'default'}>{SECRET_LEVELS[v]}</Tag>,
          },
          { title: '签发时间', dataIndex: 'issuedAt', width: 170 },
          { title: '归档时间', dataIndex: 'archivedAt', width: 170 },
          {
            title: '操作', key: 'op', width: 90,
            render: (_, r) => <Button size="small" onClick={() => navigate(`/docs/${r.id}`)}>查看</Button>,
          },
        ]}
      />
    </Card>
  )
}
