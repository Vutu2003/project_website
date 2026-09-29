export function LoadingState({ label = 'Đang tải…' }: { label?: string }) {
  return <main className="center-screen" role="status"><div className="loading-card">
    <span className="spinner" aria-hidden="true" /><span>{label}</span>
  </div></main>
}
