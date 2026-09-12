import { useEffect, useState } from 'react'
import { Button, Card, Table, Tag } from 'antd'
import { useNavigate } from 'react-router-dom'
import client from '../api/client'
import { DOC_STATUS, TaskItem } from '../api/types'

const ACTION_TEXT: Record<string, string> = {
  APPROVE: '同意',
  RETURN_PREV: '退回上一步',
  RETURN_DRAFT: '退回拟稿',
}

export default function DonePage() {
  const [list, setList] = useState<TaskItem[]>([])
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()

  useEffect(() => {
    setLoading(true)
    client.get('/docs/tasks/done').then((d: any) => setList(d)).finally(() => setLoading(false))
  }, [])

  return (
    <Card title="已办事项">
      <Table
        rowKey="id"
        loading={loading}
        dataSource={list}
        pagination={{ pageSize: 10 }}
        columns={[
          { title: '标题', dataIndex: 'title', render: (v, r) => <a onClick={() => navigate(`/docs/${r.docId}`)}>{v}</a> },
          { title: '办理节点', dataIndex: 'nodeName', width: 120 },
          {
            title: '办理意见', dataIndex: 'action', width: 120,
            render: (v, r) => (
              <Tag color={v === 'APPROVE' ? 'green' : 'orange'}>{ACTION_TEXT[v] || v}</Tag>
            ),
          },
          { title: '意见', dataIndex: 'comment', ellipsis: true },
          {
            title: '公文状态', dataIndex: 'docStatus', width: 100,
            render: (v: string) => <Tag color={DOC_STATUS[v]?.color}>{DOC_STATUS[v]?.text || v}</Tag>,
          },
          { title: '办理时间', dataIndex: 'doneAt', width: 170 },
          {
            title: '操作', key: 'op', width: 90,
            render: (_, r) => <Button size="small" onClick={() => navigate(`/docs/${r.docId}`)}>查看</Button>,
          },
        ]}
      />
    </Card>
  )
}
