import { Navigate, Route, Routes } from 'react-router-dom'
import Login from './pages/Login'
import AppLayout from './layouts/AppLayout'
import TodoPage from './pages/TodoPage'
import DonePage from './pages/DonePage'
import MyDocsPage from './pages/MyDocsPage'
import DocEditPage from './pages/DocEditPage'
import DocDetailPage from './pages/DocDetailPage'
import SearchPage from './pages/SearchPage'
import ArchivePage from './pages/ArchivePage'
import NotificationsPage from './pages/NotificationsPage'
import TemplatesPage from './pages/admin/TemplatesPage'
import FlowsPage from './pages/admin/FlowsPage'
import SealsPage from './pages/admin/SealsPage'
import UsersPage from './pages/admin/UsersPage'
import OrgsPage from './pages/admin/OrgsPage'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/" element={<AppLayout />}>
        <Route index element={<TodoPage />} />
        <Route path="done" element={<DonePage />} />
        <Route path="my-docs" element={<MyDocsPage />} />
        <Route path="docs/new" element={<DocEditPage />} />
        <Route path="docs/:id/edit" element={<DocEditPage />} />
        <Route path="docs/:id" element={<DocDetailPage />} />
        <Route path="search" element={<SearchPage />} />
        <Route path="archives" element={<ArchivePage />} />
        <Route path="notifications" element={<NotificationsPage />} />
        <Route path="admin/templates" element={<TemplatesPage />} />
        <Route path="admin/flows" element={<FlowsPage />} />
        <Route path="admin/seals" element={<SealsPage />} />
        <Route path="admin/users" element={<UsersPage />} />
        <Route path="admin/orgs" element={<OrgsPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
