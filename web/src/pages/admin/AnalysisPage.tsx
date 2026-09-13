import { useCallback, useEffect, useState } from 'react'
import {
  Button, Card, Col, DatePicker, Progress, Radio, Row, Space, Statistic,
  Table, Tabs, Tag, Typography, message,
} from 'antd'
import { ReloadOutlined, DownloadOutlined } from '@ant-design/icons'
import dayjs, { Dayjs } from 'dayjs'
import client from '../../api/client'
import EChart from '../../components/EChart'
import type {
  AnalysisOverview, EfficiencyRow, NodeDurationRow, RankingRow,
  ReminderItem, ReturnAnalysis, TrendRow,
} from '../../api/types'

const PALETTE = ['#c00', '#1677ff', '#fa8c16', '#52c41a', '#722ed1', '#13c2c2', '#eb2f96', '#faad14']

/** 效能看板：办理时效 / 退回热点 / 效能排行 / 督办列表（数据来自分析表，定时抽取） */
export default function AnalysisPage() {
  const [refreshKey, setRefreshKey] = useState(0)
  const [refreshing, setRefreshing] = useState(false)

  const refresh = async () => {
    setRefreshing(true)
    try {
      const d: any = await client.post('/analysis/refresh')
      message.success(`分析数据已刷新：${d.docs} 篇公文，新增督办单 ${d.reminders} 张`)
      setRefreshKey((k) => k + 1)
    } finally {
      setRefreshing(false)
    }
  }

  return (
    <Card
      title="效能看板"
      extra={
        <Button icon={<ReloadOutlined />} loading={refreshing} onClick={refresh}>
          刷新分析数据
        </Button>
      }
    >
      <Tabs
        items={[
          { key: 'efficiency', label: '办理时效', children: <EfficiencyTab refreshKey={refreshKey} /> },
          { key: 'returns', label: '退回热点', children: <ReturnsTab refreshKey={refreshKey} /> },
          { key: 'ranking', label: '效能排行', children: <RankingTab refreshKey={refreshKey} /> },
          { key: 'reminders', label: '督办列表', children: <RemindersTab refreshKey={refreshKey} /> },
        ]}
      />
    </Card>
  )
}

// ==================== 办理时效 ====================

function EfficiencyTab({ refreshKey }: { refreshKey: number }) {
  const [overview, setOverview] = useState<AnalysisOverview | null>(null)
  const [dim, setDim] = useState<'org' | 'post' | 'template'>('org')
  const [efficiency, setEfficiency] = useState<EfficiencyRow[]>([])
  const [trend, setTrend] = useState<TrendRow[]>([])
  const [nodes, setNodes] = useState<NodeDurationRow[]>([])

  useEffect(() => {
    client.get('/analysis/overview').then((d: any) => setOverview(d))
    client.get('/analysis/trend?months=6').then((d: any) => setTrend(d))
    client.get('/analysis/nodes').then((d: any) => setNodes(d))
  }, [refreshKey])

  useEffect(() => {
    client.get(`/analysis/efficiency?dim=${dim}`).then((d: any) => setEfficiency(d))
  }, [dim, refreshKey])

  const barOption = {
    tooltip: { trigger: 'axis' },
    legend: {},
    grid: { left: 48, right: 24, top: 40, bottom: 32 },
    xAxis: { type: 'category', data: efficiency.map((r) => r.name) },
    yAxis: { type: 'value', name: '小时' },
    series: [
      { name: '拟稿时长', type: 'bar', data: efficiency.map((r) => r.avgDraftHours), itemStyle: { color: PALETTE[1] } },
      { name: '办理时长', type: 'bar', data: efficiency.map((r) => r.avgHandleHours), itemStyle: { color: PALETTE[2] } },
      { name: '会签周期', type: 'bar', data: efficiency.map((r) => r.avgCountersignHours), itemStyle: { color: PALETTE[3] } },
      { name: '全程时长', type: 'bar', data: efficiency.map((r) => r.avgTotalHours), itemStyle: { color: PALETTE[0] } },
    ],
  }

  const trendOption = {
    tooltip: { trigger: 'axis' },
    legend: {},
    grid: { left: 48, right: 48, top: 40, bottom: 32 },
    xAxis: { type: 'category', data: trend.map((r) => r.month) },
    yAxis: [
      { type: 'value', name: '小时' },
      { type: 'value', name: '件', minInterval: 1 },
    ],
    series: [
      { name: '平均全程时长', type: 'line', smooth: true, data: trend.map((r) => r.avgTotalHours), itemStyle: { color: PALETTE[0] } },
      { name: '平均拟稿时长', type: 'line', smooth: true, data: trend.map((r) => r.avgDraftHours), itemStyle: { color: PALETTE[1] } },
      { name: '办结量', type: 'bar', yAxisIndex: 1, data: trend.map((r) => r.count), itemStyle: { color: '#91caff' } },
    ],
  }

  const nodeOption = {
    tooltip: { trigger: 'axis' },
    legend: {},
    grid: { left: 8, right: 40, top: 40, bottom: 24, containLabel: true },
    xAxis: { type: 'value', name: '小时' },
    yAxis: { type: 'category', data: nodes.map((r) => r.name).reverse() },
    series: [
      { name: '平均停留', type: 'bar', data: nodes.map((r) => r.avgHours).reverse(), itemStyle: { color: PALETTE[2] } },
      { name: '最长停留', type: 'bar', data: nodes.map((r) => r.maxHours).reverse(), itemStyle: { color: '#ffd591' } },
    ],
  }

  return (
    <>
      {overview && (
        <Row gutter={16} style={{ marginBottom: 16 }}>
          <Col span={4}><Card size="small"><Statistic title="流转公文（篇）" value={overview.total} /></Card></Col>
          <Col span={4}><Card size="small"><Statistic title="已办结" value={overview.archived} /></Card></Col>
          <Col span={4}><Card size="small"><Statistic title="在办" value={overview.running} /></Card></Col>
          <Col span={4}><Card size="small"><Statistic title="平均全程时长（小时）" value={overview.avgTotalHours} /></Card></Col>
          <Col span={4}><Card size="small"><Statistic title="退回率（%）" value={overview.returnRate} /></Card></Col>
          <Col span={4}>
            <Card size="small">
              <Statistic title="待处理督办单" value={overview.openReminders} valueStyle={{ color: overview.openReminders > 0 ? '#c00' : undefined }} />
            </Card>
          </Col>
        </Row>
      )}

      <Card
        size="small"
        title="办理时效统计（按办结件平均，小时）"
        extra={
          <Radio.Group
            value={dim}
            onChange={(e) => setDim(e.target.value)}
            options={[
              { value: 'org', label: '按部门' },
              { value: 'post', label: '按岗位' },
              { value: 'template', label: '按公文类型' },
            ]}
            optionType="button"
            size="small"
          />
        }
      >
        <EChart option={barOption} height={300} />
      </Card>

      <Row gutter={16} style={{ marginTop: 16 }}>
        <Col span={12}>
          <Card size="small" title="办结趋势（近 6 个月）">
            <EChart option={trendOption} height={280} />
          </Card>
        </Col>
        <Col span={12}>
          <Card size="small" title="各节点停留时长">
            <EChart option={nodeOption} height={280} />
          </Card>
        </Col>
      </Row>
    </>
  )
}

// ==================== 退回热点 ====================

function ReturnsTab({ refreshKey }: { refreshKey: number }) {
  const [data, setData] = useState<ReturnAnalysis | null>(null)

  useEffect(() => {
    client.get('/analysis/returns').then((d: any) => setData(d))
  }, [refreshKey])

  if (!data) return null

  const cloudOption = {
    tooltip: {},
    series: [
      {
        type: 'wordCloud',
        shape: 'circle',
        sizeRange: [14, 52],
        rotationRange: [-45, 45],
        gridSize: 8,
        textStyle: {
          color: () => PALETTE[Math.floor(Math.random() * PALETTE.length)],
        },
        emphasis: { textStyle: { fontWeight: 'bold' } },
        data: data.wordCloud.map((w) => ({ name: w.name, value: w.value })),
      },
    ],
  }

  return (
    <>
      <Row gutter={16}>
        <Col span={12}>
          <Card size="small" title={`退回原因词云（共 ${data.total} 次退回）`}>
            {data.wordCloud.length > 0 ? (
              <EChart option={cloudOption} height={320} />
            ) : (
              <Typography.Text type="secondary">暂无退回记录</Typography.Text>
            )}
          </Card>
        </Col>
        <Col span={12}>
          <Card size="small" title="退回原因占比排行">
            {data.categories.length === 0 && <Typography.Text type="secondary">暂无退回记录</Typography.Text>}
            {data.categories.map((c) => (
              <div key={c.name} style={{ marginBottom: 12 }}>
                <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                  <span>{c.name}</span>
                  <span>{c.count} 次（{c.percent}%）</span>
                </Space>
                <Progress percent={c.percent} showInfo={false} strokeColor={PALETTE[0]} />
              </div>
            ))}
          </Card>
        </Col>
      </Row>
      <Card size="small" title="最近退回意见" style={{ marginTop: 16 }}>
        <Table
          rowKey={(r) => `${r.docId}-${r.at}`}
          size="small"
          dataSource={data.recent}
          pagination={false}
          columns={[
            { title: '公文', dataIndex: 'title', ellipsis: true },
            { title: '节点', dataIndex: 'nodeName', width: 120 },
            { title: '操作人', dataIndex: 'actorName', width: 100 },
            { title: '退回意见', dataIndex: 'comment', ellipsis: true },
            { title: '归类', dataIndex: 'category', width: 100, render: (_, r) => <Tag>{r.category}</Tag> },
            { title: '时间', dataIndex: 'at', width: 170 },
          ]}
        />
      </Card>
    </>
  )
}

// ==================== 效能排行 ====================

function RankingTab({ refreshKey }: { refreshKey: number }) {
  const [month, setMonth] = useState<Dayjs>(dayjs())
  const [type, setType] = useState<'org' | 'user'>('org')
  const [rows, setRows] = useState<RankingRow[]>([])
  const [loading, setLoading] = useState(false)

  const load = useCallback(() => {
    setLoading(true)
    client
      .get(`/analysis/ranking?month=${month.format('YYYY-MM')}&type=${type}`)
      .then((d: any) => setRows(d))
      .finally(() => setLoading(false))
  }, [month, type])

  useEffect(load, [load, refreshKey])

  const exportCsv = () => {
    const token = localStorage.getItem('token')
    window.open(`/api/analysis/ranking/export?month=${month.format('YYYY-MM')}&type=${type}&token=${token}`)
  }

  const columns =
    type === 'org'
      ? [
          { title: '排名', dataIndex: 'rank', width: 70, render: (v: number) => rankTag(v) },
          { title: '部门', dataIndex: 'name' },
          { title: '办结数量（件）', dataIndex: 'count', width: 130 },
          { title: '平均全程时长（小时）', dataIndex: 'avgTotalHours', width: 170 },
          { title: '平均拟稿时长（小时）', dataIndex: 'avgDraftHours', width: 170 },
          { title: '退回次数', dataIndex: 'returnCount', width: 100 },
        ]
      : [
          { title: '排名', dataIndex: 'rank', width: 70, render: (v: number) => rankTag(v) },
          { title: '姓名', dataIndex: 'name' },
          { title: '部门', dataIndex: 'orgName' },
          { title: '办理数量（件）', dataIndex: 'count', width: 130 },
          { title: '平均办理时长（小时）', dataIndex: 'avgHours', width: 170 },
        ]

  return (
    <Card
      size="small"
      title="效能排行（按月）"
      extra={
        <Space>
          <DatePicker.MonthPicker
            value={month}
            onChange={(d) => d && setMonth(d)}
            allowClear={false}
            size="small"
          />
          <Radio.Group
            value={type}
            onChange={(e) => setType(e.target.value)}
            options={[
              { value: 'org', label: '部门排行' },
              { value: 'user', label: '个人排行' },
            ]}
            optionType="button"
            size="small"
          />
          <Button size="small" icon={<DownloadOutlined />} onClick={exportCsv}>
            导出报表
          </Button>
        </Space>
      }
    >
      <Table rowKey="name" size="small" loading={loading} dataSource={rows} pagination={false} columns={columns as any} />
    </Card>
  )
}

function rankTag(rank: number) {
  if (rank === 1) return <Tag color="gold">1</Tag>
  if (rank === 2) return <Tag color="geekblue">2</Tag>
  if (rank === 3) return <Tag color="volcano">3</Tag>
  return <Tag>{rank}</Tag>
}

// ==================== 督办列表 ====================

function RemindersTab({ refreshKey }: { refreshKey: number }) {
  const [status, setStatus] = useState<string>('')
  const [rows, setRows] = useState<ReminderItem[]>([])
  const [loading, setLoading] = useState(false)

  const load = useCallback(() => {
    setLoading(true)
    client
      .get(`/analysis/reminders${status ? `?status=${status}` : ''}`)
      .then((d: any) => setRows(d))
      .finally(() => setLoading(false))
  }, [status])

  useEffect(load, [load, refreshKey])

  const handle = async (r: ReminderItem) => {
    await client.post(`/analysis/reminders/${r.id}/handle`)
    message.success('已标记为已处理')
    load()
  }

  return (
    <Card
      size="small"
      title="督办单（超时未办结 / 催办≥3次自动生成，已推送分管领导）"
      extra={
        <Radio.Group
          value={status}
          onChange={(e) => setStatus(e.target.value)}
          options={[
            { value: '', label: '全部' },
            { value: 'OPEN', label: '未处理' },
            { value: 'HANDLED', label: '已处理' },
          ]}
          optionType="button"
          size="small"
        />
      }
    >
      <Table
        rowKey="id"
        size="small"
        loading={loading}
        dataSource={rows}
        pagination={{ pageSize: 10 }}
        columns={[
          {
            title: '公文', dataIndex: 'title', ellipsis: true,
            render: (v: string, r) => (
              <Space size={4}>
                <a href={`/docs/${r.docId}`}>{v}</a>
                {r.docNo && <Typography.Text type="secondary" style={{ fontSize: 12 }}>{r.docNo}</Typography.Text>}
              </Space>
            ),
          },
          { title: '拟稿部门', dataIndex: 'orgName', width: 130 },
          { title: '督办原因', dataIndex: 'reason', width: 200, render: (v: string) => <Tag color="red">{v}</Tag> },
          { title: '超时（天）', dataIndex: 'overdueDays', width: 90 },
          { title: '催办（次）', dataIndex: 'urgeCount', width: 90 },
          { title: '当前节点', dataIndex: 'currentNodeName', width: 110 },
          { title: '责任人', dataIndex: 'assigneeNames', width: 120 },
          { title: '推送领导', dataIndex: 'leaderName', width: 100 },
          { title: '生成时间', dataIndex: 'createdAt', width: 165 },
          {
            title: '状态', dataIndex: 'status', width: 90,
            render: (v: string) => (v === 'OPEN' ? <Tag color="processing">未处理</Tag> : <Tag color="success">已处理</Tag>),
          },
          {
            title: '操作', key: 'op', width: 100,
            render: (_, r) =>
              r.status === 'OPEN' ? (
                <Button size="small" onClick={() => handle(r)}>标记已处理</Button>
              ) : null,
          },
        ]}
      />
    </Card>
  )
}
