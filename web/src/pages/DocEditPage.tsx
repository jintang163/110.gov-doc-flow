import { useEffect, useState } from 'react'
import { Button, Card, Form, Input, Select, Space, Steps, Upload, message } from 'antd'
import { UploadOutlined } from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import client from '../api/client'
import { Attachment, DocTemplate, FlowNode, NODE_MODE_TEXT, NODE_TYPE_TEXT } from '../api/types'

export default function DocEditPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [form] = Form.useForm()
  const [templates, setTemplates] = useState<DocTemplate[]>([])
  const [flowNodes, setFlowNodes] = useState<FlowNode[]>([])
  const [attachments, setAttachments] = useState<Attachment[]>([])
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    client.get('/templates').then((d: any) => setTemplates(d))
  }, [])

  // 编辑模式：加载草稿
  useEffect(() => {
    if (!id) return
    client.get(`/docs/${id}`).then((d: any) => {
      const doc = d.doc
      form.setFieldsValue({
        title: doc.title,
        templateId: doc.templateId,
        secretLevel: doc.secretLevel,
        mainSend: doc.mainSend,
        copySend: doc.copySend,
        content: doc.content,
      })
      setAttachments(JSON.parse(doc.attachmentsJson || '[]'))
      setFlowNodes(d.flowNodes || [])
    })
  }, [id])

  const onTemplateChange = async (templateId: number) => {
    const tpl = templates.find((t) => t.id === templateId)
    if (!tpl) return
    const flow: any = await client.get(`/flows/${tpl.flowId}`)
    setFlowNodes(JSON.parse(flow.nodesJson))
  }

  const save = async (submit: boolean) => {
    const values = await form.validateFields()
    setSubmitting(true)
    try {
      const payload = { ...values, attachments }
      const doc: any = id
        ? await client.put(`/docs/${id}`, payload)
        : await client.post('/docs', payload)
      if (submit) {
        await client.post(`/docs/${doc.id}/submit`)
        message.success('已提交，进入流转')
        navigate(`/docs/${doc.id}`)
      } else {
        message.success('草稿已保存')
        navigate('/my-docs')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Card title={id ? '编辑 / 补正' : '拟稿发文'}>
      <Form form={form} layout="vertical" style={{ maxWidth: 860 }} initialValues={{ secretLevel: 1 }}>
        <Form.Item name="templateId" label="红头模板" rules={[{ required: true, message: '请选择模板' }]}>
          <Select
            placeholder="选择发文模板（决定红头、文号与办理流程）"
            onChange={onTemplateChange}
            options={templates.map((t) => ({ value: t.id, label: `${t.name}（${t.noPrefix}）` }))}
          />
        </Form.Item>
        {flowNodes.length > 0 && (
          <Form.Item label="办理流程">
            <Steps
              size="small"
              items={[
                { title: '拟稿' },
                ...flowNodes.map((n) => ({
                  title: n.name,
                  description: `${NODE_TYPE_TEXT[n.type]} · ${NODE_MODE_TEXT[n.mode]}`,
                })),
                { title: '归档' },
              ]}
            />
          </Form.Item>
        )}
        <Form.Item name="title" label="公文标题" rules={[{ required: true, message: '请输入标题' }]}>
          <Input placeholder="如：关于印发 XX 工作方案的通知" maxLength={120} showCount />
        </Form.Item>
        <Space.Compact block>
          <Form.Item name="secretLevel" label="密级" style={{ width: 220, marginRight: 16 }} rules={[{ required: true }]}>
            <Select
              options={[
                { value: 0, label: '公开' },
                { value: 1, label: '内部' },
                { value: 2, label: '秘密' },
                { value: 3, label: '机密' },
              ]}
            />
          </Form.Item>
          <Form.Item name="mainSend" label="主送单位" style={{ flex: 1 }}>
            <Input placeholder="如：市发展改革委，市财政局" />
          </Form.Item>
        </Space.Compact>
        <Form.Item name="copySend" label="抄送单位">
          <Input placeholder="如：市政府办公室" />
        </Form.Item>
        <Form.Item name="content" label="正文" rules={[{ required: true, message: '请输入正文' }]}>
          <Input.TextArea rows={12} placeholder="正文内容，每段一行" />
        </Form.Item>
        <Form.Item label="附件">
          <Upload
            multiple
            showUploadList={false}
            customRequest={async ({ file, onSuccess, onError }) => {
              const fd = new FormData()
              fd.append('file', file as File)
              fd.append('dir', 'attach')
              try {
                const d: any = await client.post('/files/upload', fd, {
                  headers: { 'Content-Type': 'multipart/form-data' },
                })
                setAttachments((prev) => [...prev, d])
                onSuccess?.(d)
              } catch (e) {
                onError?.(e as Error)
              }
            }}
          >
            <Button icon={<UploadOutlined />}>上传附件</Button>
          </Upload>
          <ul style={{ paddingLeft: 18, marginTop: 8 }}>
            {attachments.map((a, i) => (
              <li key={a.key}>
                {a.name}（{(a.size / 1024).toFixed(1)} KB）
                <Button
                  type="link"
                  size="small"
                  danger
                  onClick={() => setAttachments((prev) => prev.filter((_, idx) => idx !== i))}
                >
                  删除
                </Button>
              </li>
            ))}
          </ul>
        </Form.Item>
        <Space>
          <Button onClick={() => save(false)} loading={submitting}>保存草稿</Button>
          <Button type="primary" onClick={() => save(true)} loading={submitting}>提交流转</Button>
          <Button type="text" onClick={() => navigate(-1)}>返回</Button>
        </Space>
      </Form>
    </Card>
  )
}
