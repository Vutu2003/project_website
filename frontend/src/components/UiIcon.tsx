import type { CSSProperties } from 'react'
export type IconName = 'bell' | 'calendar' | 'equipment' | 'contract' | 'activity' | 'check' | 'report' | 'users' | 'building' | 'briefcase' | 'shield' | 'arrow' | 'clock' | 'chart' | 'alert' | 'history' | 'close'
const paths: Record<IconName, string[]> = {
 bell: ['M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9', 'M10 21h4'],
 calendar: ['M8 2v4M16 2v4M3 10h18', 'M5 4h14a2 2 0 0 1 2 2v14H3V6a2 2 0 0 1 2-2Z', 'M8 14h2M14 14h2M8 18h2'],
 equipment: ['M4 3h16v13H4zM8 21h8M12 16v5', 'M7 10h3l2-4 2 7 2-3h1'],
 contract: ['M14 2H5v20h14V7Z', 'M14 2v5h5M8 11h8M8 15h8M8 18h5'],
 activity: ['M3 12h4l3-8 4 16 3-8h4'],
 check: ['M20 11v1a8 8 0 1 1-4.5-7.2', 'm8 11 4 4 9-10'],
 report: ['M14 2H5v20h14V7Z', 'M14 2v5h5M8 18v-4M12 18v-7M16 18v-5'],
 users: ['M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2', 'M16 3a4 4 0 0 1 0 8M22 21v-2a4 4 0 0 0-3-3.9'],
 building: ['M4 21V3h12v18M2 21h20M16 9h4v12', 'M8 7h4M8 11h4M8 15h4M9 21v-3h2v3'],
 briefcase: ['M3 7h18v14H3zM8 7V3h8v4M3 12a22 22 0 0 0 18 0M10 12h4v3h-4z'],
 shield: ['M12 2 3 6v6c0 5 9 10 9 10s9-5 9-10V6Z', 'm8 12 3 3 5-6'],
 arrow: ['M5 12h14m-6-6 6 6-6 6'],
 clock: ['M12 8v4l3 2'],
 chart: ['M3 3v18h18M7 16v-5M12 16V7M17 16v-8'],
 alert: ['m12 3 10 18H2Z', 'M12 9v5M12 17h.01'],
 history: ['M3 11a9 9 0 1 1 2 7M3 4v7h7', 'M12 7v5l3 2'],
 close: ['m6 6 12 12M6 18 18 6'],
}
export function UiIcon({ name, className = '', style }: { name: IconName; className?: string; style?: CSSProperties }) {
 return <svg className={`ui-icon ${className}`} style={style} width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">{name==='users'&&<circle cx="9" cy="7" r="4"/>}{name==='clock'&&<circle cx="12" cy="12" r="9"/>}{paths[name].map((d,i)=><path key={i} d={d}/>)}</svg>
}
