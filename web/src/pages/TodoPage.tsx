import { useEffect, useState } from 'react'
import { Button, Card, Table, Tag } from 'antd'
import { useNavigate } from 'react-router-dom'
import client from '../api/client'
import { SECRET_LEVELS, TaskItem } from '../api/types'

export default function TodoPage() {
  const [list, setList] = useState<TaskItem[]>([])
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()

  const load = () => {
    setLoading(true)
    client.get('/docs/tasks/todo').then((d: any) => setList(d)).finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    const timer = setInterval(load, 20000)
    return () => clearInterval(timer)
  }, [])

  return (
    <Card title="待办事项" extra={<Button onClick={load}>刷新</Button>}>
      <Table
        rowKey="id"
        loading={loading}
        dataSource={list}
        pagination={{ pageSize: 10 }}
        columns={[
          { title: '标题', dataIndex: 'title', render: (v, r) => <a onClick={() => navigate(`/docs/${r.docId}`)}>{v}</a> },
          { title: '当前节点', dataIndex: 'nodeName', width: 130 },
          {
            title: '密级', dataIndex: 'secretLevel', width: 90,
            render: (v: number) => <Tag color={v >= 2 ? 'red' : 'default'}>{SECRET_LEVELS[v]}</Tag>,
          },
          {
            title: '状态', key: 'overdue', width: 100,
            render: (_, r) => (r.overdue ? <Tag color="red">已超时</Tag> : <Tag color="processing">待办理</Tag>),
          },
          { title: '到达时间', dataIndex: 'createdAt', width: 170 },
          {
            title: '操作', key: 'op', width: 100,
            render: (_, r) => <Button type="primary" size="small" onClick={() => navigate(`/docs/${r.docId}`)}>办理</Button>,
          },
        ]}
      />
    </Card>
  )
}
