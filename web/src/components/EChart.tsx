import { useEffect, useRef } from 'react'
import * as echarts from 'echarts'
import 'echarts-wordcloud'

/** ECharts 容器组件：自适应尺寸，option 变化时整体重绘 */
export default function EChart({ option, height = 320 }: { option: any; height?: number }) {
  const ref = useRef<HTMLDivElement>(null)
  const chartRef = useRef<echarts.ECharts | null>(null)

  useEffect(() => {
    if (!ref.current) return
    const chart = echarts.init(ref.current)
    chartRef.current = chart
    const observer = new ResizeObserver(() => chart.resize())
    observer.observe(ref.current)
    return () => {
      observer.disconnect()
      chart.dispose()
      chartRef.current = null
    }
  }, [])

  useEffect(() => {
    chartRef.current?.setOption(option, true)
  }, [option])

  return <div ref={ref} style={{ height, width: '100%' }} />
}
