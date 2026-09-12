import { useEffect, useState } from 'react'
import { Badge, Button, Card, List, Space, Tag, Typography } from 'antd'
import { useNavigate } from 'react-router-dom'
import client from '../api/client'
import { Notification } from '../api/types'

const TYPE_TEXT: Record<string, { text: string; color: string }> = {
  TODO: { text: '待办', color: 'processing' },
  RETURN: { text: '退回', color: 'warning' },
  URGE: { text: '催办', color: 'gold' },
  ARCHIVE: { text: '归档', color: 'success' },
  OVERDUE: { text: '超时', color: 'red' },
}

export default function NotificationsPage() {
  const [list, setList] = useState<Notification[]>([])
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()

  const load = () => {
    setLoading(true)
    client.get('/notifications').then((d: any) => setList(d)).finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
  }, [])

  const readAll = async () => {
    await client.post('/notifications/read-all')
    load()
  }

  const open = async (n: Notification) => {
    if (!n.readFlag) {
      await client.post(`/notifications/${n.id}/read`).catch(() => {})
    }
    if (n.docId) navigate(`/docs/${n.docId}`)
    else load()
  }

  return (
    <Card
      title="通知中心"
      extra={
        <Space>
          <Button onClick={readAll}>全部已读</Button>
          <Button onClick={load}>刷新</Button>
        </Space>
      }
    >
      <List
        loading={loading}
        dataSource={list}
        pagination={{ pageSize: 15 }}
        renderItem={(n) => (
          <List.Item
            style={{ cursor: 'pointer', opacity: n.readFlag ? 0.65 : 1 }}
            onClick={() => open(n)}
          >
            <List.Item.Meta
              title={
                <Space>
                  <Badge status={n.readFlag ? 'default' : 'processing'} />
                  <Tag color={TYPE_TEXT[n.type]?.color}>{TYPE_TEXT[n.type]?.text || n.type}</Tag>
                  <span>{n.title}</span>
                </Space>
              }
              description={n.content}
            />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>{n.createdAt}</Typography.Text>
          </List.Item>
        )}
      />
    </Card>
  )
}
