import { useEffect, useState } from 'react'
import { Avatar, Badge, Button, Dropdown, Layout, Menu, Space, Tag } from 'antd'
import {
  BellOutlined,
  FileAddOutlined,
  FileDoneOutlined,
  FileProtectOutlined,
  FileSearchOutlined,
  FileTextOutlined,
  InboxOutlined,
  LogoutOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  TeamOutlined,
  ApartmentOutlined,
  NodeIndexOutlined,
} from '@ant-design/icons'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import client from '../api/client'
import type { UserInfo } from '../api/types'

const { Sider, Header, Content } = Layout

export function currentUser(): UserInfo | null {
  const raw = localStorage.getItem('user')
  return raw ? (JSON.parse(raw) as UserInfo) : null
}

export default function AppLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const [user, setUser] = useState<UserInfo | null>(currentUser())
  const [unread, setUnread] = useState(0)

  useEffect(() => {
    if (!localStorage.getItem('token')) {
      navigate('/login', { replace: true })
      return
    }
    client.get('/auth/me').then((u: any) => {
      setUser(u)
      localStorage.setItem('user', JSON.stringify(u))
    }).catch(() => {})
  }, [navigate])

  useEffect(() => {
    const load = () =>
      client.get('/notifications/unread-count').then((d: any) => setUnread(d.count)).catch(() => {})
    load()
    const timer = setInterval(load, 20000)
    return () => clearInterval(timer)
  }, [location.pathname])

  if (!user) return null

  const menuItems = [
    { key: '/', icon: <InboxOutlined />, label: '待办事项' },
    { key: '/done', icon: <FileDoneOutlined />, label: '已办事项' },
    { key: '/docs/new', icon: <FileAddOutlined />, label: '拟稿发文' },
    { key: '/my-docs', icon: <FileTextOutlined />, label: '我的公文' },
    { key: '/search', icon: <FileSearchOutlined />, label: '公文查询' },
    { key: '/archives', icon: <FileProtectOutlined />, label: '归档查询' },
    ...(user.admin
      ? [
          {
            key: 'admin',
            icon: <SettingOutlined />,
            label: '系统配置',
            children: [
              { key: '/admin/templates', icon: <FileTextOutlined />, label: '红头模板' },
              { key: '/admin/flows', icon: <NodeIndexOutlined />, label: '流程配置' },
              { key: '/admin/seals', icon: <SafetyCertificateOutlined />, label: '签章管理' },
              { key: '/admin/users', icon: <TeamOutlined />, label: '用户管理' },
              { key: '/admin/orgs', icon: <ApartmentOutlined />, label: '部门管理' },
            ],
          },
        ]
      : []),
  ]

  const selectedKey =
    location.pathname === '/'
      ? '/'
      : menuItems
          .flatMap((m: any) => (m.children ? m.children : [m]))
          .map((m: any) => m.key)
          .filter((k: string) => k !== '/' && location.pathname.startsWith(k))
          .sort((a: string, b: string) => b.length - a.length)[0] || location.pathname

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider theme="dark" width={220}>
        <div style={{ color: '#fff', fontSize: 16, fontWeight: 600, padding: '18px 16px', lineHeight: 1.4 }}>
          政务协同办公系统
          <div style={{ fontSize: 12, fontWeight: 400, opacity: 0.7 }}>公文流转引擎</div>
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selectedKey]}
          defaultOpenKeys={user.admin ? ['admin'] : []}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header style={{ background: '#fff', padding: '0 24px', display: 'flex', justifyContent: 'flex-end', alignItems: 'center', boxShadow: '0 1px 4px rgba(0,21,41,.08)', zIndex: 1 }}>
          <Space size="large">
            <Badge count={unread} size="small">
              <Button type="text" icon={<BellOutlined style={{ fontSize: 18 }} />} onClick={() => navigate('/notifications')} />
            </Badge>
            <Dropdown
              menu={{
                items: [{ key: 'logout', icon: <LogoutOutlined />, label: '退出登录' }],
                onClick: () => {
                  client.post('/auth/logout').catch(() => {})
                  localStorage.removeItem('token')
                  localStorage.removeItem('user')
                  navigate('/login', { replace: true })
                },
              }}
            >
              <Space style={{ cursor: 'pointer' }}>
                <Avatar style={{ background: '#c00' }}>{user.name.slice(0, 1)}</Avatar>
                <span>{user.name}</span>
                <Tag color="red">{user.orgName}</Tag>
                {user.admin && <Tag color="gold">管理员</Tag>}
              </Space>
            </Dropdown>
          </Space>
        </Header>
        <Content style={{ margin: 16 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}
