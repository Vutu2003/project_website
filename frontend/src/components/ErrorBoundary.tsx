import { Component } from 'react'
import type { ReactNode } from 'react'

interface State { failed: boolean }

export class ErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { failed: false }
  static getDerivedStateFromError(): State { return { failed: true } }
  render() {
    if (this.state.failed) return <main className="center-screen"><section className="panel narrow-panel">
      <h1>Trang gặp sự cố</h1><p>Vui lòng tải lại trang để tiếp tục.</p>
      <button className="button primary" onClick={() => window.location.reload()}>Tải lại</button>
    </section></main>
    return this.props.children
  }
}
