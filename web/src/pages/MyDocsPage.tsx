import { useEffect, useState } from 'react'
import { Button, Card, Space, Table, Tag } from 'antd'
import { useNavigate } from 'react-router-dom'
import client from '../api/client'
import { DOC_STATUS, Document } from '../api/types'

export default function MyDocsPage() {
  const [list, setList] = useState<Document[]>([])
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()

  useEffect(() => {
    setLoading(true)
    client.get('/docs/mine').then((d: any) => setList(d)).finally(() => setLoading(false))
  }, [])

  return (
    <Card
      title="我的公文"
      extra={<Button type="primary" onClick={() => navigate('/docs/new')}>新建拟稿</Button>}
    >
      <Table
        rowKey="id"
        loading={loading}
        dataSource={list}
        pagination={{ pageSize: 10 }}
        columns={[
          { title: '标题', dataIndex: 'title', render: (v, r) => <a onClick={() => navigate(`/docs/${r.id}`)}>{v}</a> },
          { title: '文号', dataIndex: 'docNo', width: 180, render: (v) => v || '—' },
          {
            title: '状态', dataIndex: 'status', width: 110,
            render: (v: string) => <Tag color={DOC_STATUS[v]?.color}>{DOC_STATUS[v]?.text || v}</Tag>,
          },
          { title: '当前节点', dataIndex: 'currentNodeName', width: 120, render: (v) => v || '—' },
          { title: '更新时间', dataIndex: 'updatedAt', width: 170 },
          {
            title: '操作', key: 'op', width: 150,
            render: (_, r) => (
              <Space>
                <Button size="small" onClick={() => navigate(`/docs/${r.id}`)}>查看</Button>
                {(r.status === 'DRAFT' || r.status === 'RETURNED') && (
                  <Button size="small" type="primary" onClick={() => navigate(`/docs/${r.id}/edit`)}>
                    {r.status === 'RETURNED' ? '补正' : '编辑'}
                  </Button>
                )}
              </Space>
            ),
          },
        ]}
      />
    </Card>
  )
}
