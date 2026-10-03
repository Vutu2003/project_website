import { useEffect, useRef } from 'react'
import type { ReactNode } from 'react'

export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  const dialog = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    dialog.current?.showModal()
    return () => { previous?.focus() }
  }, [])
  return <dialog ref={dialog} className="detail-dialog" aria-label={title} onCancel={event => { event.preventDefault(); onClose() }}>
    <div className="dialog-heading"><h2>{title}</h2><button type="button" className="button secondary compact" onClick={onClose}>Đóng</button></div>
    <div className="dialog-content">{children}</div>
  </dialog>
}
