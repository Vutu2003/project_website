import '../dashboard.css'
import { useEffect, useState } from 'react'
import { useLocation } from 'react-router'
import { useAuth } from '../auth/useAuth'
import { dashboardApi } from '../api/dashboardApi'
import type { DashboardData } from '../types/dashboard'
import { describeError } from '../utils/errorMessages'
import { VtytDashboard } from './dashboards/VtytDashboard'
import { BgdDashboard } from './dashboards/BgdDashboard'
import { KhoaDashboard } from './dashboards/KhoaDashboard'
import { AdminDashboard } from './dashboards/AdminDashboard'
export function DashboardPage() {
 const { user }=useAuth(); const location=useLocation()
 const [data,setData]=useState<DashboardData|null>(null); const [error,setError]=useState<unknown>(null); const [loading,setLoading]=useState(true); const [refresh,setRefresh]=useState(0)
 useEffect(()=>{let active=true;setLoading(true);setError(null);setData(null);dashboardApi.get().then(result=>{if(active){if(result.role!==user?.role)throw new Error('Không thể xác minh phạm vi tổng quan. Vui lòng đăng nhập lại.');setData(result);setLoading(false)}}).catch(failure=>{if(active){setError(failure);setLoading(false)}});return()=>{active=false}},[user?.id,user?.role,refresh])
 useEffect(()=>{if(!loading&&location.hash)document.getElementById(location.hash.slice(1))?.scrollIntoView({behavior:'smooth',block:'start'})},[location.hash,loading])
 if(!user)return null
 if(loading)return <div className="dashboard-loading" role="status" aria-label="Đang tải tổng quan"><div className="dashboard-skeleton header"/><div className="dashboard-skeleton"/><div className="dashboard-skeleton-grid">{[0,1,2,3].map(n=><div className="dashboard-skeleton" key={n}/>)}</div><p>Đang tải tổng quan công việc…</p></div>
 if(error)return <section className="panel dashboard-error" role="alert"><h1>Chưa thể tải tổng quan</h1><p>{describeError(error)}</p><button className="button primary" onClick={()=>setRefresh(v=>v+1)}>Thử lại</button></section>
 if(!data)return null
 return <div className="dashboard-page page-stack">{data.role==='PHONG_VTYT'?<VtytDashboard data={data}/>:data.role==='BAN_GIAM_DOC'?<BgdDashboard data={data}/>:data.role==='KHOA_PHONG'?<KhoaDashboard data={data}/>:<AdminDashboard data={data}/>}<div className="dashboard-updated"><span>Dữ liệu cập nhật khi mở trang tổng quan.</span><button className="text-link" onClick={()=>setRefresh(v=>v+1)}>Làm mới tổng quan</button></div></div>
}
