export function StatusBadge({ label, tone = 'neutral' }: { label: string; tone?: 'neutral' | 'teal' | 'amber' }) {
  return <span className={`status-badge ${tone}`}>{label}</span>
}
