import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import { AuthProvider } from './auth/AuthProvider'
import { ErrorBoundary } from './components/ErrorBoundary'
import App from './App'
import './styles.css'

createRoot(document.getElementById('root')!).render(
  <ErrorBoundary><BrowserRouter><AuthProvider><App /></AuthProvider></BrowserRouter></ErrorBoundary>,
)
