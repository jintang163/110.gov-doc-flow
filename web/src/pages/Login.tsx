import { Button, Card, Form, Input, Typography } from 'antd'
import { useNavigate } from 'react-router-dom'
import client from '../api/client'

export default function Login() {
  const navigate = useNavigate()

  const onFinish = async (values: { username: string; password: string }) => {
    const data: any = await client.post('/auth/login', values)
    localStorage.setItem('token', data.token)
    localStorage.setItem('user', JSON.stringify(data.user))
    navigate('/', { replace: true })
  }

  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'linear-gradient(135deg,#8b0000 0%,#c0392b 100%)' }}>
      <Card style={{ width: 400, boxShadow: '0 8px 32px rgba(0,0,0,.2)' }}>
        <Typography.Title level={3} style={{ textAlign: 'center', color: '#c00', marginBottom: 4 }}>
          政务协同办公系统
        </Typography.Title>
        <Typography.Paragraph style={{ textAlign: 'center', color: '#888', marginBottom: 24 }}>
          公文流转引擎 · 拟稿 审核 会签 签发 归档
        </Typography.Paragraph>
        <Form layout="vertical" onFinish={onFinish} initialValues={{ username: 'zhangsan', password: '123456' }}>
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input size="large" placeholder="用户名" autoFocus />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password size="large" placeholder="密码" />
          </Form.Item>
          <Button type="primary" htmlType="submit" size="large" block>
            登 录
          </Button>
        </Form>
        <Typography.Paragraph type="secondary" style={{ fontSize: 12, marginTop: 16, marginBottom: 0 }}>
          演示账号：admin/admin123（管理员）；zhangsan 拟稿、lisi 审核、wangwu/zhaoliu 会签、qianqi 签发，密码均 123456
        </Typography.Paragraph>
      </Card>
    </div>
  )
}
